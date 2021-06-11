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
package org.neo4j.gds.embeddings.fastrp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.neo4j.gds.ml.core.features.FeatureExtraction;
import org.neo4j.gds.ml.core.features.FeatureExtractor;
import org.neo4j.graphalgo.AlgoTestBase;
import org.neo4j.graphalgo.NodeLabel;
import org.neo4j.graphalgo.Orientation;
import org.neo4j.graphalgo.PropertyMapping;
import org.neo4j.graphalgo.RelationshipType;
import org.neo4j.graphalgo.StoreLoaderBuilder;
import org.neo4j.graphalgo.TestProgressLogger;
import org.neo4j.graphalgo.api.DefaultValue;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.GraphStore;
import org.neo4j.graphalgo.beta.generator.RandomGraphGenerator;
import org.neo4j.graphalgo.beta.generator.RelationshipDistribution;
import org.neo4j.graphalgo.core.Aggregation;
import org.neo4j.graphalgo.core.GraphLoader;
import org.neo4j.graphalgo.core.ImmutableGraphDimensions;
import org.neo4j.graphalgo.core.utils.mem.AllocationTracker;
import org.neo4j.graphalgo.core.utils.paged.HugeObjectArray;
import org.neo4j.graphalgo.extension.GdlExtension;
import org.neo4j.graphalgo.extension.GdlGraph;
import org.neo4j.graphalgo.extension.IdFunction;
import org.neo4j.graphalgo.extension.Inject;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.neo4j.gds.ml.core.la.operations.FloatVectorOperations.l2Normalize;
import static org.neo4j.graphalgo.utils.StringFormatting.formatWithLocale;

class FastRPTest extends AlgoTestBase {

    private static final int DEFAULT_EMBEDDING_DIMENSION = 128;
    static final FastRPBaseConfig DEFAULT_CONFIG = FastRPBaseConfig.builder()
        .embeddingDimension(DEFAULT_EMBEDDING_DIMENSION)
        .propertyDimension(DEFAULT_EMBEDDING_DIMENSION/2)
        .featureProperties(List.of("f1", "f2"))
        .addIterationWeight(1.0D)
        .randomSeed(42L)
        .build();

    private static final String DB_CYPHER =
        "CREATE" +
        "  (a:Node1 {f1: 0.4, f2: [1.3, 1.4]})" +
        ", (b:Node1 {f1: 2.1, f2: [0.5, 1.8]})" +
        ", (c:Node2 {f1: -0.3, f2: [0.8, 2.8]})" +
        ", (d:Isolated {f1: 2.5, f2: [8.1, 1.3]})" +
        ", (e:Isolated {f1: -0.6, f2: [0.5, 5.2]})" +
        ", (a)-[:REL {weight: 2.0}]->(b)" +
        ", (b)-[:REL {weight: 1.0}]->(a)" +
        ", (a)-[:REL {weight: 1.0}]->(c)" +
        ", (c)-[:REL {weight: 1.0}]->(a)" +
        ", (b)-[:REL {weight: 1.0}]->(c)" +
        ", (c)-[:REL {weight: 1.0}]->(b)";

    @BeforeEach
    void setupGraphDb() {
        runQuery(DB_CYPHER);
    }

    @Test
    void shouldSwapInitialRandomVectors() {
        GraphLoader graphLoader = new StoreLoaderBuilder()
            .api(db)
            .addNodeLabel("Node1")
            .nodeProperties(List.of(PropertyMapping.of("f1"), PropertyMapping.of("f2")))
            .build();

        Graph graph = graphLoader.graph();

        FastRP fastRP = new FastRP(
            graph,
            DEFAULT_CONFIG,
            defaultFeatureExtractors(graph),
            progressLogger,
            AllocationTracker.empty()
        );

        fastRP.initPropertyVectors();
        fastRP.initRandomVectors();
        HugeObjectArray<float[]> randomVectors = HugeObjectArray.newArray(float[].class, 2, AllocationTracker.empty());
        fastRP.currentEmbedding(-1).copyTo(randomVectors, 2);
        fastRP.propagateEmbeddings();
        HugeObjectArray<float[]> embeddings = fastRP.embeddings();

        float[] expected = randomVectors.get(1);
        l2Normalize(expected);
        assertArrayEquals(expected, embeddings.get(0));
    }

