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

import org.neo4j.gds.AlgorithmFactory;
import org.neo4j.gds.StatsProc;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.config.GraphCreateConfig;
import org.neo4j.gds.core.CypherMapWrapper;
import org.neo4j.gds.core.utils.mem.AllocationTracker;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.result.AbstractResultBuilder;
import org.neo4j.gds.results.MemoryEstimateResult;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.neo4j.procedure.Mode.READ;

public class TestProc extends StatsProc<TestAlgorithm, TestAlgorithm, TestProc.TestResult, TestConfig> {

    @Procedure(value = "gds.testProc.test", mode = READ)
    @Description(STATS_DESCRIPTION)
    public Stream<TestResult> stats(
        @Name(value = "graphName") Object graphNameOrConfig,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        ComputationResult<TestAlgorithm, TestAlgorithm, TestConfig> computationResult = compute(
            graphNameOrConfig,
            configuration
        );
        return stats(computationResult);
    }

    @Procedure(value = "gds.testProc.test.estimate", mode = READ)
    @Description(ESTIMATE_DESCRIPTION)
    public Stream<MemoryEstimateResult> estimateStats(
        @Name(value = "graphName") Object graphNameOrConfig,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        return computeEstimate(graphNameOrConfig, configuration);
    }

    @Override
    protected AbstractResultBuilder<TestResult> resultBuilder(ComputationResult<TestAlgorithm, TestAlgorithm, TestConfig> computeResult) {
        return new TestAlgoResultBuilder().withRelationshipCount(computeResult.result().relationshipCount());
    }

    @Override
    protected TestConfig newConfig(
        String username,
        Optional<String> graphName,
        Optional<GraphCreateConfig> maybeImplicitCreate,
        CypherMapWrapper config
    ) {
        return TestConfig.of(username, graphName, maybeImplicitCreate, config);
    }

    @Override
    protected AlgorithmFactory<TestAlgorithm, TestConfig> algorithmFactory() {
        return new AlgorithmFactory<>() {

            @Override
            protected String taskName() {
                return "TestAlgorithm";
            }

            @Override
            protected TestAlgorithm build(
                Graph graph, TestConfig configuration, AllocationTracker allocationTracker, ProgressTracker progressTracker
            ) {
                return new TestAlgorithm(
                    graph,
                    allocationTracker,
                    0L,
                    progressTracker,
                    configuration.throwInCompute()
                );
            }
        };
    }

    static class TestAlgoResultBuilder extends AbstractResultBuilder<TestResult> {

        long relationshipCount = 0;

        @Override
        public TestResult build() {
            return new TestResult(
                createMillis,
                computeMillis,
                relationshipCount,
                config.toMap()
            );
        }

        TestAlgoResultBuilder withRelationshipCount(long relationshipCount) {
            this.relationshipCount = relationshipCount;
            return this;
        }
    }

    public static final class TestResult {

        public long createMillis;
        public long computeMillis;
        public long relationshipCount;
        public Map<String, Object> configuration;

        TestResult(long createMillis, long computeMillis, long relationshipCount, Map<String, Object> configuration) {
            this.createMillis = createMillis;
            this.computeMillis = computeMillis;
            this.relationshipCount = relationshipCount;
            this.configuration = configuration;
        }
    }
}
