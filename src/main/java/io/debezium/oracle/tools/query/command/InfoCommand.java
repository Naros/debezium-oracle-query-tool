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
import java.text.DecimalFormat;

import io.debezium.oracle.tools.query.Constants;
import io.debezium.oracle.tools.query.service.OracleConnection;
import picocli.CommandLine.Command;

/**
 * A command that displays basic information about the Oracle database fetched from {@code V$DATABASE}
 * and {@code V$LOG} tables.
 *
 * @author Chris Cranford
 */
@Command(name = "info", description = "Display database information")
public class InfoCommand extends AbstractCommand {

    private static final DecimalFormat BYTES_FORMAT = new DecimalFormat("#,###");

    @Override
    public void run() {
        try (OracleConnection connection = createConnection()) {
            // Display Oracle version
            System.out.println(connection.getBanner());
            System.out.println();

            // Display V$DATABASE
            System.out.println("V$DATABASE");
            System.out.println(Constants.SEPERATOR_SHORT);
            connection.queryTable("V$DATABASE", rs -> {
                writeLine("Database ID", rs.getLong("DBID"));
                writeLine("Name", rs.getString("NAME"));
                writeLine("Mode", rs.getString("OPEN_MODE"));
                writeLine("Role", rs.getString("DATABASE_ROLE"));
                writeLine("Platofmr", rs.getString("PLATFORM_NAME"));
                writeLine("Current SCN", rs.getString("CURRENT_SCN"));
                writeLine("CDB", rs.getString("CDB"));
                writeLine("Con ID", rs.getInt("CON_ID"));
                writeLine("Logging - Min", rs.getString("SUPPLEMENTAL_LOG_DATA_MIN"));
                writeLine("Logging - PK", rs.getString("SUPPLEMENTAL_LOG_DATA_PK"));
                writeLine("Logging - UI", rs.getString("SUPPLEMENTAL_LOG_DATA_UI"));
                writeLine("Logging - FK", rs.getString("SUPPLEMENTAL_LOG_DATA_FK"));
                writeLine("Logging - PL", rs.getString("SUPPLEMENTAL_LOG_DATA_PL"));
                writeLine("Logging - SR", rs.getString("SUPPLEMENTAL_LOG_DATA_SR"));
                writeLine("Logging - All", rs.getString("SUPPLEMENTAL_LOG_DATA_ALL"));
                System.out.println(Constants.SEPERATOR_LONG);
            });
            System.out.println();

            // todo: add dump of v$containers
            // Currently on Oracle 21, this view is empty for a common user that has
            // access to all created PDBs; which shouldn't be the case?

            // Display V$LOG
            System.out.println("V$LOG");
            System.out.println(Constants.SEPERATOR_SHORT);
            connection.queryTable("V$LOG", rs -> {
                writeLine("Group", rs.getLong("GROUP#"));
                writeLine("Thread", rs.getLong("THREAD#"));
                writeLine("Sequence", rs.getLong("SEQUENCE#"));
                writeLine("Size", BYTES_FORMAT.format(rs.getLong("BYTES")));
                writeLine("Members", rs.getLong("MEMBERS"));
                writeLine("Archived", rs.getString("ARCHIVED"));
                writeLine("Status", rs.getString("STATUS"));
                writeLine("First Change", rs.getString("FIRST_CHANGE#"));
                writeLine("Next Change", rs.getString("NEXT_CHANGE#"));
                writeLine("Con ID", rs.getInt("CON_ID"));
                System.out.println(Constants.SEPERATOR_LONG);
            });

        }
        catch (SQLException e) {
            throw new RuntimeException("Failed to get database information", e);
        }
    }

    private void writeLine(String name, Object value) {
        System.out.println(String.format("%-14s: ", name) + value);
    }
}
