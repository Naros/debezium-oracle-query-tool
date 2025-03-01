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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;

/**
 * A simple wrapper that converts a {@link ResultSet} into a formatted {@link AsciiTable}.
 *
 * @author Chris Cranford
 */
public class ResultSetAsciiTable {
    /**
     * Generates an {@link AsciiTable} output from a {@link ResultSet}.
     *
     * @param resultSet the result set
     * @return formatted ascii table of the result set's results
     * @throws SQLException if a SQL operation fails
     */
    public static String from(ResultSet resultSet) throws SQLException {
        final int columnSize = resultSet.getMetaData().getColumnCount();

        final List<Column> columns = new ArrayList<>();
        for (int i = 1; i <= columnSize; i++) {
            final String columnName = resultSet.getMetaData().getColumnName(i);
            columns.add(new Column().header(columnName).maxWidth(4000));
        }

        final List<Object[]> rows = new ArrayList<>();
        while (resultSet.next()) {
            final Object[] row = new Object[columnSize];
            for (int i = 0; i < columnSize; i++) {
                row[i] = resultSet.getObject(i + 1);
            }
            rows.add(row);
        }

        return AsciiTable.getTable(columns.toArray(new Column[0]), rows.toArray(new Object[0][]));
    }

    private ResultSetAsciiTable() {

    }
}
