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
import java.util.List;

import io.debezium.oracle.tools.query.service.LogFile;
import io.debezium.oracle.tools.query.service.OracleConnection;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * A command to list all changes from Oracle LogMiner between a starting and ending SCN.
 *
 * @author Chris Cranford
 */
@Command(name = "list-changes", description = "Lists all change events")
public class ListChangeEventsCommand extends AbstractLogMinerCommand {

    @Option(names = { "--transaction" }, required = false, description = "Transaction id in hex that should only be mined between scn range")
    public String transactionId;

    @Option(names = { "--exclude-internal" }, required = false, defaultValue = "false", description = "Excludes internal operations")
    public boolean excludeInternalOps;

    public ListChangeEventsCommand() {
        super(false);
    }

    @Override
    public void doRun(OracleConnection connection) throws SQLException {
        final List<LogFile> logs = getLogs(connection);
        connection.createLogMinerContext()
                .withLogs(logs)
                .withStartScn(startScn)
                .withEndScn(endScn)
                .withTransactionId(transactionId)
                .withExcludeInternalOps(excludeInternalOps)
                .execute(this::writeLogMinerResults);
    }
}
