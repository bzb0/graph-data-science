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
package org.neo4j.gds.similarity;

import org.HdrHistogram.DoubleHistogram;
import org.neo4j.gds.AlgoBaseProc;
import org.neo4j.gds.Algorithm;
import org.neo4j.gds.compat.MapUtil;
import org.neo4j.gds.result.AbstractResultBuilder;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.config.AlgoBaseConfig;
import org.neo4j.gds.core.utils.ProgressTimer;
import org.neo4j.internal.kernel.api.procs.ProcedureCallContext;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.neo4j.gds.core.ProcedureConstants.HISTOGRAM_PRECISION_DEFAULT;

public final class SimilarityProc {

    private SimilarityProc() {}

    public static boolean shouldComputeHistogram(ProcedureCallContext callContext) {
        return callContext
            .outputFields()
            .anyMatch(s -> s.equalsIgnoreCase("similarityDistribution"));
    }

    public static <RESULT, PROC_RESULT, CONFIG extends AlgoBaseConfig> SimilarityResultBuilder<PROC_RESULT> resultBuilder(
        SimilarityResultBuilder<PROC_RESULT> procResultBuilder,
        AlgoBaseProc.ComputationResult<? extends Algorithm<?, ?>, RESULT, CONFIG> computationResult,
        Function<RESULT, SimilarityGraphResult> graphResultFunc
    ) {
        RESULT result = computationResult.result();
        SimilarityGraphResult graphResult = graphResultFunc.apply(result);
        resultBuilder(procResultBuilder, computationResult)
            .withNodesCompared(graphResult.comparedNodes())
            .withRelationshipsWritten(graphResult.similarityGraph().relationshipCount());

        return procResultBuilder;
    }

    public static <PROC_RESULT, CONFIG extends AlgoBaseConfig> SimilarityResultBuilder<PROC_RESULT> resultBuilder(
        SimilarityResultBuilder<PROC_RESULT> procResultBuilder,
        AlgoBaseProc.ComputationResult<? extends Algorithm<?, ?>, ?, CONFIG> computationResult
    ) {
        procResultBuilder
            .withCreateMillis(computationResult.createMillis())
            .withComputeMillis(computationResult.computeMillis())
            .withConfig(computationResult.config());

        return procResultBuilder;
    }

    public static DoubleHistogram computeHistogram(Graph similarityGraph) {
        DoubleHistogram histogram = new DoubleHistogram(HISTOGRAM_PRECISION_DEFAULT);
        similarityGraph.forEachNode(nodeId -> {
            similarityGraph.forEachRelationship(nodeId, Double.NaN, (node1, node2, property) -> {
                histogram.recordValue(property);
                return true;
            });
            return true;
        });
        return histogram;
    }

    public abstract static class SimilarityResultBuilder<PROC_RESULT> extends AbstractResultBuilder<PROC_RESULT> {

        public long nodesCompared = 0L;

        public long postProcessingMillis = -1L;

        Optional<DoubleHistogram> maybeHistogram = Optional.empty();

        public Map<String, Object> distribution() {
            if (maybeHistogram.isPresent()) {
                DoubleHistogram definitelyHistogram = maybeHistogram.get();
                return MapUtil.map(
                    "min", definitelyHistogram.getMinValue(),
                    "max", definitelyHistogram.getMaxValue(),
                    "mean", definitelyHistogram.getMean(),
                    "stdDev", definitelyHistogram.getStdDeviation(),
                    "p1", definitelyHistogram.getValueAtPercentile(1),
                    "p5", definitelyHistogram.getValueAtPercentile(5),
                    "p10", definitelyHistogram.getValueAtPercentile(10),
                    "p25", definitelyHistogram.getValueAtPercentile(25),
                    "p50", definitelyHistogram.getValueAtPercentile(50),
                    "p75", definitelyHistogram.getValueAtPercentile(75),
                    "p90", definitelyHistogram.getValueAtPercentile(90),
                    "p95", definitelyHistogram.getValueAtPercentile(95),
                    "p99", definitelyHistogram.getValueAtPercentile(99),
                    "p100", definitelyHistogram.getValueAtPercentile(100)
                );
            }
            return Collections.emptyMap();
        }

        public SimilarityResultBuilder<PROC_RESULT> withNodesCompared(long nodesCompared) {
            this.nodesCompared = nodesCompared;
            return this;
        }

        public SimilarityResultBuilder<PROC_RESULT> withHistogram(DoubleHistogram histogram) {
            this.maybeHistogram = Optional.of(histogram);
            return this;
        }

        public ProgressTimer timePostProcessing() {
            return ProgressTimer.start(this::setPostProcessingMillis);
        }

        public void setPostProcessingMillis(long postProcessingMillis) {
            this.postProcessingMillis = postProcessingMillis;
        }
    }
}
