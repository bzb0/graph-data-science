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
package org.neo4j.gds.labelpropagation;

import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.NodeProperties;
import org.neo4j.gds.api.RelationshipIterator;
import org.neo4j.gds.core.utils.collection.primitive.PrimitiveLongIterable;
import org.neo4j.gds.core.utils.collection.primitive.PrimitiveLongIterator;
import org.neo4j.gds.core.utils.paged.HugeLongArray;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;

import static org.neo4j.gds.labelpropagation.LabelPropagation.DEFAULT_WEIGHT;

final class ComputeStep implements Step {

    private final RelationshipIterator localRelationshipIterator;
    private final HugeLongArray existingLabels;
    private final PrimitiveLongIterable nodes;
    private final ProgressTracker progressTracker;
    private final ComputeStepConsumer consumer;
    private final Graph graph;

    private boolean didChange = true;

    ComputeStep(
            Graph graph,
            NodeProperties nodeWeights,
            ProgressTracker progressTracker,
            HugeLongArray existingLabels,
            PrimitiveLongIterable nodes) {
        this.existingLabels = existingLabels;
        this.progressTracker = progressTracker;
        this.graph = graph;
        this.localRelationshipIterator = graph.concurrentCopy();
        this.nodes = nodes;
        this.consumer = new ComputeStepConsumer(nodeWeights, existingLabels);
    }

    @Override
    public Step next() {
        return this;
    }

    @Override
    public void run() {
        this.didChange = iterateAll(nodes.iterator());
    }

    @Override
    public boolean didConverge() {
        return !this.didChange;
    }

    private boolean iterateAll(PrimitiveLongIterator nodeIds) {
        boolean didChange = false;
        while (nodeIds.hasNext()) {
            long nodeId = nodeIds.next();
            didChange = compute(nodeId, didChange);
            progressTracker.logProgress(graph.degree(nodeId));
        }
        return didChange;
    }

    private boolean compute(long nodeId, boolean didChange) {
        consumer.clearVotes();
        long label = existingLabels.get(nodeId);
        localRelationshipIterator.forEachRelationship(nodeId, DEFAULT_WEIGHT, consumer);
        long newLabel = consumer.tallyVotes(label);
        if (newLabel != label) {
            existingLabels.set(nodeId, newLabel);
            return true;
        }
        return didChange;
    }

    @Override
    public void release() {
        consumer.release();
    }
}
