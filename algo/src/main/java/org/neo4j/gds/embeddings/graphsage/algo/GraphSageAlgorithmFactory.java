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
package org.neo4j.gds.embeddings.graphsage.algo;

import org.jetbrains.annotations.TestOnly;
import org.neo4j.gds.AlgorithmFactory;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.config.MutateConfig;
import org.neo4j.gds.core.concurrency.Pools;
import org.neo4j.gds.core.utils.ProgressLogger;
import org.neo4j.gds.core.utils.mem.AllocationTracker;
import org.neo4j.gds.core.utils.mem.MemoryEstimation;
import org.neo4j.gds.core.utils.mem.MemoryEstimations;
import org.neo4j.gds.core.utils.paged.HugeObjectArray;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.core.utils.progress.tasks.Task;
import org.neo4j.gds.core.utils.progress.tasks.Tasks;
import org.neo4j.gds.embeddings.graphsage.GraphSageHelper;

import static org.neo4j.gds.core.utils.mem.MemoryEstimations.RESIDENT_MEMORY;
import static org.neo4j.gds.core.utils.mem.MemoryEstimations.TEMPORARY_MEMORY;
import static org.neo4j.gds.core.utils.mem.MemoryUsage.sizeOfDoubleArray;
import static org.neo4j.gds.ml.core.EmbeddingUtils.validateRelationshipWeightPropertyValue;

public class GraphSageAlgorithmFactory<CONFIG extends GraphSageBaseConfig> extends AlgorithmFactory<GraphSage, CONFIG> {

    public GraphSageAlgorithmFactory() {
        super();
    }

    @Override
    protected String taskName() {
        return GraphSage.class.getSimpleName();
    }

    @Override
    public GraphSage build(
        Graph graph,
        CONFIG configuration,
        AllocationTracker allocationTracker,
        ProgressTracker progressTracker
    ) {

        var executorService = Pools.DEFAULT;
        if(configuration.model().trainConfig().hasRelationshipWeightProperty()) {
            validateRelationshipWeightPropertyValue(graph, configuration.concurrency(), executorService);
        }

        return new GraphSage(
            graph,
            configuration,
            executorService,
            allocationTracker,
            progressTracker
        );
    }

    @Override
    public MemoryEstimation memoryEstimation(CONFIG config) {
        return MemoryEstimations.setup(
            "",
            graphDimensions -> withNodeCount(
                config.model().trainConfig(),
                graphDimensions.nodeCount(),
                config instanceof MutateConfig
            )
        );
    }

    @Override
    public Task progressTask(Graph graph, CONFIG config) {
        return Tasks.leaf(taskName(), graph.nodeCount());
    }

    private MemoryEstimation withNodeCount(GraphSageTrainConfig config, long nodeCount, boolean mutate) {
        var gsBuilder = MemoryEstimations.builder("GraphSage");

        if (mutate) {
            gsBuilder = gsBuilder.startField(RESIDENT_MEMORY)
                .add(
                    "resultFeatures",
                    HugeObjectArray.memoryEstimation(sizeOfDoubleArray(config.embeddingDimension()))
                ).endField();
        }

        var builder = gsBuilder
            .startField(TEMPORARY_MEMORY)
            .field("this.instance", GraphSage.class)
            .add(
                "initialFeatures",
                HugeObjectArray.memoryEstimation(sizeOfDoubleArray(config.estimationFeatureDimension()))
            )
            .perThread(
                "concurrentBatches",
                MemoryEstimations.builder().add(
                    GraphSageHelper.embeddingsEstimation(config, config.batchSize(), nodeCount, 0, false)
                ).build()
            );
        if (!mutate) {
            builder = builder.add(
                "resultFeatures",
                HugeObjectArray.memoryEstimation(sizeOfDoubleArray(config.embeddingDimension()))
            );
        }
        return builder.endField().build();
    }

    @TestOnly
    public GraphSageAlgorithmFactory(ProgressLogger.ProgressLoggerFactory loggerFactory) {
        super(loggerFactory);
    }
}
