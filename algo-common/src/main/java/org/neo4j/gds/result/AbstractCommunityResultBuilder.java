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
package org.neo4j.gds.result;

import org.HdrHistogram.Histogram;
import org.jetbrains.annotations.Nullable;
import org.neo4j.gds.compat.MapUtil;
import org.neo4j.gds.core.concurrency.Pools;
import org.neo4j.gds.core.utils.ProgressTimer;
import org.neo4j.gds.core.utils.mem.AllocationTracker;
import org.neo4j.internal.kernel.api.procs.ProcedureCallContext;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ExecutorService;
import java.util.function.LongUnaryOperator;

import static org.neo4j.gds.core.utils.statistics.CommunityStatistics.communityCount;
import static org.neo4j.gds.core.utils.statistics.CommunityStatistics.communityCountAndHistogram;

public abstract class AbstractCommunityResultBuilder<WRITE_RESULT> extends AbstractResultBuilder<WRITE_RESULT> {

    private final ExecutorService executorService;
    private final int concurrency;
    private final AllocationTracker allocationTracker;
    protected boolean buildHistogram;
    protected boolean buildCommunityCount;

    protected long postProcessingDuration = -1L;
    protected OptionalLong maybeCommunityCount = OptionalLong.empty();
    protected Optional<Histogram> maybeCommunityHistogram = Optional.empty();
    protected @Nullable Map<String, Object> communityHistogramOrNull() {
        return maybeCommunityHistogram.map(histogram -> MapUtil.map(
            "min", histogram.getMinValue(),
            "mean", histogram.getMean(),
            "max", histogram.getMaxValue(),
            "p50", histogram.getValueAtPercentile(50),
            "p75", histogram.getValueAtPercentile(75),
            "p90", histogram.getValueAtPercentile(90),
            "p95", histogram.getValueAtPercentile(95),
            "p99", histogram.getValueAtPercentile(99),
            "p999", histogram.getValueAtPercentile(99.9)
        )).orElse(null);
    }

    private LongUnaryOperator communityFunction = null;

    protected AbstractCommunityResultBuilder(
        ProcedureCallContext callContext,
        int concurrency,
        AllocationTracker allocationTracker
    ) {
        this(callContext, Pools.DEFAULT, concurrency, allocationTracker);
    }

    protected AbstractCommunityResultBuilder(
        ProcedureCallContext callContext,
        ExecutorService executorService,
        int concurrency,
        AllocationTracker allocationTracker
    ) {
        this.buildHistogram = callContext
            .outputFields()
            .anyMatch(s -> s.equalsIgnoreCase("communityDistribution") || s.equalsIgnoreCase("componentDistribution"));
        this.buildCommunityCount = callContext
            .outputFields()
            .anyMatch(s -> s.equalsIgnoreCase("communityCount") || s.equalsIgnoreCase("componentCount"));
        this.executorService = executorService;
        this.concurrency = concurrency;
        this.allocationTracker = allocationTracker;
    }

    protected abstract WRITE_RESULT buildResult();

    public AbstractCommunityResultBuilder<WRITE_RESULT> withCommunityFunction(LongUnaryOperator communityFunction) {
        this.communityFunction = communityFunction;
        return this;
    }

    @Override
    public WRITE_RESULT build() {
        final ProgressTimer timer = ProgressTimer.start();

        if (communityFunction != null) {
            if (buildCommunityCount && !buildHistogram) {
                maybeCommunityCount = OptionalLong.of(communityCount(
                    nodeCount,
                    communityFunction,
                    executorService,
                    concurrency,
                    allocationTracker
                ));
            } else if (buildCommunityCount || buildHistogram) {
                var communityCountAndHistogram = communityCountAndHistogram(
                    nodeCount,
                    communityFunction,
                    executorService,
                    concurrency,
                    allocationTracker
                );
                maybeCommunityCount = OptionalLong.of(communityCountAndHistogram.componentCount());
                maybeCommunityHistogram = Optional.of(communityCountAndHistogram.histogram());
            }
        }

        timer.stop();

        this.postProcessingDuration = timer.getDuration();

        return buildResult();
    }

}
