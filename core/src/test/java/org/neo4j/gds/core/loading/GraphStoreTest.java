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
package org.neo4j.gds.core.loading;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.gds.BaseTest;
import org.neo4j.gds.extension.Neo4jGraph;
import org.neo4j.gds.NodeLabel;
import org.neo4j.gds.NodeProjection;
import org.neo4j.gds.Orientation;
import org.neo4j.gds.PropertyMapping;
import org.neo4j.gds.PropertyMappings;
import org.neo4j.gds.RelationshipProjection;
import org.neo4j.gds.RelationshipType;
import org.neo4j.gds.StoreLoaderBuilder;
import org.neo4j.gds.api.DefaultValue;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.api.Relationships;
import org.neo4j.gds.api.schema.RelationshipSchema;
import org.neo4j.gds.core.Aggregation;
import org.neo4j.gds.core.GraphLoader;
import org.neo4j.gds.core.huge.TransientCompressedList;
import org.neo4j.gds.core.loading.NullPropertyMap.DoubleNullPropertyMap;
import org.neo4j.gds.core.utils.paged.HugeIntArray;
import org.neo4j.gds.core.utils.paged.HugeLongArray;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.neo4j.gds.TestSupport.assertGraphEquals;
import static org.neo4j.gds.TestSupport.fromGdl;
import static org.neo4j.gds.NodeLabel.ALL_NODES;

class GraphStoreTest extends BaseTest {

    @Neo4jGraph
    private static final String DB_CYPHER =
        "CREATE" +
        "  (i1:Ignore)" +
        ", (a:A {nodeProperty: 33, a: 33})" +
        ", (b:B {nodeProperty: 42, b: 42})" +
        ", (i2:Ignore)" +
        ", (a)-[:T1 {property1: 42, property2: 1337}]->(b)" +
        ", (a)-[:T2 {property1: 43}]->(b)" +
        ", (a)-[:T3 {property2: 1338}]->(b)" +
        ", (a)-[:T1 {property1: 33}]->(c)" +
        ", (c)-[:T1 {property1: 33}]->(a)" +
        ", (b)-[:T1 {property1: 33}]->(c)" +
        ", (c)-[:T1 {property1: 33}]->(b)";

