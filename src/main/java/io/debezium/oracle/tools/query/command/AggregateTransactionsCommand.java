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

/**
 * Provides an aggregate output of all transactions within the range, with a count of all
 * changes associated with each transaction.
 *
 * @author Chris Cranford
 */
@Command(name = "transactions", description = "Generates an aggregate of changes per transaction")
public class AggregateTransactionsCommand extends AbstractLogMinerCommand {

    public AggregateTransactionsCommand() {
        super(false);
    }

    @Override
    protected void doRun(OracleConnection connection) throws SQLException {
        final List<LogFile> logs = getLogs(connection);
        connection.createLogMinerContext()
                .withLogs(logs)
                .withStartScn(startScn)
                .withEndScn(endScn)
                .withColumns("UPPER(RAWTOHEX(XID)) AS TRANSACTION_ID, COUNT(1) AS COUNT")
                .withGroupBy("UPPER(RAWTOHEX(XID))")
                .execute(this::writeLogMinerResults);
    }

}
