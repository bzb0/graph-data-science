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
package org.neo4j.gds.beta.modularity;

import org.neo4j.gds.AlgorithmFactory;
import org.neo4j.gds.StreamProc;
import org.neo4j.gds.api.NodeProperties;
import org.neo4j.gds.config.GraphCreateConfig;
import org.neo4j.gds.core.CypherMapWrapper;
import org.neo4j.gds.results.MemoryEstimateResult;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.neo4j.gds.beta.modularity.ModularityOptimizationProc.MODULARITY_OPTIMIZATION_DESCRIPTION;
import static org.neo4j.procedure.Mode.READ;

public class ModularityOptimizationStreamProc extends StreamProc<ModularityOptimization, ModularityOptimization, ModularityOptimizationStreamProc.StreamResult, ModularityOptimizationStreamConfig> {

    @Procedure(name = "gds.beta.modularityOptimization.stream", mode = READ)
    @Description(MODULARITY_OPTIMIZATION_DESCRIPTION)
    public Stream<StreamResult> stream(
        @Name(value = "graphName") Object graphNameOrConfig,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        return stream(compute(graphNameOrConfig, configuration));
    }

    @Procedure(value = "gds.beta.modularityOptimization.stream.estimate", mode = READ)
    @Description(ESTIMATE_DESCRIPTION)
    public Stream<MemoryEstimateResult> estimate(
        @Name(value = "graphName") Object graphNameOrConfig,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        return computeEstimate(graphNameOrConfig, configuration);
    }

    @Override
    protected AlgorithmFactory<ModularityOptimization, ModularityOptimizationStreamConfig> algorithmFactory() {
        return new ModularityOptimizationFactory<>();
    }

    @Override
    protected StreamResult streamResult(long originalNodeId, long internalNodeId, NodeProperties nodeProperties) {
        return new StreamResult(originalNodeId, nodeProperties.longValue(internalNodeId));
    }

    @Override
    protected NodeProperties nodeProperties(
        ComputationResult<ModularityOptimization, ModularityOptimization, ModularityOptimizationStreamConfig> computationResult
    ) {
        return ModularityOptimizationProc.nodeProperties(computationResult, allocationTracker());
    }

    @Override
    protected ModularityOptimizationStreamConfig newConfig(
        String username,
        Optional<String> graphName,
        Optional<GraphCreateConfig> maybeImplicitCreate,
        CypherMapWrapper config
    ) {
        return ModularityOptimizationStreamConfig.of(username, graphName, maybeImplicitCreate, config);
    }

    @SuppressWarnings("unused")
    public static class StreamResult {
        public final long nodeId;
        public final long communityId;

        public StreamResult(long nodeId, long communityId) {
            this.nodeId = nodeId;
            this.communityId = communityId;
        }
    }
}
