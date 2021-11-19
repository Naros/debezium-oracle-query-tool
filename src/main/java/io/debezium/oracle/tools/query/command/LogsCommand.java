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

import java.sql.SQLException;

import io.debezium.oracle.tools.query.Constants;
import io.debezium.oracle.tools.query.service.OracleConnection;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * A command that displays the transaction logs based on given criteria.
 *
 * @author Chris Cranford
 */
@Command(name = "logs", description = "Display information about Oracle tranasction logs")
public class LogsCommand extends AbstractCommand {

    @Option(names = { "--since" }, required = true, description = "Specifies to list logs since the specified SCN")
    private String sinceScn;

    @Override
    public void run() {
        try (OracleConnection connection = createConnection()) {
            // Display Oracle version
            System.out.println(connection.getBanner());
            System.out.println();

            System.out.println("Log Destinations");
            System.out.println(Constants.SEPERATOR_SHORT);
            connection.executeQuery(getLogDestinationStatusQuery(), rs -> {
                writeLine("ID", rs.getLong("DEST_ID"));
                writeLine("Name", rs.getString("DEST_NAME"));
                writeLine("Status", rs.getString("STATUS"));
                writeLine("Type", rs.getString("TYPE"));
                writeLine("Location", rs.getString("DESTINATION"));
                System.out.println(Constants.SEPERATOR_LONG);
            });
            System.out.println();

            System.out.println("Logs Since " + sinceScn);
            System.out.println(Constants.SEPERATOR_SHORT);
            connection.executeQuery(getLogsSinceQuery(sinceScn), rs -> {
                writeLine("FileName", rs.getString("NAME"));
                final Long destId = rs.getLong("DEST_ID");
                writeLine("Dest ID", destId == -1 ? "N/A" : destId);
                writeLine("Thread ID", rs.getLong("THREAD#"));
                writeLine("Sequence ID", rs.getLong("SEQUENCE#"));
                writeLine("First Change", rs.getString("FIRST_CHANGE#"));
                writeLine("Next Change", rs.getString("NEXT_CHANGE#"));
                writeLine("Archived", rs.getString("ARCHIVED"));
                writeLine("Deleted", rs.getString("DELETED"));
                writeLine("Status", rs.getString("STATUS"));
                System.out.println(Constants.SEPERATOR_LONG);
            });
            System.out.println();
        }
        catch (SQLException e) {
            throw new RuntimeException("Failed to execute logs command", e);
        }
    }

    private String getLogsSinceQuery(String sinceScn) {
        final StringBuilder query = new StringBuilder();
        query.append("SELECT NAME, DEST_ID, THREAD#, SEQUENCE#, FIRST_CHANGE#, NEXT_CHANGE#, ARCHIVED, DELETED, STATUS ");
        query.append("FROM V$ARCHIVED_LOG ");
        query.append("WHERE FIRST_CHANGE# >= ").append(sinceScn).append(" ");
        query.append("UNION ALL ");
        query.append("SELECT MIN(F.MEMBER), -1, THREAD#, SEQUENCE#, FIRST_CHANGE#, NEXT_CHANGE#, 'NO', 'NO', 'REDO' ");
        query.append("FROM V$LOG L, V$LOGFILE F ");
        query.append("WHERE L.GROUP# = F.GROUP# ");
        query.append("GROUP BY L.THREAD#, L.SEQUENCE#, L.FIRST_CHANGE#, L.NEXT_CHANGE#");
        return query.toString();
    }

    private String getLogDestinationStatusQuery() {
        final StringBuilder query = new StringBuilder();
        query.append("SELECT ad.dest_id, ad.dest_name, ad.status, ads.TYPE, ads.destination ");
        query.append("FROM v$archive_dest ad, v$archive_dest_status ads ");
        query.append("WHERE ad.dest_id=ads.dest_id");
        return query.toString();
    }

    private void writeLine(String name, Object value) {
        System.out.println(String.format("%-14s: ", name) + value);
    }
}