    @Test
    void shouldAverageNeighbors() {
        GraphLoader graphLoader = new StoreLoaderBuilder()
            .api(db)
            .addNodeLabel("Node1")
            .addNodeLabel("Node2")
            .nodeProperties(List.of(PropertyMapping.of("f1"), PropertyMapping.of("f2")))
            .build();

        Graph graph = graphLoader.graph();

        FastRP fastRP = new FastRP(
            graph,
            DEFAULT_CONFIG,
            defaultFeatureExtractors(graph),
            progressLogger,
            AllocationTracker.empty()
        );

        fastRP.initPropertyVectors();
        fastRP.initRandomVectors();
        HugeObjectArray<float[]> randomVectors = HugeObjectArray.newArray(float[].class, 3, AllocationTracker.empty());
        fastRP.currentEmbedding(-1).copyTo(randomVectors, 3);
        fastRP.propagateEmbeddings();
        HugeObjectArray<float[]> embeddings = fastRP.embeddings();

        float[] expected = new float[DEFAULT_EMBEDDING_DIMENSION];
        for (int i = 0; i < DEFAULT_EMBEDDING_DIMENSION; i++) {
            expected[i] = (randomVectors.get(1)[i] + randomVectors.get(2)[i]) / 2.0f;
        }
        l2Normalize(expected);
        assertArrayEquals(expected, embeddings.get(0));
    }

    @Test
    void shouldBeIndpendentOfPartitioning() {
        GraphLoader graphLoader = new StoreLoaderBuilder()
            .api(db)
            .addNodeLabel("Node1")
            .addNodeLabel("Node2")
            .nodeProperties(List.of(PropertyMapping.of("f1"), PropertyMapping.of("f2")))
            .build();

        Graph graph = graphLoader.graph();

        var configBuilder = FastRPBaseConfig.builder()
            .embeddingDimension(DEFAULT_EMBEDDING_DIMENSION)
            .propertyDimension(DEFAULT_EMBEDDING_DIMENSION/2)
            .featureProperties(List.of("f1", "f2"))
            .addIterationWeight(1.0D)
            .randomSeed(42L);

        FastRP concurrentFastRP = new FastRP(
            graph,
            configBuilder.concurrency(4).build(),
            defaultFeatureExtractors(graph),
            progressLogger,
            AllocationTracker.empty()
        );

        concurrentFastRP.compute();
        HugeObjectArray<float[]> concurrentEmbeddings = concurrentFastRP.embeddings();

        FastRP sequentialFastRP = new FastRP(
            graph,
            configBuilder.concurrency(1).build(),
            defaultFeatureExtractors(graph),
            progressLogger,
            AllocationTracker.empty()
        );

        sequentialFastRP.compute();
        HugeObjectArray<float[]> sequentialEmbeddings = sequentialFastRP.embeddings();

        for (int i = 0; i < graph.nodeCount(); i++) {
            assertArrayEquals(sequentialEmbeddings.get(i), concurrentEmbeddings.get(i));
        }
    }

    @Test
    void shouldAverageNeighborsWeighted() {
        GraphLoader graphLoader = new StoreLoaderBuilder()
            .api(db)
            .addNodeLabel("Node1")
            .addNodeLabel("Node2")
            .nodeProperties(List.of(PropertyMapping.of("f1"), PropertyMapping.of("f2")))
            .addRelationshipProperty("weight", "weight", DefaultValue.of(1.0), Aggregation.NONE)
            .build();

        Graph graph = graphLoader.graph();

        var weightedConfig = ImmutableFastRPBaseConfig
            .builder()
            .from(DEFAULT_CONFIG)
            .relationshipWeightProperty("weight")
            .embeddingDimension(DEFAULT_EMBEDDING_DIMENSION)
            .build();

        FastRP fastRP = new FastRP(
            graph,
            weightedConfig,
            defaultFeatureExtractors(graph),
            progressLogger,
            AllocationTracker.empty()
        );

        fastRP.initPropertyVectors();
        fastRP.initRandomVectors();
        HugeObjectArray<float[]> randomVectors = HugeObjectArray.newArray(float[].class, 3, AllocationTracker.empty());
        fastRP.currentEmbedding(-1).copyTo(randomVectors, 3);
        fastRP.propagateEmbeddings();
        HugeObjectArray<float[]> embeddings = fastRP.embeddings();

        float[] expected = new float[DEFAULT_EMBEDDING_DIMENSION];
        for (int i = 0; i < DEFAULT_EMBEDDING_DIMENSION; i++) {
            expected[i] = (2.0f * randomVectors.get(1)[i] + 1.0f * randomVectors.get(2)[i]) / 2.0f;
        }
        l2Normalize(expected);

        assertArrayEquals(expected, embeddings.get(0));
    }

