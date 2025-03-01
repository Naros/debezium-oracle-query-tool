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
import io.debezium.oracle.tools.query.service.ResultSetAsciiTable;
import io.quarkus.runtime.util.StringUtil;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * A command that displays the transaction logs based on given criteria.
 *
 * @author Chris Cranford
 */
@Command(name = "logs", description = "Display information about Oracle tranasction logs")
public class LogsCommand extends AbstractDatabaseCommand {

    @Option(names = { "--since" }, required = false, description = "Specifies to list logs since the specified SCN")
    private String sinceScn;

    @Override
    public void doRun(OracleConnection connection) throws SQLException {
        System.out.println(ResultSetAsciiTable.fromTable(connection, "V$ARCHIVE_DEST_STATUS"));

        if (!StringUtil.isNullOrEmpty(sinceScn)) {
            System.out.println(ResultSetAsciiTable.fromQuery(
                    connection,
                    "Logs Since " + sinceScn,
                    connection.getLogsSinceScnQuery(sinceScn)));
        }
    }

}
