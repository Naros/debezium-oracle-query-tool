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

/**
 * An abstract database-specific command.
 *
 * @author Chris Cranford
 */
public abstract class AbstractDatabaseCommand extends AbstractCommand {

    private final boolean showBanner;

    public AbstractDatabaseCommand() {
        this(true);
    }

    public AbstractDatabaseCommand(boolean showBanner) {
        this.showBanner = showBanner;
    }

    @Override
    public void run() {
        try (OracleConnection connection = createConnection()) {
            if (showBanner) {
                // Display Oracle version
                System.out.println(connection.getBanner());
                System.out.println();
            }
            doRun(connection);
        }
        catch (SQLException e) {
            throw new RuntimeException("Command failed", e);
        }
    }

    protected abstract void doRun(OracleConnection connection) throws SQLException;

    private OracleConnection createConnection() {
        return new OracleConnection(hostName, port, serviceName, userName, password);
    }
}