    @Test
    void shouldDistributeValuesCorrectly() {
        GraphLoader graphLoader = new StoreLoaderBuilder()
            .api(db)
            .addNodeLabel("Node1")
            .addNodeLabel("Node2")
            .nodeProperties(List.of(PropertyMapping.of("f1"), PropertyMapping.of("f2")))
            .build();

        Graph graph = graphLoader.graph();

        FastRP fastRP = new FastRP(
            graph,
            FastRPBaseConfig.builder()
                .embeddingDimension(512)
                .addIterationWeight(1.0D)
                .build(),
            List.of(),
            progressLogger,
            AllocationTracker.empty()
        );

        fastRP.initPropertyVectors();
        fastRP.initRandomVectors();
        double p = 1D / 6D;
        int maxNumPositive = (int) ((p + 5D * Math.sqrt((p * (1 - p)) / 512D)) * 512D); // 1:30.000.000 chance of failing :P
        int minNumPositive = (int) ((p - 5D * Math.sqrt((p * (1 - p)) / 512D)) * 512D);
        HugeObjectArray<float[]> randomVectors = fastRP.currentEmbedding(-1);
        for (int i = 0; i < graph.nodeCount(); i++) {
            float[] embedding = randomVectors.get(i);
            int numZeros = 0;
            int numPositive = 0;
            for (int j = 0; j < 512; j++) {
                double embeddingValue = embedding[j];
                if (embeddingValue == 0) {
                    numZeros++;
                } else if (embeddingValue > 0) {
                    numPositive++;
                }
            }

            int numNegative = 512 - numZeros - numPositive;
            assertTrue(numPositive >= minNumPositive && numPositive <= maxNumPositive);
            assertTrue(numNegative >= minNumPositive && numNegative <= maxNumPositive);
        }
    }

    @Test
    void shouldYieldEmptyEmbeddingForIsolatedNodes() {
        GraphLoader graphLoader = new StoreLoaderBuilder()
            .api(db)
            .addNodeLabel("Isolated")
            .nodeProperties(List.of(PropertyMapping.of("f1"), PropertyMapping.of("f2")))
            .build();

        Graph graph = graphLoader.graph();

        FastRP fastRP = new FastRP(
            graph,
            FastRPBaseConfig.builder()
                .embeddingDimension(64)
                .addIterationWeights(1.0D, 1.0D, 1.0D, 1.0D)
                .build(),
            List.of(),
            progressLogger,
            AllocationTracker.empty()
        );

        HugeObjectArray<float[]> embeddings = fastRP.embeddings();
        for (int i = 0; i < embeddings.size(); i++) {
            float[] embedding = embeddings.get(i);
            for (double embeddingValue : embedding) {
                assertEquals(0.0f, embeddingValue);
            }
        }
    }

    @Test
    void testMemoryEstimationWithoutIterationWeights() {
        var config = ImmutableFastRPBaseConfig
            .builder()
            .addIterationWeights(1.0D, 1.0D)
            .embeddingDimension(128)
            .build();

        var dimensions = ImmutableGraphDimensions.builder().nodeCount(100).build();

        var estimate = FastRP.memoryEstimation(config).estimate(dimensions, 1).memoryUsage();
        assertEquals(estimate.min, estimate.max);
        assertEquals(159_832, estimate.min);
    }

    @Test
    void testMemoryEstimationWithIterationWeights() {
        var config = ImmutableFastRPBaseConfig
            .builder()
            .embeddingDimension(128)
            .iterationWeights(List.of(1.0D, 2.0D))
            .build();

        var dimensions = ImmutableGraphDimensions.builder().nodeCount(100).build();

        var estimate = FastRP.memoryEstimation(config).estimate(dimensions, 1).memoryUsage();
        assertEquals(estimate.min, estimate.max);
        assertEquals(159_832, estimate.min);
    }

