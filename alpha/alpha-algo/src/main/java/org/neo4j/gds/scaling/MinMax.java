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
package org.neo4j.gds.scaling;

import org.neo4j.graphalgo.api.NodeProperties;
import org.neo4j.graphalgo.core.concurrency.ParallelUtil;
import org.neo4j.graphalgo.core.utils.partition.Partition;
import org.neo4j.graphalgo.core.utils.partition.PartitionUtils;

import java.util.concurrent.ExecutorService;

final class MinMax implements Scaler {

    private final NodeProperties properties;
    final double min;
    final double maxMinDiff;

    private MinMax(NodeProperties properties, double min, double maxMinDiff) {
        this.properties = properties;
        this.min = min;
        this.maxMinDiff = maxMinDiff;
    }

    static Scaler create(NodeProperties properties, long nodeCount, int concurrency, ExecutorService executor) {
        var tasks = PartitionUtils.rangePartition(
            concurrency,
            nodeCount,
            partition -> new ComputeMaxMin(partition, properties)
        );

        ParallelUtil.runWithConcurrency(concurrency, tasks, executor);

        var min = tasks.stream().mapToDouble(ComputeMaxMin::min).min().orElse(Double.MAX_VALUE);
        var max = tasks.stream().mapToDouble(ComputeMaxMin::max).max().orElse(-Double.MAX_VALUE);

        var maxMinDiff = max - min;

        if (Math.abs(maxMinDiff) < CLOSE_TO_ZERO) {
            return ZERO_SCALER;
        } else {
            return new MinMax(properties, min, maxMinDiff);
        }
    }

    @Override
    public void scaleProperty(long nodeId, double[] result, int offset) {
        result[offset] = (properties.doubleValue(nodeId) - min) / maxMinDiff;
    }

    static class ComputeMaxMin extends AggregatesComputer {

        private double min;
        private double max;

        ComputeMaxMin(Partition partition, NodeProperties property) {
            super(partition, property);
            this.min = Double.MAX_VALUE;
            this.max = -Double.MAX_VALUE;
        }

        @Override
        void compute(long nodeId) {
            var propertyValue = properties.doubleValue(nodeId);
            if (propertyValue < min) {
                min = propertyValue;
            }
            if (propertyValue > max) {
                max = propertyValue;
            }
        }

        double max() {
            return max;
        }

        double min() {
            return min;
        }
    }

}
