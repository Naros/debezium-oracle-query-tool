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

import io.debezium.oracle.tools.query.service.OracleConnection;
import picocli.CommandLine.Option;

/**
 * An abstract command that all commands should be derived from.
 *
 * @author Chris Cranford
 */
public abstract class AbstractCommand implements Runnable {

    @Option(names = { "--hostname" }, required = true, description = "Database hostname")
    public String hostName;

    @Option(names = { "--username" }, required = true, description = "Authentication username")
    public String userName;

    @Option(names = { "--password" }, required = true, description = "Authentication password")
    public String password;

    @Option(names = { "--service" }, required = true, description = "Database service name")
    public String serviceName;

    @Option(names = { "--port" }, defaultValue = "1521", description = "Database port")
    public String port;

    protected OracleConnection createConnection() {
        return new OracleConnection(hostName, port, serviceName, userName, password);
    }
}