    @Test
    void testProgressLogging() {
        var graph = RandomGraphGenerator
            .builder()
            .nodeCount(100)
            .averageDegree(2)
            .orientation(Orientation.UNDIRECTED)
            .relationshipDistribution(RelationshipDistribution.RANDOM)
            .build()
            .generate();

        var config = ImmutableFastRPBaseConfig
            .builder()
            .embeddingDimension(2)
            .iterationWeights(List.of(1.0D, 2.0D))
            .concurrency(4)
            .build();

        var logger = new TestProgressLogger(
            graph.nodeCount(),
            FastRP.class.getSimpleName(),
            config.concurrency()
        );

        new FastRP(graph, config, List.of(), logger, AllocationTracker.empty()).compute();

        assertTrue(logger.containsMessage(TestProgressLogger.INFO, ":: Start"));
        assertTrue(logger.containsMessage(TestProgressLogger.INFO, "Iteration 1 :: Start"));
        assertTrue(logger.containsMessage(TestProgressLogger.INFO, "Iteration 1 :: Finished"));
        assertTrue(logger.containsMessage(TestProgressLogger.INFO, "Iteration 2 :: Start"));
        assertTrue(logger.containsMessage(TestProgressLogger.INFO, "Iteration 2 :: Finished"));
        assertTrue(logger.containsMessage(TestProgressLogger.INFO, ":: Finished"));
        assertEquals(
            3,
            logger.getMessages(TestProgressLogger.INFO).stream().filter(message -> message.contains("100%")).count()
        );
    }

    private List<FeatureExtractor> defaultFeatureExtractors(Graph graph) {
        return FeatureExtraction.propertyExtractors(graph, DEFAULT_CONFIG.featureProperties());
    }


    @Nested
    @GdlExtension
    class MissingProperties {

        @GdlGraph
        private static final String DB_CYPHER = "CREATE" +
                                                "  (a:N { prop: 1 })" +
                                                ", (b:N)" +
                                                ", (c:NaNRelWeight)" +
                                                ", (d:NaNRelWeight)" +
                                                ", (a)-[:REL]->(b)" +
                                                ", (c)-[:REL]->(d)" +
                                                ", (d)-[:REL {weight: 1.0}]->(d)";

        @Inject
        GraphStore graphStore;

        @Inject
        IdFunction idFunction;

        @Test
        void shouldFailWhenNodePropertiesAreMissing() {
            Graph graph = graphStore.getGraph(NodeLabel.of("N"), RelationshipType.of("REL"), Optional.empty());
            FastRP fastRP = new FastRP(
                graph,
                FastRPBaseConfig.builder()
                    .embeddingDimension(64)
                    .addIterationWeights(1.0D, 1.0D, 1.0D, 1.0D)
                    .addFeatureProperty("prop")
                    .build(),
                FeatureExtraction.propertyExtractors(graph, List.of("prop")),
                progressLogger,
                AllocationTracker.empty()
            );

            assertThatThrownBy(fastRP::initRandomVectors)
                .hasMessageContaining(
                    formatWithLocale("Missing node property for property key `prop` on node with id `%s`.", idFunction.of("b"))
                );
        }

        @Test
        void shouldFailWhenRelationshipWeightIsMissing() {
            Graph graph = graphStore.getGraph(NodeLabel.of("NaNRelWeight"), RelationshipType.of("REL"), Optional.of("weight"));

            var weightedConfig = FastRPBaseConfig.builder()
                .relationshipWeightProperty("weight")
                .embeddingDimension(DEFAULT_EMBEDDING_DIMENSION)
                .addIterationWeight(1.0D)
                .randomSeed(42L)
                .build();

            FastRP fastRP = new FastRP(
                graph,
                weightedConfig,
                List.of(),
                progressLogger,
                AllocationTracker.empty()
            );

            assertThatThrownBy(fastRP::compute)
                .hasMessageContaining(
                    formatWithLocale(
                        "Missing relationship property `weight` on relationship between nodes with ids `%d` and `%d`.",
                        idFunction.of("c"),
                        idFunction.of("d")
                    )
                );
        }
    }
}