    @ParameterizedTest(name = "{0}")
    @MethodSource("validRelationshipFilterParameters")
    void testFilteringGraphsByRelationships(
        String desc,
        List<RelationshipType> relTypes,
        Optional<String> relProperty,
        String expectedGraph
    ) {
        GraphLoader graphLoader = new StoreLoaderBuilder()
            .api(db)
            .graphName("myGraph")
            .addNodeProjection(NodeProjection.of("A"))
            .addNodeProjection(NodeProjection.of("B"))
            .relationshipProjections(relationshipProjections())
            .build();

        GraphStore graphStore = graphLoader.graphStore();

        Graph filteredGraph = graphStore.getGraph(relTypes, relProperty);

        assertGraphEquals(fromGdl(expectedGraph), filteredGraph);

        assertEquals(graphStore.schema().nodeSchema(), filteredGraph.schema().nodeSchema());

        var expectedRelationshipSchema = relTypes
            .stream()
            .map(relType -> graphStore.schema().relationshipSchema().singleTypeAndProperty(relType, relProperty))
            .reduce(RelationshipSchema::union)
            .get();
        assertEquals(expectedRelationshipSchema, filteredGraph.schema().relationshipSchema());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validNodeFilterParameters")
    void testFilteringGraphsByNodeLabels(String desc, List<NodeLabel> labels, String expectedGraph) {
        GraphLoader graphLoader = new StoreLoaderBuilder()
            .api(db)
            .graphName("myGraph")
            .nodeProjections(nodeProjections())
            .addRelationshipProjection(RelationshipProjection.of("T1", Orientation.NATURAL))
            .build();

        GraphStore graphStore = graphLoader.graphStore();

        Graph filteredGraph = graphStore.getGraph(labels, graphStore.relationshipTypes(), Optional.empty());

        assertGraphEquals(fromGdl(expectedGraph), filteredGraph);

        assertEquals(graphStore.schema().filterNodeLabels(new HashSet<>(labels)), filteredGraph.schema());
    }

    @Test
    void testModificationDate() throws InterruptedException {
        GraphStore graphStore = new StoreLoaderBuilder()
            .api(db)
            .build()
            .graphStore();

        // add node properties
        ZonedDateTime initialTime = graphStore.modificationTime();
        Thread.sleep(42);
        graphStore.addNodeProperty(ALL_NODES, "foo", new DoubleNullPropertyMap(42.0));
        ZonedDateTime nodePropertyTime = graphStore.modificationTime();

        // add relationships
        Relationships relationships = Relationships.of(
            0L,
            Orientation.NATURAL,
            false,
            new TransientCompressedList(new byte[0][0], HugeIntArray.of(), HugeLongArray.of()),
            null,
            42.0
        );
        Thread.sleep(42);
        graphStore.addRelationshipType(RelationshipType.of("BAR"), Optional.empty(), Optional.empty(), relationships);
        ZonedDateTime relationshipTime = graphStore.modificationTime();

        assertTrue(initialTime.isBefore(nodePropertyTime), "Node property update did not change modificationTime");
        assertTrue(nodePropertyTime.isBefore(relationshipTime), "Relationship update did not change modificationTime");
    }

    @Test
    void testRemoveNodeProperty() {
        runQuery("CREATE (a {nodeProp: 42})-[:REL]->(b {nodeProp: 23})");

        GraphStore graphStore = new StoreLoaderBuilder()
            .api(db)
            .addNodeProperty(PropertyMapping.of("nodeProp", 0D))
            .build()
            .graphStore();

        assertTrue(graphStore.hasNodeProperty(Collections.singletonList(ALL_NODES), "nodeProp"));
        graphStore.removeNodeProperty(ALL_NODES, "nodeProp");
        assertFalse(graphStore.hasNodeProperty(Collections.singletonList(ALL_NODES), "nodeProp"));
    }

    @Test
    void deleteRelationshipsAndProperties() {
        runQuery("CREATE ()-[:REL {p: 2}]->(), ()-[:LER {p: 1}]->(), ()-[:LER {p: 2}]->(), ()-[:LER {q: 2}]->()");

        GraphStore graphStore = new StoreLoaderBuilder()
            .api(db)
            .addRelationshipProjection(RelationshipProjection.of("REL", Orientation.NATURAL)
                .withProperties(
                    PropertyMappings.of(PropertyMapping.of("p", 3.14))
                )
            )
            .addRelationshipProjection(RelationshipProjection.of("LER", Orientation.NATURAL)
                .withProperties(
                    PropertyMappings.of(
                        PropertyMapping.of("p", 3.14),
                        PropertyMapping.of("q", 3.15)
                    )
                )
            )
            .build()
            .graphStore();

        assertThat(graphStore.relationshipCount()).isEqualTo(4L);

        DeletionResult deletionResult = graphStore.deleteRelationships(RelationshipType.of("LER"));

        assertEquals(new HashSet<>(singletonList(RelationshipType.of("REL"))), graphStore.relationshipTypes());
        assertFalse(graphStore.hasRelationshipType(RelationshipType.of("LER")));
        assertEquals(1, graphStore.relationshipCount());

        assertEquals(3, deletionResult.deletedRelationships());
        assertThat(deletionResult.deletedProperties()).containsExactlyInAnyOrderEntriesOf(Map.of("p", 3L, "q", 3L));
    }

    @NotNull
    private static List<NodeProjection> nodeProjections() {
        NodeProjection aMapping = NodeProjection.builder()
            .label("A")
            .properties(PropertyMappings.of(Arrays.asList(
                PropertyMapping.of("nodeProperty", -1D),
                PropertyMapping.of("a", -1D)
            )))
            .build();

        NodeProjection bMapping = NodeProjection.builder()
            .label("B")
            .properties(PropertyMappings.of(Arrays.asList(
                PropertyMapping.of("nodeProperty", -1D),
                PropertyMapping.of("b", -1D)
            )))
            .build();

        return Arrays.asList(aMapping, bMapping);
    }

    @NotNull
    private static List<RelationshipProjection> relationshipProjections() {
        RelationshipProjection t1Mapping = RelationshipProjection.builder()
            .type("T1")
            .orientation(Orientation.NATURAL)
            .aggregation(Aggregation.NONE)
            .properties(
                PropertyMappings.builder()
                    .addMapping("property1", "property1", DefaultValue.of(42D), Aggregation.NONE)
                    .addMapping("property2", "property2", DefaultValue.of(1337D), Aggregation.NONE)
                    .build()
            ).build();

        RelationshipProjection t2Mapping = RelationshipProjection.builder()
            .type("T2")
            .orientation(Orientation.NATURAL)
            .aggregation(Aggregation.NONE)
            .properties(
                PropertyMappings.builder()
                    .addMapping("property1", "property1", DefaultValue.of(42D), Aggregation.NONE)
                    .build()
            ).build();

        RelationshipProjection t3Mapping = RelationshipProjection.builder()
            .type("T3")
            .orientation(Orientation.NATURAL)
            .aggregation(Aggregation.NONE)
            .properties(
                PropertyMappings.builder()
                    .addMapping("property2", "property2", DefaultValue.of(42D), Aggregation.NONE)
                    .build()
            ).build();

        return Arrays.asList(t1Mapping, t2Mapping, t3Mapping);
    }

    static Stream<Arguments> validRelationshipFilterParameters() {
        return Stream.of(
            Arguments.of(
                "filterByRelationshipType",
                singletonList(RelationshipType.of("T1")),
                Optional.empty(),
                "(a:A), (b:B), (a)-[T1]->(b)"
            ),
            Arguments.of(
                "filterByMultipleRelationshipTypes",
                Arrays.asList(RelationshipType.of("T1"), RelationshipType.of("T2")),
                Optional.empty(),
                "(a:A), (b:B), (a)-[T1]->(b), (a)-[T2]->(b)"
            ),
            Arguments.of(
                "filterByAnyRelationshipType",
                Arrays.asList(RelationshipType.of("T1"), RelationshipType.of("T2"), RelationshipType.of("T3")),
                Optional.empty(),
                "(a:A), (b:B), (a)-[T1]->(b), (a)-[T2]->(b), (a)-[T3]->(b)"
            ),
            Arguments.of(
                "filterByRelationshipProperty",
                Arrays.asList(RelationshipType.of("T1"), RelationshipType.of("T2")),
                Optional.of("property1"),
                "(a:A), (b:B), (a)-[T1 {property1: 42.0}]->(b), (a)-[T2 {property1: 43.0}]->(b)"
            ),
            Arguments.of(
                "filterByRelationshipTypeAndProperty",
                singletonList(RelationshipType.of("T1")),
                Optional.of("property1"),
                "(a:A), (b:B), (a)-[T1 {property1: 42.0}]->(b)"
            )
        );
    }

    static Stream<Arguments> validNodeFilterParameters() {
        return Stream.of(
            Arguments.of(
                "filterAllLabels",
                Arrays.asList(NodeLabel.of("A"), NodeLabel.of("B"), NodeLabel.of("Ignore")),
                "(a:A {nodeProperty: 33, a: 33}), (b:B {nodeProperty: 42, b: 42}), (a)-[T1]->(b)"
            ),
            Arguments.of(
                "filterAllTypesExplicit",
                Arrays.asList(NodeLabel.of("A"), NodeLabel.of("B")),
                "(a:A {nodeProperty: 33, a: 33}), (b:B {nodeProperty: 42, b: 42}), (a)-[T1]->(b)"
            ),
            Arguments.of(
                "FilterA",
                singletonList(NodeLabel.of("A")),
                "(a:A {nodeProperty: 33, a: 33})"
            ),
            Arguments.of(
                "FilterB",
                singletonList(NodeLabel.of("B")),
                "(b:B {nodeProperty: 42, b: 42})"
            )
        );
    }
}
