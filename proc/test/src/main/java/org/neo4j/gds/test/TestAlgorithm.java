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
package org.neo4j.gds.test;

import org.neo4j.gds.Algorithm;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.core.utils.mem.AllocationTracker;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;

public class TestAlgorithm extends Algorithm<TestAlgorithm, TestAlgorithm> {

    private final Graph graph;
    private final AllocationTracker allocationTracker;
    private long relationshipCount = 0;
    private final long memoryLimit;
    private final boolean throwInCompute;

    public TestAlgorithm(
        Graph graph,
        AllocationTracker allocationTracker,
        long memoryLimit,
        ProgressTracker progressTracker,
        boolean throwInCompute
    ) {
        this.graph = graph;
        this.allocationTracker = allocationTracker;
        this.memoryLimit = memoryLimit;
        this.throwInCompute = throwInCompute;
        this.progressTracker = progressTracker;
    }


    @Override
    public TestAlgorithm compute() {
        progressTracker.beginSubTask();

        if (throwInCompute) {
            throw new IllegalStateException("boo");
        }
        relationshipCount = graph.relationshipCount();
        allocationTracker.add(memoryLimit * 2);

        progressTracker.endSubTask();

        return this;
    }

    @Override
    public TestAlgorithm me() {
        return this;
    }

    @Override
    public void release() {}

    long relationshipCount() {
        return relationshipCount;
    }
}
