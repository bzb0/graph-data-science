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
package org.neo4j.gds.pagerank;

import org.jetbrains.annotations.Nullable;
import org.neo4j.gds.AlgorithmFactory;
import org.neo4j.gds.StatsProc;
import org.neo4j.gds.config.GraphCreateConfig;
import org.neo4j.gds.core.CypherMapWrapper;
import org.neo4j.gds.result.AbstractResultBuilder;
import org.neo4j.gds.results.MemoryEstimateResult;
import org.neo4j.gds.results.StandardStatsResult;
import org.neo4j.internal.kernel.api.procs.ProcedureCallContext;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.neo4j.procedure.Mode.READ;

public class PageRankStatsProc extends StatsProc<PageRankAlgorithm, PageRankResult, PageRankStatsProc.StatsResult, PageRankStatsConfig> {

    @Procedure(value = "gds.pageRank.stats", mode = READ)
    @Description(STATS_DESCRIPTION)
    public Stream<StatsResult> stats(
        @Name(value = "graphName") Object graphNameOrConfig,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        ComputationResult<PageRankAlgorithm, PageRankResult, PageRankStatsConfig> computationResult = compute(
            graphNameOrConfig,
            configuration
        );
        return stats(computationResult);
    }

    @Procedure(value = "gds.pageRank.stats.estimate", mode = READ)
    @Description(ESTIMATE_DESCRIPTION)
    public Stream<MemoryEstimateResult> estimateStats(
        @Name(value = "graphName") Object graphNameOrConfig,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        return computeEstimate(graphNameOrConfig, configuration);
    }

    @Override
    protected AbstractResultBuilder<StatsResult> resultBuilder(ComputationResult<PageRankAlgorithm, PageRankResult, PageRankStatsConfig> computeResult) {
        return PageRankProc.resultBuilder(
            new StatsResult.Builder(callContext, computeResult.config().concurrency()),
            computeResult
        );
    }

    @Override
    protected void validateConfigsBeforeLoad(GraphCreateConfig graphCreateConfig, PageRankStatsConfig config) {
        super.validateConfigsBeforeLoad(graphCreateConfig, config);
        PageRankProc.validateAlgoConfig(config, log);
    }

    @Override
    protected PageRankStatsConfig newConfig(
        String username,
        Optional<String> graphName,
        Optional<GraphCreateConfig> maybeImplicitCreate,
        CypherMapWrapper config
    ) {
        return PageRankStatsConfig.of(username, graphName, maybeImplicitCreate, config);
    }

    @Override
    protected AlgorithmFactory<PageRankAlgorithm, PageRankStatsConfig> algorithmFactory() {
        return new PageRankAlgorithmFactory<>();
    }

    @SuppressWarnings("unused")
    public static class StatsResult extends StandardStatsResult {

        public final long ranIterations;
        public final boolean didConverge;
        public final Map<String, Object> centralityDistribution;

        StatsResult(
            long ranIterations,
            boolean didConverge,
            @Nullable Map<String, Object> centralityDistribution,
            long createMillis,
            long computeMillis,
            long postProcessingMillis,
            Map<String, Object> configuration
        ) {
            super(createMillis, computeMillis, postProcessingMillis, configuration);
            this.ranIterations = ranIterations;
            this.didConverge = didConverge;
            this.centralityDistribution = centralityDistribution;
        }

        static class Builder extends PageRankProc.PageRankResultBuilder<StatsResult> {

            Builder(ProcedureCallContext context, int concurrency) {
                super(context, concurrency);
            }

            @Override
            public StatsResult buildResult() {
                return new StatsResult(
                    ranIterations,
                    didConverge,
                    centralityHistogramOrNull(),
                    createMillis,
                    computeMillis,
                    postProcessingMillis,
                    config.toMap()
                );
            }
        }
    }
}
