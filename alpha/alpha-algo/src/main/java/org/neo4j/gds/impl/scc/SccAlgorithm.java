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
package org.neo4j.gds.impl.scc;

import com.carrotsearch.hppc.BitSet;
import org.neo4j.gds.Algorithm;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.core.utils.mem.AllocationTracker;
import org.neo4j.gds.core.utils.paged.HugeLongArray;
import org.neo4j.gds.core.utils.paged.PagedLongStack;

/**
 * huge iterative (non recursive) sequential strongly connected components algorithm.
 *
 * specified in:  http://code.activestate.com/recipes/578507-strongly-connected-components-of-a-directed-graph/
 */
public class SccAlgorithm extends Algorithm<SccAlgorithm, HugeLongArray> {

    private enum Action {
        VISIT(0L),
        VISITEDGE(1L),
        POSTVISIT(2L);

        public final long code;

        Action(long code) {
            this.code = code;
        }

    }

    private Graph graph;

    private final long nodeCount;
    private HugeLongArray index;
    private BitSet visited;
    private HugeLongArray connectedComponents;
    private PagedLongStack stack;
    private PagedLongStack boundaries;
    private PagedLongStack todo; // stores pairs of (node-Id, TODO-Id)
    private int setCount;

    private int minSetSize;
    private int maxSetSize;

    public SccAlgorithm(Graph graph, AllocationTracker allocationTracker) {
        this.graph = graph;
        nodeCount = graph.nodeCount();
        index = HugeLongArray.newArray(nodeCount, allocationTracker);
        stack = new PagedLongStack(nodeCount, allocationTracker);
        boundaries = new PagedLongStack(nodeCount, allocationTracker);
        connectedComponents = HugeLongArray.newArray(nodeCount, allocationTracker);
        visited = new BitSet(nodeCount);
        todo = new PagedLongStack(nodeCount, allocationTracker);
    }

    /**
     * compute scc
     */
    public HugeLongArray compute() {
        progressTracker.beginSubTask(graph.nodeCount());
        setCount = 0;
        minSetSize = Integer.MAX_VALUE;
        maxSetSize = 0;
        index.fill(-1);
        connectedComponents.fill(-1);
        todo.clear();
        boundaries.clear();
        stack.clear();
        graph.forEachNode(this::compute);
        progressTracker.endSubTask();
        return connectedComponents;
    }

    @Override
    public SccAlgorithm me() {
        return this;
    }

    /**
     * release inner data structures
     */
    @Override
    public void release() {
        graph = null;
        index = null;
        visited = null;
        connectedComponents = null;
        stack = null;
        boundaries = null;
        todo = null;
    }

    /**
     * number of connected components in the graph
     */
    public long getSetCount() {
        return setCount;
    }

    /**
     * minimum set size
     */
    public long getMinSetSize() {
        return minSetSize;
    }

    /**
     * maximum component size
     */
    public long getMaxSetSize() {
        return maxSetSize;
    }

    private boolean compute(long nodeId) {
        if (!running()) {
            return false;
        }
        if (index.get(nodeId) != -1) {
            return true;
        }
        push(Action.VISIT, nodeId);
        while (!todo.isEmpty()) {
            final long action = todo.pop();
            final long node = todo.pop();
            if (action == Action.VISIT.code) {
                visit(node);
            } else if (action == Action.VISITEDGE.code) {
                visitEdge(node);
            } else {
                postVisit(node);
            }
        }
        progressTracker.logProgress();
        return true;
    }

    private void visitEdge(long nodeId) {
        if (index.get(nodeId) == -1) {
            push(Action.VISIT, nodeId);
        } else if (!visited.get(nodeId)) {
            while (index.get(nodeId) < boundaries.peek()) {
                boundaries.pop();
            }
        }
    }

    private void postVisit(long nodeId) {
        if (boundaries.peek() == index.get(nodeId)) {
            boundaries.pop();
            int elementCount = 0;
            long element;
            do {
                element = stack.pop();
                connectedComponents.set(element, nodeId);
                visited.set(element);
                elementCount++;
            } while (element != nodeId);
            minSetSize = Math.min(minSetSize, elementCount);
            maxSetSize = Math.max(maxSetSize, elementCount);
            setCount++;
        }

    }

    private void visit(long nodeId) {
        final long stackSize = stack.size();
        index.set(nodeId, stackSize);
        stack.push(nodeId);
        boundaries.push(stackSize);
        push(Action.POSTVISIT, nodeId);
        graph.forEachRelationship(nodeId, (s, t) -> {
            push(Action.VISITEDGE, t);
            return true;
        });
    }

    /**
     * pushes an action and a nodeId on the stack
     *
     * @param action
     * @param value
     */
    private void push(Action action, long value) {
        todo.push(value);
        todo.push(action.code);
    }

    /**
     * stream result type
     */
    public static class StreamResult {

        public final long nodeId;
        public final long componentId;

        public StreamResult(long nodeId, long componentId) {
            this.nodeId = nodeId;
            this.componentId = componentId;
        }
    }
}
