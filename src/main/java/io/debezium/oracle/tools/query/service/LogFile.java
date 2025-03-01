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

/**
 * An immutable representation of an Oracle archive or online redo log file.
 *
 * @author Chris Cranford
 */
public class LogFile {

    public enum Type {
        ARCHIVE,
        ONLINE
    }

    private final String fileName;
    private final BigInteger firstScn;
    private final BigInteger nextScn;
    private final Long sequence;
    private final Type type;
    private final Long redoThread;
    private final Long bytes;

    public LogFile(String fileName, BigInteger firstScn, BigInteger nextScn, Long sequence, Type type, Long redoThread, Long bytes) {
        this.fileName = fileName;
        this.firstScn = firstScn;
        this.nextScn = nextScn;
        this.sequence = sequence;
        this.type = type;
        this.redoThread = redoThread;
        this.bytes = bytes;
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

    public Long getRedoThread() {
        return redoThread;
    }

    public Long getBytes() {
        return bytes;
    }

    @Override
    public String toString() {
        return "LogFile{" +
                "fileName='" + fileName + '\'' +
                ", firstScn=" + firstScn +
                ", nextScn=" + nextScn +
                ", sequence=" + sequence +
                ", type=" + type +
                ", redoThread=" + redoThread +
                ", bytes=" + bytes +
                '}';
    }
}
