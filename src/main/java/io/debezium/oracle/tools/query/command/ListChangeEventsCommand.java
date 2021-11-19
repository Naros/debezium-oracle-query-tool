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

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;

import io.debezium.oracle.tools.query.Constants;
import io.debezium.oracle.tools.query.service.OracleConnection;
import io.debezium.oracle.tools.query.service.OracleConnection.LogFile;
import io.debezium.oracle.tools.query.util.HexConverter;
import oracle.jdbc.OracleTypes;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * A command to list all changes from Oracle LogMiner between a starting and ending SCN.
 *
 * @author Chris Cranford
 */
@Command(name = "list-changes", description = "Lists all change events")
public class ListChangeEventsCommand extends AbstractCommand {

    @Option(names = { "--start-scn" }, required = true, description = "Mining range starting point")
    public String startScn;

    @Option(names = { "--end-scn" }, required = true, description = "Mining range end point")
    public String endScn;

    @Override
    public void run() {
        try (OracleConnection connection = createConnection()) {
            // Display Oracle version
            System.out.println(connection.getBanner());
            System.out.println();

            // Display available databases
            System.out.println("Databases: ");
            System.out.println(Constants.SEPERATOR_SHORT);
            for (String databaseName : connection.getDatabaseNames()) {
                System.out.println(databaseName);
            }
            System.out.println();

            final List<LogFile> logs = connection.getMineableLogFiles(startScn, endScn);
            if (logs.isEmpty()) {
                throw new RuntimeException("No logs found for the range [" + startScn + ", " + endScn + "]");
            }

            // Display logs
            System.out.println("Registered Logs:");
            System.out.println(Constants.SEPERATOR_SHORT);
            for (LogFile log : logs) {
                System.out.println(log.getFileName() + " [" + log.getFirstScn() + ", " + log.getNextScn() + "]");
                connection.registerLogWithLogMiner(log);
            }
            System.out.println();

            // Display information about mining session
            System.out.println("Mining Session:");
            System.out.println(Constants.SEPERATOR_SHORT);
            System.out.println(String.format("%9s: %s %s", "Start SCN", startScn, "(greater-than, exclusive)"));
            System.out.println(String.format("%9s: %s %s", "End SCN", endScn, "(less-than, inclusive)"));
            System.out.println();

            // Start mining session
            connection.startMiningSession(startScn, endScn);

            // Process results from mining session
            System.out.println(Constants.SEPERATOR_LONG);
            connection.mineResults(startScn, endScn, rs -> {
                final ResultSetMetaData metadata = rs.getMetaData();
                while (rs.next()) {
                    for (int i = 0; i < metadata.getColumnCount(); ++i) {
                        final int columnIndex = i + 1;
                        final String columnName = metadata.getColumnName(columnIndex);
                        switch (metadata.getColumnType(columnIndex)) {
                            case OracleTypes.VARCHAR:
                            case OracleTypes.NVARCHAR:
                                writeColumnNameAndValue(columnName, rs.getString(columnName));
                                break;
                            case OracleTypes.NUMERIC:
                                writeColumnNameAndValue(columnName, rs.getLong(columnIndex));
                                break;
                            case OracleTypes.TIMESTAMP:
                                writeColumnNameAndValue(columnName, rs.getTimestamp(columnName).toInstant());
                                break;
                            case OracleTypes.VARBINARY:
                            case OracleTypes.RAW:
                                writeColumnNameAndValue(columnName, getHexValue(rs.getBytes(columnName)));
                                break;
                            default:
                                writeColumnNameAndValue(columnName, "Unknown column type " + metadata.getColumnType(columnIndex));
                        }
                    }
                    System.out.println(Constants.SEPERATOR_LONG);
                }
            });

            // Stop mining session
            connection.endMiningSession();
        }
        catch (SQLException e) {
            throw new RuntimeException("Failed to get change events", e);
        }
    }

    private void writeColumnNameAndValue(String columnName, Object columnValue) {
        System.out.println(String.format("%16s: ", columnName) + columnValue);
    }

    private String getHexValue(byte[] value) {
        return value != null ? HexConverter.convertToHexString(value) : "null";
    }
}
