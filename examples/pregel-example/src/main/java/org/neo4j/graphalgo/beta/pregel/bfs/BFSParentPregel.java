/*
 * Copyright (c) 2017-2020 "Neo4j,"
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
package org.neo4j.graphalgo.beta.pregel.bfs;

import org.neo4j.graphalgo.beta.pregel.PregelComputation;
import org.neo4j.graphalgo.beta.pregel.PregelContext;
import org.neo4j.graphalgo.beta.pregel.annotation.GDSMode;
import org.neo4j.graphalgo.beta.pregel.annotation.PregelProcedure;

import java.util.Queue;

/**
 * setting the value for each node, to the node-id of its parent.
 * If there are multiple parents at the discovery level/iteration, the parent with the minimum id is chosen.
 *
 */
@PregelProcedure(name = "example.pregel.bfs", modes = {GDSMode.STREAM})
public class BFSParentPregel implements PregelComputation<BFSPregelConfig> {

    private static final double NOT_FOUND = Double.MAX_VALUE;

    @Override
    public void compute(PregelContext<BFSPregelConfig> pregel, long nodeId, Queue<Double> messages) {
        if (pregel.isInitialSuperstep()) {
            if (nodeId == pregel.getConfig().startNode()) {
                pregel.setNodeValue(nodeId, nodeId);
                pregel.sendMessages(nodeId, nodeId);
                pregel.voteToHalt(nodeId);
            } else {
                pregel.setNodeValue(nodeId, NOT_FOUND);
            }
        } else if (messages != null && !messages.isEmpty()) {
            double currentParent = pregel.getNodeValue(nodeId);
            if (currentParent == NOT_FOUND) {
                Double message;
                while ((message = messages.poll()) != null) {
                    // somehow Double.NaN gets in the queue (although only nodeId values are send)
                    if (!Double.isNaN(message)) {
                        currentParent = Double.min(currentParent, message);
                    }
                }

                pregel.setNodeValue(nodeId, currentParent);
                pregel.sendMessages(nodeId, nodeId);
                pregel.voteToHalt(nodeId);
            }
            pregel.voteToHalt(nodeId);
        }
    }
}

