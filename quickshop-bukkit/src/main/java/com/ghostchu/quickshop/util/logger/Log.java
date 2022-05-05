/*
 *  This file is a part of project QuickShop, the name is QuickLogger.java
 *  Copyright (C) Ghost_chu and contributors
 *
 *  This program is free software: you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License as published by the
 *  Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.ghostchu.quickshop.util.logger;

import com.ghostchu.quickshop.QuickShop;
import com.ghostchu.quickshop.util.Util;
import com.google.common.collect.EvictingQueue;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;

public class Log {
    private static final ReentrantReadWriteLock LOCK = new ReentrantReadWriteLock();
    private static final int bufferSize = 1500 * Type.values().length;
    private static final Queue<Record> loggerBuffer = EvictingQueue.create(bufferSize);

    private static final boolean disableLocationRecording;

    static {
        disableLocationRecording = Boolean.parseBoolean(System.getProperty("com.ghostchu.quickshop.util.logger.disableLocationRecoding"));
    }


    public static void debug(@NotNull String message) {
        debug(Level.INFO, message, Caller.create());
    }

    public static void cron(@NotNull String message) {
        cron(Level.INFO, message, Caller.create());
    }

    public static void transaction(@NotNull String message) {
        transaction(Level.INFO, message, Caller.create());
    }


    public static void debug(@NotNull Level level, @NotNull String message) {
        debug(level, message, Caller.create());
    }

    public static void cron(@NotNull Level level, @NotNull String message) {
        cron(level, message, Caller.create());
    }

    public static void transaction(@NotNull Level level, @NotNull String message) {
        transaction(level, message, Caller.create());
    }

    public static void debug(@NotNull Level level, @NotNull String message, @Nullable Caller caller) {
        LOCK.writeLock().lock();
        Record record;
        if (disableLocationRecording)
            record = new Record(level, Type.DEBUG, message, null);
        else
            record = new Record(level, Type.DEBUG, message, caller);
        loggerBuffer.offer(record);
        debugStdOutputs(record);
        LOCK.writeLock().unlock();
    }

    public static void cron(@NotNull Level level, @NotNull String message, @Nullable Caller caller) {
        LOCK.writeLock().lock();
        Record record;
        if (disableLocationRecording)
            record = new Record(level, Type.CRON, message, null);
        else
            record = new Record(level, Type.CRON, message, caller);
        loggerBuffer.offer(record);
        debugStdOutputs(record);
        LOCK.writeLock().unlock();
    }

    public static void transaction(@NotNull Level level, @NotNull String message, @Nullable Caller caller) {
        LOCK.writeLock().lock();
        Record record;
        if (disableLocationRecording)
            record = new Record(level, Type.TRANSACTION, message, null);
        else
            record = new Record(level, Type.TRANSACTION, message, caller);
        loggerBuffer.offer(record);
        debugStdOutputs(record);
        LOCK.writeLock().unlock();
    }

    private static void debugStdOutputs(Record record) {
        if (Util.isDevMode()) {
            QuickShop.getInstance().getLogger().info("[DEBUG] " + record.toString());
        }
    }

    @NotNull
    public static List<Record> fetchLogs() {
        LOCK.readLock().lock();
        List<Record> records = new ArrayList<>(loggerBuffer);
        LOCK.readLock().unlock();
        return records;
    }

    @NotNull
    public static List<Record> fetchLogs(@NotNull Type type) {
        LOCK.readLock().lock();
        List<Record> records = loggerBuffer.stream().filter(record -> record.getType() == type).toList();
        LOCK.readLock().unlock();
        return records;
    }

    @NotNull
    public static List<Record> fetchLogs(@NotNull Type type, @NotNull Level level) {
        LOCK.readLock().lock();
        List<Record> records = loggerBuffer.stream().filter(record -> record.getType() == type && record.getLevel() == level).toList();
        LOCK.readLock().unlock();
        return records;
    }


    @Getter
    @EqualsAndHashCode
    public static class Record {
        private final long nanoTime = System.nanoTime();
        @NotNull
        private final Level level;
        @NotNull
        private final Type type;
        @NotNull
        private final String message;
        @Nullable
        private final Caller caller;
        @Nullable
        private String toStringCache;

        public Record(@NotNull Level level, @NotNull Type type, @NotNull String message, @Nullable Caller caller) {
            this.level = level;
            this.type = type;
            this.message = message;
            this.caller = caller;
        }

        @Override
        public String toString() {
            if (toStringCache != null)
                return toStringCache;
            StringBuilder sb = new StringBuilder();
            Log.Caller caller = this.getCaller();
            //noinspection IfStatementWithIdenticalBranches
            if (caller != null) {
                sb.append("[");
                sb.append(caller.getThreadName());
                sb.append("/");
                sb.append(this.getLevel().getName());
                sb.append("]");
                sb.append(" ");
                sb.append("(");
                sb.append(caller.getClassName()).append("#").append(caller.getMethodName()).append(":").append(caller.getLineNumber());
                sb.append(")");
                sb.append(" ");
            } else {
                sb.append("[");
                sb.append(this.getLevel().getName());
                sb.append("]");
                sb.append(" ");
            }
            sb.append(this.getMessage());
            toStringCache = sb.toString();
            return toStringCache;
        }

    }

    @AllArgsConstructor
    @Data
    public static class Caller {
        @NotNull
        private final static StackWalker stackWalker = StackWalker.getInstance(Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE), 3);
        @NotNull
        private final String threadName;
        @NotNull
        private final String className;
        @NotNull
        private final String methodName;
        private final int lineNumber;

        @NotNull
        public static Caller create() {
            List<StackWalker.StackFrame> caller = stackWalker.walk(frames -> frames.limit(3).toList());
            StackWalker.StackFrame frame = caller.get(2);
            String threadName = Thread.currentThread().getName();
            String className = frame.getClassName();
            String methodName = frame.getMethodName();
            int codeLine = frame.getLineNumber();
            return new Caller(threadName, className, methodName, codeLine);
        }
    }


    enum Type {
        DEBUG,
        CRON,
        TRANSACTION
    }

}
