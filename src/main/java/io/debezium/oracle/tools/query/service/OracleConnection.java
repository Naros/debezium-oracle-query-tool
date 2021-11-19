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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

import io.debezium.oracle.tools.query.service.OracleConnection.LogFile.Type;

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
            return queryAndMapSingleValue(getBannerQuery("BANNER_FULL"), rs -> rs.getString(1));
        }
        catch (SQLException e) {
            // use fallback
            return queryAndMapSingleValue(getBannerQuery("BANNER"), rs -> rs.getString(1));
        }
    }

    public String getCompatibility() throws SQLException {
        return queryAndMapSingleValue("SELECT name, value FROM v$parameter WHERE name = 'compatible'", rs -> rs.getString(2));
    }

    /**
     * Get the list of available database names from the Oracle instance.
     *
     * @return list of database names, never {@code null}
     * @throws SQLException if a database error occurred
     */
    public List<String> getDatabaseNames() throws SQLException {
        List<String> names = new ArrayList<>();
        names.add(queryAndMapSingleValue("SELECT NAME FROM V$DATABASE", rs -> rs.getString(1)));

        String cdb = null;
        try {
            cdb = queryAndMapSingleValue("SELECT CDB FROM V$DATABASE", rs -> rs.getString(1));
        }
        catch (SQLException e) {
            // ignore working with CDB
        }

        if (!isNullOrEmpty(cdb) && "YES".equalsIgnoreCase(cdb)) {
            query("SELECT NAME FROM V$CONTAINERS", rs -> {
                while (rs.next()) {
                    names.add(rs.getString(1));
                }
            });
        }

        return names;
    }

    /**
     * Performs an Oracle LogMiner mining loop.
     *
     * @param startScn the starting mining range system change number, should never be {@code null}
     * @param endScn the ending mining range system change number, should never be {@code null}
     * @param consumer the result set consumer, should never be {@code null}
     * @throws SQLException if a database error occurred
     */
    public void mineResults(String startScn, String endScn, ResultSetConsumer consumer) throws SQLException {
        Objects.requireNonNull(startScn);
        Objects.requireNonNull(endScn);
        Objects.requireNonNull(consumer);

        query(getLogMinerQuery(startScn, endScn), consumer);
    }

    /**
     * Gathers a list of available logs based on the supplied mining range.
     *
     * @param startScn the starting mining range system change number, should never be {@code null}
     * @param endScn the ending mining range system change number, should never be {@code null}
     * @return list of logs, never {@code null}
     * @throws SQLException if ad atabase error ocurred
     */
    public List<LogFile> getMineableLogFiles(String startScn, String endScn) throws SQLException {
        Objects.requireNonNull(startScn);
        Objects.requireNonNull(endScn);

        List<LogFile> archiveLogFiles = new ArrayList<>();
        List<LogFile> redoLogFiles = new ArrayList<>();
        query(getMinableLogsQuery(startScn, endScn), rs -> {
            while (rs.next()) {
                final String fileName = rs.getString(1);
                final BigInteger firstScn = rs.getObject(2, BigInteger.class);
                final BigInteger nextScn = rs.getObject(3, BigInteger.class);
                final String type = rs.getString(6);
                final Long sequence = rs.getLong(7);
                if ("ARCHIVED".equalsIgnoreCase(type)) {
                    final LogFile log = new LogFile(fileName, firstScn, nextScn, sequence, Type.ARCHIVE);
                    if (log.nextScn.compareTo(BigInteger.valueOf(Long.parseLong(startScn))) >= 0) {
                        archiveLogFiles.add(log);
                    }
                }
                else if ("ONLINE".equalsIgnoreCase(type)) {
                    final LogFile log = new LogFile(fileName, firstScn, nextScn, sequence, Type.REDO);
                    if (log.nextScn.compareTo(BigInteger.valueOf(Long.parseLong(startScn))) >= 0) {
                        redoLogFiles.add(log);
                    }
                }
            }
        });

        // Clear any potential duplicates
        for (LogFile log : redoLogFiles) {
            archiveLogFiles.removeIf(f -> {
                if (f.sequence.equals(log.sequence)) {
                    return true;
                }
                return false;
            });
        }

        final List<LogFile> results = new ArrayList<>();
        results.addAll(archiveLogFiles);
        results.addAll(redoLogFiles);
        return results;
    }

    /**
     * Register the specified log with an Oracle LogMiner session.
     *
     * @param log the log instance, should never be {@code null}
     * @throws SQLException if a database error ocurred
     */
    public void registerLogWithLogMiner(LogFile log) throws SQLException {
        Objects.requireNonNull(log);

        String query = "BEGIN sys.dbms_logmnr.add_logfile(LOGFILENAME => '" + log.getFileName() + "', OPTIONS => DBMS_LOGMNR.ADDFILE); END;";
        try (CallableStatement statement = connection.prepareCall(query)) {
            statement.execute();
        }
    }

    /**
     * Start an Oracle LogMiner session.
     *
     * @param startScn the mining range starting system change number, should never be {@code null}
     * @param endScn the mining range ending system change number, should never be {@code null}
     * @throws SQLException if a database error occurred
     */
    public void startMiningSession(String startScn, String endScn) throws SQLException {
        Objects.requireNonNull(startScn);
        Objects.requireNonNull(endScn);

        try (Statement statement = connection.createStatement()) {
            final StringBuilder query = new StringBuilder();
            query.append("BEGIN sys.dbms_logmnr.start_logmnr");
            query.append("( startScn => '").append(startScn).append("'");
            query.append(", endScn => '").append(endScn).append("'");
            query.append(", OPTIONS => DBMS_LOGMNR.DICT_FROM_ONLINE_CATALOG + DBMS_LOGMNR.NO_ROWID_IN_STMT );");
            query.append("END;");
            statement.execute(query.toString());
        }
    }

    /**
     * End an Oracle LogMiner session.
     *
     * @throws SQLException if a database error occurred
     */
    public void endMiningSession() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("BEGIN sys.dbms_logmnr.end_logmnr(); end;");
        }
    }

    public void queryTable(String tableName, ResultSetConsumer consumer) throws SQLException {
        query("SELECT * FROM " + tableName, rs -> {
            while (rs.next()) {
                consumer.accept(rs);
            }
        });
    }

    public void executeQuery(String query, ResultSetConsumer consumer) throws SQLException {
        query(query, rs -> {
            while (rs.next()) {
                consumer.accept(rs);
            }
        });
    }

    private void query(String query, ResultSetConsumer consumer) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery(query)) {
                consumer.accept(rs);
            }
        }
    }

    private String getMinableLogsQuery(String startScn, String endScn) {
        // todo:
        final StringBuilder sb = new StringBuilder();
        // if (!archiveLogOnlyMode) {
        sb.append("SELECT MIN(F.MEMBER) AS FILE_NAME, L.FIRST_CHANGE# FIRST_CHANGE, L.NEXT_CHANGE# NEXT_CHANGE, L.ARCHIVED, ");
        sb.append("L.STATUS, 'ONLINE' AS TYPE, L.SEQUENCE# AS SEQ, 'NO' AS DICT_START, 'NO' AS DICT_END ");
        sb.append("FROM V$LOGFILE F, V$LOG L ");
        sb.append("LEFT JOIN V$ARCHIVED_LOG A ");
        sb.append("ON A.FIRST_CHANGE# = L.FIRST_CHANGE# AND A.NEXT_CHANGE# = L.NEXT_CHANGE# ");
        sb.append("WHERE A.FIRST_CHANGE# IS NULL ");
        sb.append("AND F.GROUP# = L.GROUP# ");
        sb.append("GROUP BY F.GROUP#, L.FIRST_CHANGE#, L.NEXT_CHANGE#, L.STATUS, L.ARCHIVED, L.SEQUENCE# ");
        sb.append("UNION ");
        // }
        sb.append("SELECT A.NAME AS FILE_NAME, A.FIRST_CHANGE# FIRST_CHANGE, A.NEXT_CHANGE# NEXT_CHANGE, 'YES', ");
        sb.append("NULL, 'ARCHIVED', A.SEQUENCE# AS SEQ, A.DICTIONARY_BEGIN, A.DICTIONARY_END ");
        sb.append("FROM V$ARCHIVED_LOG A ");
        sb.append("WHERE A.NAME IS NOT NULL ");
        sb.append("AND A.ARCHIVED = 'YES' ");
        sb.append("AND A.STATUS = 'A' ");
        sb.append("AND A.NEXT_CHANGE# > ").append(startScn).append(" ");
        sb.append("AND A.DEST_ID IN (").append(localArchiveLogDestinationsOnlyQuery(null)).append(") ");
        return sb.append("ORDER BY 7").toString();
    }

    private String localArchiveLogDestinationsOnlyQuery(String archiveDestinationName) {
        final StringBuilder query = new StringBuilder(256);
        query.append("SELECT DEST_ID FROM V$ARCHIVE_DEST_STATUS WHERE ");
        query.append("STATUS='VALID' AND TYPE='LOCAL' ");
        if (isNullOrEmpty(archiveDestinationName)) {
            query.append("AND ROWNUM=1");
        }
        else {
            query.append("AND UPPER(DEST_NAME)='").append(archiveDestinationName.toUpperCase()).append("'");
        }
        return query.toString();
    }

    private String getLogMinerQuery(String startScn, String endScn) {
        final StringBuilder query = new StringBuilder();
        query.append(
                "SELECT SCN, SQL_REDO, OPERATION_CODE, TIMESTAMP, XID, CSF, TABLE_NAME, SEG_OWNER, OPERATION, USERNAME, ROW_ID, ROLLBACK, RS_ID, SSN, SEQUENCE#, INFO, STATUS, OBJECT_ID ");
        query.append("FROM V$LOGMNR_CONTENTS ");
        query.append("WHERE SCN > ").append(startScn).append(" AND SCN <= ").append(endScn);
        return query.toString();
    }

    private <T> T queryAndMapSingleValue(String query, ResultSetMapper<T> mapper) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery(query)) {
                if (rs.next()) {
                    final T value = mapper.execute(rs);
                    if (!rs.next()) {
                        return value;
                    }
                }
                throw new RuntimeException("Expected only a single row");
            }
        }
    }

    private static String getBannerQuery(String fieldName) {
        return "SELECT " + fieldName + " FROM V$VERSION WHERE " + fieldName + " LIKE 'Oracle Database%'";
    }

    private static String getDatabaseUri(String hostName, String port, String serviceName) {
        return String.format("jdbc:oracle:thin:@%s:%s/%s", hostName, port, serviceName);
    }

    private static boolean isNullOrEmpty(String str) {
        return str == null || str.isEmpty();
    }

    public static class LogFile {

        public enum Type {
            ARCHIVE,
            REDO
        }

        private final String fileName;
        private final BigInteger firstScn;
        private final BigInteger nextScn;
        private final Long sequence;
        private final Type type;

        public LogFile(String fileName, BigInteger firstScn, BigInteger nextScn, Long sequence, Type type) {
            this.fileName = fileName;
            this.firstScn = firstScn;
            this.nextScn = nextScn;
            this.sequence = sequence;
            this.type = type;
        }

        public String getFileName() {
            return fileName;
        }

        public BigInteger getFirstScn() {
            return firstScn;
        }

        public BigInteger getNextScn() {
            return nextScn;
        }

        public Long getSequence() {
            return sequence;
        }

        public Type getType() {
            return type;
        }
    }
}
