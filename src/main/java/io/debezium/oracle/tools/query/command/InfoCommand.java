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

import io.debezium.oracle.tools.query.service.OracleConnection;

import picocli.CommandLine.Command;

/**
 * A command that displays basic information about the Oracle database fetched from {@code V$DATABASE}
 * and {@code V$LOG} tables.
 *
 * @author Chris Cranford
 */
@Command(name = "info", description = "Display database information")
public class InfoCommand extends AbstractDatabaseCommand {
    @Override
    protected void doRun(OracleConnection connection) throws SQLException {
        // Display variables
        System.out.println(connection.exportQuery("LogMiner-specific System Parameters",
                "SELECT NAME, VALUE FROM V$PARAMETER WHERE NAME IN ('compatible')"));

        // Display V$DATABASE
        System.out.println(connection.exportTable("V$DATABASE"));

        // Display V$LOG
        System.out.println(connection.exportTable("V$LOG"));
    }
}
