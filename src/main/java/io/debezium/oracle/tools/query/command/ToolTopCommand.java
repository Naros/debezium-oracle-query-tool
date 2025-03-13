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

import jakarta.enterprise.inject.Produces;

import io.debezium.oracle.tools.query.service.ExecutionExceptionHandler;
import io.debezium.oracle.tools.query.service.VersionProviderWithConfigProvider;
import io.quarkus.picocli.runtime.PicocliCommandLineFactory;
import io.quarkus.picocli.runtime.annotations.TopCommand;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;

/**
 * The base command handler for the tool.
 *
 * @author Chris Cranford
 */
@TopCommand
@Command(name = "dbzoqt", mixinStandardHelpOptions = true, versionProvider = VersionProviderWithConfigProvider.class, subcommands = {
        HelpCommand.class,
        InfoCommand.class,
        LogsCommand.class,
        ListChangeEventsCommand.class,
        AggregateTransactionsCommand.class
}, description = "A command-line query tool for Oracle by Debezium")
public class ToolTopCommand {
    @Produces
    CommandLine getCommandLineInstance(PicocliCommandLineFactory factory) {
        return factory.create().setExecutionExceptionHandler(new ExecutionExceptionHandler());
    }
}
