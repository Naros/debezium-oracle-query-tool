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
package io.debezium.oracle.tools.query.service;

import org.eclipse.microprofile.config.ConfigProvider;

import picocli.CommandLine.IVersionProvider;

/**
 * @author Chris Cranford
 */
public class VersionProviderWithConfigProvider implements IVersionProvider {
    @Override
    public String[] getVersion() throws Exception {
        String applicationName = ConfigProvider.getConfig().getValue("quarkus.application.name", String.class);
        String applicationVersion = ConfigProvider.getConfig().getValue("quarkus.application.version", String.class);
        return new String[]{ String.format("%s %s", applicationName, applicationVersion) };
    }
}
