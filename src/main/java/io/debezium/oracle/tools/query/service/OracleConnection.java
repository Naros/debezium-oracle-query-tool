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

import java.math.BigInteger;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.quarkus.runtime.util.StringUtil;

/**
 * @author Chris Cranford
 */
public class OracleConnection implements AutoCloseable {

    @FunctionalInterface
    public interface ResultSetMapper<T> {
        T execute(ResultSet rs) throws SQLException;
    }

    @FunctionalInterface
    public interface ResultSetConsumer {
        void accept(ResultSet rs) throws SQLException;
    }

    private final Connection connection;

    /**
     * Creates a connection to the Oracle database
     *
     * @param hostName the hostname, should never be {@code null}
     * @param port the port number, should never be {@code null}
     * @param serviceName the service name, should never be {@code null}
     * @param userName the authenticating username, should never be {@code null}
     * @param password the authenticating user's password, should never be {@code null}
     */
    public OracleConnection(String hostName, String port, String serviceName, String userName, String password) {
        Objects.requireNonNull(hostName);
        Objects.requireNonNull(port);
        Objects.requireNonNull(serviceName);
        Objects.requireNonNull(userName);
        Objects.requireNonNull(password);

        final Properties properties = new Properties();
        properties.put("user", userName);
        properties.put("password", password);

        try {
            this.connection = DriverManager.getConnection(getDatabaseUri(hostName, port, serviceName), properties);
        }
        catch (SQLException e) {
            throw new RuntimeException("Failed to establish connection to Oracle", e);
        }
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        }
        catch (SQLException e) {
            throw new RuntimeException("Failed to close connection", e);
        }
    }

    /**
     * Get the database banner text.
     *
     * @return the banner text
     * @throws SQLException if a database error occurred
     */
    public String getBanner() throws SQLException {
        try {
            return queryAndMap(getBannerQuery("BANNER_FULL"), singleResultMapper(rs -> rs.getString(1)));
        }
        catch (SQLException e) {
            // use fallback
            return queryAndMap(getBannerQuery("BANNER"), singleResultMapper(rs -> rs.getString(1)));
        }
    }

    /**
     * Executes the current query using {@link CallableStatement#execute()}.
     *
     * @param query the query to execute, should not be {@code null}.
     * @throws SQLException if a database exception was thrown
     */
    public void call(String query) throws SQLException {
        Objects.requireNonNull(query, "A query must be provided");
        try (CallableStatement statement = connection.prepareCall(query)) {
            statement.execute();
        }
    }

    /**
     * Executes the query using {@link Statement#execute(String)}.
     *
     * @param query the query to execute, should not be {@code null}
     * @throws SQLException if a database exception was thrown
     */
    public void execute(String query) throws SQLException {
        Objects.requireNonNull(query, "A query must be provided");
        try (Statement statement = connection.createStatement()) {
            statement.execute(query);
        }
    }

    /**
     * Executes the query, providing the {@link ResultSet} to the provided {@link ResultSetConsumer}.
     *
     * @param query the query to execute, should not be {@code null}
     * @param consumer the result set consumer, should not be {@code null}
     * @throws SQLException if a database exception is thrown
     */
    public void query(String query, ResultSetConsumer consumer) throws SQLException {
        Objects.requireNonNull(query, "A query must be provided");
        Objects.requireNonNull(consumer, "A result set consumer must be supplied");
        try (Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery(query)) {
                consumer.accept(rs);
            }
        }
    }

    /**
     * Executes the provided query and uses the {@link ResultSetMapper} to return the result.
     *
     * @param query the query to execute, should not be {@code null}
     * @param mapper the result set mapper, should not be {@code null}
     * @return the mapped value.
     * @param <T> the return type
     * @throws SQLException if a database exception is thrown
     */
    public <T> T queryAndMap(String query, ResultSetMapper<T> mapper) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery(query)) {
                return mapper.execute(rs);
            }
        }
    }

    /**
     * Provides a utility mapper for single result expectations.
     *
     * @param extractor the result set extractor that maps a column's value, should not be {@code null}
     * @return the column's mapped value
     * @param <T> the return type
     * @throws SQLException if a database exception is thrown
     */
    public static <T> ResultSetMapper<T> singleResultMapper(ResultSetMapper<T> extractor) throws SQLException {
        return (rs) -> {
            if (rs.next()) {
                final T result = extractor.execute(rs);
                if (!rs.next()) {
                    return result;
                }
            }
            throw new IllegalStateException("Expected only a single row");
        };
    }

    /**
     * Gathers a list of available logs based on the supplied system change number range.
     *
     * @param startScn the starting range system change number, should never be {@code null}
     * @return list of logs, never {@code null}
     * @throws SQLException if a database exception is thrown
     */
    public List<LogFile> getLogsSinceScn(String startScn) throws SQLException {
        Objects.requireNonNull(startScn, "A start scn must be supplied to fetch logs.");

        return queryAndMap(mineableLogsQuery(startScn, false, null, Duration.ZERO), rs -> {
            final List<LogFile> archiveLogs = new ArrayList<>();
            final List<LogFile> onlineLogs = new ArrayList<>();

            while (rs.next()) {
                final String fileName = rs.getString(1);
                final BigInteger firstScn = rs.getObject(2, BigInteger.class);
                final BigInteger nextScn = rs.getObject(3, BigInteger.class);
                final String type = rs.getString(6);
                final Long sequence = rs.getLong(7);
                final Long redoThread = rs.getLong(10);
                if ("ARCHIVED".equals(type)) {
                    final LogFile log = new LogFile(fileName, firstScn, nextScn, sequence, LogFile.Type.ARCHIVE, redoThread, 0L);
                    if (log.getNextScn().compareTo(new BigInteger(startScn)) >= 0) {
                        archiveLogs.add(log);
                    }
                }
                else {
                    final LogFile log = new LogFile(fileName, firstScn, nextScn, sequence, LogFile.Type.ONLINE, redoThread, 0L);
                    if (log.getNextScn().compareTo(new BigInteger(startScn)) >= 0) {
                        onlineLogs.add(log);
                    }
                }
            }

            // Deduplicate any logs
            onlineLogs.forEach(l -> archiveLogs.removeIf(a -> a.getSequence().equals(l.getSequence())));

            return Stream.concat(archiveLogs.stream(), onlineLogs.stream()).collect(Collectors.toList());
        });
    }

    public String getLogsSinceScnQuery(String sinceScn) {
        final StringBuilder query = new StringBuilder();
        query.append("SELECT NAME, DEST_ID, THREAD#, SEQUENCE#, FIRST_CHANGE#, NEXT_CHANGE#, ARCHIVED, DELETED, STATUS ");
        query.append("FROM V$ARCHIVED_LOG ");
        query.append("WHERE FIRST_CHANGE# >= ").append(sinceScn).append(" ");
        query.append("UNION ALL ");
        query.append("SELECT MIN(F.MEMBER), -1, THREAD#, SEQUENCE#, FIRST_CHANGE#, NEXT_CHANGE#, 'NO', 'NO', 'REDO' ");
        query.append("FROM V$LOG L, V$LOGFILE F ");
        query.append("WHERE L.GROUP# = F.GROUP# ");
        query.append("AND L.STATUS != 'INACTIVE' ");
        query.append("GROUP BY L.THREAD#, L.SEQUENCE#, L.FIRST_CHANGE#, L.NEXT_CHANGE#");
        return query.toString();
    }

    /**
     * Creates a new {@link LogMinerContext} to query LogMiner.
     *
     * @return the mining context
     */
    public LogMinerContext createLogMinerContext() {
        return new LogMinerContext(this);
    }

    private static String getBannerQuery(String fieldName) {
        return "SELECT " + fieldName + " FROM V$VERSION WHERE " + fieldName + " LIKE 'Oracle Database%'";
    }

    private static String getDatabaseUri(String hostName, String port, String serviceName) {
        return String.format("jdbc:oracle:thin:@%s:%s/%s", hostName, port, serviceName);
    }

    private static String mineableLogsQuery(String startScn, boolean archiveLogOnlyMode, String destinationName, Duration archiveRetention) {
        final StringBuilder query = new StringBuilder();
        if (!archiveLogOnlyMode) {
            query.append("SELECT ");
            query.append("MIN(F.MEMBER) AS FILE_NAME, ");
            query.append("L.FIRST_CHANGE# AS FIRST_CHANGE, ");
            query.append("L.NEXT_CHANGE# AS NEXT_CHANGE, ");
            query.append("L.ARCHIVED, ");
            query.append("L.STATUS, ");
            query.append("'ONLINE' AS TYPE, ");
            query.append("L.SEQUENCE# as SEQ, ");
            query.append("'NO' AS DICT_START, ");
            query.append("'NO' AS DICT_END, ");
            query.append("L.THREAD# AS THREAD ");
            query.append("FROM V$LOGFILE F, V$LOG L LEFT JOIN V$ARCHIVED_LOG A ");
            query.append("ON A.FIRST_CHANGE# = L.FIRST_CHANGE# ");
            query.append("AND A.NEXT_CHANGE# = L.NEXT_CHANGE# ");
            query.append("WHERE A.FIRST_CHANGE# IS NULL ");
            query.append("AND F.GROUP# = L.GROUP# ");
            query.append("GROUP BY F.GROUP#, L.FIRST_CHANGE#, L.NEXT_CHANGE#, L.STATUS, L.ARCHIVED, L.SEQUENCE#, L.THREAD# ");
            query.append("UNION ");
        }
        query.append("SELECT ");
        query.append("A.NAME AS FILE_NAME, ");
        query.append("A.FIRST_CHANGE# AS FIRST_CHANGE, ");
        query.append("A.NEXT_CHANGE# AS NEXT_CHANGE, ");
        query.append("'YES' AS ARCHIVED, ");
        query.append("NULL AS STATUS, ");
        query.append("'ARCHIVED' AS TYPE, ");
        query.append("A.SEQUENCE# AS SEQ, ");
        query.append("A.DICTIONARY_BEGIN AS DICT_BEGIN, ");
        query.append("A.DICTIONARY_END AS DICT_END, ");
        query.append("A.THREAD# AS THREAD ");
        query.append("FROM V$ARCHIVED_LOG A ");
        query.append("WHERE A.NAME IS NOT NULL ");
        query.append("AND A.ARCHIVED = 'YES' ");
        query.append("AND A.STATUS = 'A' ");
        query.append("AND A.NEXT_CHANGE# > ").append(startScn).append(" ");
        query.append("AND A.DEST_ID IN (").append(archiveLogDestinationQuery(destinationName)).append(") ");

        if (!archiveRetention.isNegative() && !archiveRetention.isZero()) {
            query.append("AND A.FIRST_TIME >= SYSDATE - (").append(archiveRetention.toHours()).append("/24) ");
        }

        query.append("ORDER BY 7");
        return query.toString();
    }

    private static String archiveLogDestinationQuery(String destinationName) {
        final StringBuilder query = new StringBuilder(256);
        query.append("SELECT DEST_ID FROM V$ARCHIVE_DEST_STATUS WHERE STATUS='VALID' AND TYPE='LOCAL'");
        if (!StringUtil.isNullOrEmpty(destinationName)) {
            query.append(" AND UPPER(DEST_NAME)='").append(destinationName.toUpperCase()).append("'");
        }
        else {
            query.append(" AND ROWNUM=1");
        }
        return query.toString();
    }

    /**
     * A LogMiner session context.
     */
    public static class LogMinerContext {
        private final OracleConnection connection;

        private List<LogFile> logs;
        private String startScn;
        private String endScn;
        private String transactionId;
        private String columns;
        private String groupBy;
        private boolean excludeInternalOps;

        LogMinerContext(OracleConnection connection) {
            this.connection = connection;
            this.columns = "*";
        }

        /**
         * Sets the logs that should be mined.
         *
         * @param logs collection of logs, should not be {@code null} or empty.
         * @return this context
         */
        public LogMinerContext withLogs(List<LogFile> logs) {
            this.logs = logs;
            return this;
        }

        /**
         * Sets the LogMiner mining range start system change number.
         *
         * @param startScn start range system change number, may be {@code null}
         * @return this context
         */
        public LogMinerContext withStartScn(String startScn) {
            this.startScn = startScn;
            return this;
        }

        /**
         * Sets the LogMiner mining range end system change number.
         *
         * @param endScn end range system change number, may be {@code null}
         * @return this context
         */
        public LogMinerContext withEndScn(String endScn) {
            this.endScn = endScn;
            return this;
        }

        /**
         * Sets that LogMiner should only return data for the given transaction id.
         *
         * @param transactionId hex-representation of a transaction id, may be {@code null}
         * @return this context
         */
        public LogMinerContext withTransactionId(String transactionId) {
            this.transactionId = transactionId;
            return this;
        }

        /**
         * Sets whether to exclude {@code INTERNAL} operations from the change listing.
         *
         * @param excludeInternalOps true to exclude internal operations, false to include them
         * @return this context
         */
        public LogMinerContext withExcludeInternalOps(boolean excludeInternalOps) {
            this.excludeInternalOps = excludeInternalOps;
            return this;
        }

        /**
         * Sets the desired column list for the query, defaults to all columns.
         *
         * @param columns the list of columns
         * @return this context
         */
        public LogMinerContext withColumns(String columns) {
            this.columns = columns;
            return this;
        }

        /**
         * Sets the desired group by.
         *
         * @param groupBy the group by expression
         * @return this context
         */
        public LogMinerContext withGroupBy(String groupBy) {
            this.groupBy = groupBy;
            return this;
        }

        /**
         * Executes a LogMiner data request.
         *
         * @param consumer the result set consumer that handles the results, should not be {@code null}
         * @throws SQLException if a database error occurs
         */
        public void execute(ResultSetConsumer consumer) throws SQLException {
            // Register logs
            for (LogFile logFile : logs) {
                addLogMinerLog(logFile.getFileName());
            }

            startSession();

            try {
                queryContents(consumer);
            }
            finally {
                endSession();
            }
        }

        /**
         * Registers/Adds the specified log file to LogMiner before starting a session.
         *
         * @param fileName the log file to add, should not be {@code null}
         * @throws SQLException if a database error occurs
         */
        private void addLogMinerLog(String fileName) throws SQLException {
            Objects.requireNonNull(fileName, "A log filename must be supplied to be added to LogMiner session");
            connection.call("BEGIN sys.dbms_logmnr.add_logfile(LOGFILENAME=>'" + fileName + "',options=>DBMS_LOGMNR.ADDFILE); END;");
        }

        /**
         * Starts the LogMiner session.
         *
         * @throws SQLException if a database error occurs
         */
        private void startSession() throws SQLException {
            final StringBuilder query = new StringBuilder();
            query.append("BEGIN sys.dbms_logmnr.start_logmnr(");
            if (!StringUtil.isNullOrEmpty(startScn)) {
                query.append("startScn=>'").append(startScn).append("',");
            }
            if (!StringUtil.isNullOrEmpty(endScn)) {
                query.append("endScn => '").append(endScn).append("',");
            }
            query.append("OPTIONS=>DBMS_LOGMNR.DICT_FROM_ONLINE_CATALOG + DBMS_LOGMNR.NO_ROWID_IN_STMT);");
            query.append("END;");
            connection.execute(query.toString());
        }

        /**
         * Ends the LogMiner session.
         *
         * @throws SQLException if a database error occurs
         */
        private void endSession() throws SQLException {
            connection.execute("BEGIN sys.dbms_logmnr.end_logmnr(); end;");
        }

        /**
         * Executes a query against {@code V$LOGMNR_CONTENTS} performance view.
         *
         * @param consumer the consumer for the result set, should not be {@code null}
         * @throws SQLException if a database error occurs
         */
        private void queryContents(ResultSetConsumer consumer) throws SQLException {
            final StringBuilder query = new StringBuilder();
            query.append("SELECT ").append(columns).append(" FROM V$LOGMNR_CONTENTS WHERE 1=1");

            if (!StringUtil.isNullOrEmpty(startScn)) {
                query.append(" AND SCN > ").append(startScn);
            }

            if (!StringUtil.isNullOrEmpty(endScn)) {
                query.append(" AND SCN <= ").append(endScn);
            }

            if (!StringUtil.isNullOrEmpty(transactionId)) {
                query.append(" AND UPPER(RAWTOHEX(XID))=UPPER('").append(transactionId).append("')");
            }

            if (excludeInternalOps) {
                query.append(" AND OPERATION_CODE != 0");
            }

            if (!StringUtil.isNullOrEmpty(groupBy)) {
                query.append(" GROUP BY ").append(groupBy);
            }

            connection.query(query.toString(), consumer);
        }
    }
}
