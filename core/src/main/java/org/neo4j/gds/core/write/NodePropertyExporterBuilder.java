/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.gds.core.write;

import org.neo4j.gds.api.IdMapping;
import org.neo4j.gds.config.ConcurrencyConfig;
import org.neo4j.gds.core.TransactionContext;
import org.neo4j.gds.core.utils.BatchingProgressLogger;
import org.neo4j.gds.core.utils.ProgressLogger;
import org.neo4j.gds.core.utils.TerminationFlag;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.core.utils.progress.tasks.TaskProgressTracker;
import org.neo4j.gds.core.utils.progress.tasks.Tasks;
import org.neo4j.logging.Log;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.LongUnaryOperator;

public abstract class NodePropertyExporterBuilder<T extends NodePropertyExporter> {
    protected final TransactionContext transactionContext;
    protected LongUnaryOperator toOriginalId;
    protected long nodeCount;
    protected TerminationFlag terminationFlag;

    protected ExecutorService executorService;
    protected ProgressLogger progressLogger;
    protected int writeConcurrency;
    protected ProgressTracker progressTracker;

    NodePropertyExporterBuilder(TransactionContext transactionContext) {
        this.transactionContext = Objects.requireNonNull(transactionContext);
        this.writeConcurrency = ConcurrencyConfig.DEFAULT_CONCURRENCY;
        this.progressLogger = ProgressLogger.NULL_LOGGER;
        this.progressTracker = ProgressTracker.NULL_TRACKER;
    }

    public abstract T build();

    public NodePropertyExporterBuilder<T> withIdMapping(IdMapping idMapping) {
        Objects.requireNonNull(idMapping);
        this.nodeCount = idMapping.nodeCount();
        this.toOriginalId = idMapping::toOriginalNodeId;
        return this;
    }

    public NodePropertyExporterBuilder<T> withTerminationFlag(TerminationFlag terminationFlag) {
        this.terminationFlag = terminationFlag;
        return this;
    }

    public NodePropertyExporterBuilder<T> withLog(Log log) {
        var task = Tasks.leaf(taskName(), nodeCount);
        this.progressLogger = new BatchingProgressLogger(log, task, writeConcurrency);
        var progressTracker = new TaskProgressTracker(task, progressLogger);
        return withProgressTracker(progressTracker);
    }

    public NodePropertyExporterBuilder<T> withProgressTracker(ProgressTracker progressTracker) {
        this.progressTracker = progressTracker;
        return this;
    }

    public NodePropertyExporterBuilder<T> parallel(ExecutorService es, int writeConcurrency) {
        this.executorService = es;
        this.writeConcurrency = writeConcurrency;
        return this;
    }

    protected String taskName() {
        return "WriteNodeProperties";
    }
}
