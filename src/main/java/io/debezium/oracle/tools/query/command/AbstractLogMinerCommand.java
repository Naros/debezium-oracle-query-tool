/*
 *  Copyright 2021 The original authors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.debezium.oracle.tools.query.command;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;

import io.debezium.oracle.tools.query.service.LogFile;
import io.debezium.oracle.tools.query.service.OracleConnection;
import io.debezium.oracle.tools.query.util.HexConverter;

import oracle.jdbc.OracleTypes;
import picocli.CommandLine.Option;

/**
 * Abstract base class for executing LogMiner-specific commands.
 *
 * @author Chris Cranford
 */
public abstract class AbstractLogMinerCommand extends AbstractDatabaseCommand {

    @Option(names = { "--start-scn" }, required = true, description = "Mining range starting point")
    public String startScn;

    @Option(names = { "--end-scn" }, required = true, description = "Mining range end point")
    public String endScn;

    @Option(names = { "--show-logs" }, required = false, defaultValue = "false", description = "Whether to display mined log details to console")
    public Boolean showMinedLogs;

    @Option(names = { "--output" }, required = true, description = "CSV file name for writing mined data")
    public String fileName;

    @Option(names = { "--destination-name" }, required = false, description = "Set the archive log destination name")
    public String destinationName;

    public AbstractLogMinerCommand(boolean showBanner) {
        super(showBanner);
    }

    protected List<LogFile> getLogs(OracleConnection connection) throws SQLException {
        final List<LogFile> logs = connection.getLogsSinceScn(startScn, destinationName);
        if (logs.isEmpty()) {
            throw new RuntimeException("No logs found for the range [" + startScn + ", " + endScn + "]");
        }

        if (showMinedLogs) {
            // Display logs
            System.out.println("Mineable Logs");
            System.out.println(AsciiTable.getTable(logs, Arrays.asList(
                    new Column().header("FILE_NAME").maxWidth(4000).with(LogFile::getFileName),
                    new Column().header("FIRST_SCN").with(log -> log.getFirstScn().toString()),
                    new Column().header("NEXT_SCN").with(log -> log.getNextScn().toString()))));
            System.out.println();
        }

        return logs;
    }

    protected void writeLogMinerResults(ResultSet rs) throws SQLException {
        try (PrintWriter writer = new PrintWriter(new FileOutputStream(fileName))) {
            // Write column names
            writer.println(columnNamesRow(rs));

            while (rs.next()) {
                writer.println(rowColumnValues(rs).stream().map(String::valueOf).collect(Collectors.joining(",")));
            }
        }
        catch (FileNotFoundException e) {
            throw new RuntimeException("Failed to write output file", e);
        }
    }

    private String columnNamesRow(ResultSet rs) throws SQLException {
        return IntStream.range(1, rs.getMetaData().getColumnCount() + 1)
                .mapToObj(i -> {
                    try {
                        return "\"" + rs.getMetaData().getColumnName(i) + "\"";
                    }
                    catch (SQLException e) {
                        throw new RuntimeException("Failed to read column at index " + i, e);
                    }
                })
                .collect(Collectors.joining(","));
    }

    private List<Object> rowColumnValues(ResultSet rs) throws SQLException {
        final ResultSetMetaData metadata = rs.getMetaData();
        final int columnSize = metadata.getColumnCount();

        final List<Object> row = new ArrayList<>(columnSize);
        for (int i = 1; i <= columnSize; i++) {
            row.add(columnValue(rs, i, metadata.getColumnType(i)));
        }

        return row;
    }

    private Object columnValue(ResultSet rs, int columnIndex, int columnType) throws SQLException {
        return switch (columnType) {
            case OracleTypes.VARCHAR, OracleTypes.NVARCHAR -> quote(rs.getString(columnIndex), true);
            case OracleTypes.NUMERIC -> rs.getLong(columnIndex);
            case OracleTypes.TIMESTAMP -> quote(rs.getTimestamp(columnIndex));
            case OracleTypes.VARBINARY, OracleTypes.RAW -> hex(rs.getBytes(columnIndex));
            default -> "";
        };
    }

    private String quote(String value, boolean escapeQuotes) {
        if (value == null) {
            return "";
        }
        return escapeQuotes ? "\"" + value.replaceAll("\"", "\"\"") + "\"" : "\"" + value + "\"";
    }

    private String quote(Timestamp timestamp) {
        return timestamp != null ? quote(timestamp.toInstant().toString(), false) : "";
    }

    private String hex(byte[] data) {
        return data != null ? "\"" + HexConverter.convertToHexString(data) + "\"" : "";
    }
}
