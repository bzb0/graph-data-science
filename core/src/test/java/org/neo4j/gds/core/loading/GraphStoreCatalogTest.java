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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.gds.extension.GdlExtension;
import org.neo4j.gds.extension.GdlGraph;
import org.neo4j.gds.extension.Inject;
import org.neo4j.gds.gdl.GdlFactory;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.config.GraphCreateFromStoreConfig;
import org.neo4j.kernel.database.DatabaseIdFactory;
import org.neo4j.kernel.database.NamedDatabaseId;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.neo4j.gds.extension.GdlSupportExtension.DATABASE_ID;

@GdlExtension
class GraphStoreCatalogTest {

    private static final String USER_NAME = "alice";
    private static final String GRAPH_NAME = "graph";
    private static final GraphCreateFromStoreConfig CONFIG = GraphCreateFromStoreConfig.emptyWithName(USER_NAME, GRAPH_NAME);

    @GdlGraph
    private static final String TEST_GRAPH = "()";

    @Inject
    private GraphStore graphStore;

    @Test
    void set() {
        Assertions.assertFalse(GraphStoreCatalog.exists(USER_NAME, DATABASE_ID, GRAPH_NAME));
        GraphStoreCatalog.set(CONFIG, graphStore);
        assertTrue(GraphStoreCatalog.exists(USER_NAME, DATABASE_ID, GRAPH_NAME));
    }

    @Test
    void get() {
        GraphStoreCatalog.set(CONFIG, graphStore);
        var graphStoreWithConfig = GraphStoreCatalog.get(
            CatalogRequest.of(USER_NAME, DATABASE_ID),
            GRAPH_NAME
        );
        assertEquals(graphStore, graphStoreWithConfig.graphStore());
        assertEquals(CONFIG, graphStoreWithConfig.config());
    }

    @Test
    void getAsAdminReturnsOtherUsersGraphs() {
        GraphStoreCatalog.set(CONFIG, graphStore);
        var graphStoreWithConfig = GraphStoreCatalog.get(
            CatalogRequest.ofAdmin("admin", DATABASE_ID),
            GRAPH_NAME
        );
        assertEquals(graphStore, graphStoreWithConfig.graphStore());
        assertEquals(CONFIG, graphStoreWithConfig.config());
    }

    @Test
    void getAsAdminReturnsGraphsFromOwnCatalogIfAvailable() {
        var adminConfig = GraphCreateFromStoreConfig.emptyWithName("admin", GRAPH_NAME);
        GraphStoreCatalog.set(adminConfig, graphStore); // {} -> admin -> DB -> GRAPH_NAME -> graphStore
        GraphStoreCatalog.set(CONFIG, graphStore);      // {} -> alice -> DB -> GRAPH_NAME -> graphStore

        var graphStoreWithConfig = GraphStoreCatalog.get(
            CatalogRequest.ofAdmin("admin", DATABASE_ID),
            GRAPH_NAME
        );
        assertEquals(graphStore, graphStoreWithConfig.graphStore());
        assertEquals(adminConfig, graphStoreWithConfig.config());
        assertNotEquals(CONFIG, graphStoreWithConfig.config());
    }

    @Test
    void getAsAdminThrowsIfNoGraphIsMatching() {
        assertThatThrownBy(() -> GraphStoreCatalog.get(CatalogRequest.ofAdmin("admin", DATABASE_ID), GRAPH_NAME))
            .hasMessage("Graph with name `%s` does not exist on database `%s`.", GRAPH_NAME, DATABASE_ID.name());
    }

    @Test
    void getAsAdminSuggestsOnlyOwnGraphs() {
        GraphStoreCatalog.set(GraphCreateFromStoreConfig.emptyWithName("admin", GRAPH_NAME + "y"), graphStore);
        GraphStoreCatalog.set(GraphCreateFromStoreConfig.emptyWithName("alice", GRAPH_NAME + "z"), graphStore);

        assertThatThrownBy(() -> GraphStoreCatalog.get(
            CatalogRequest.ofAdmin("admin", DATABASE_ID),
            GRAPH_NAME + "x"
        ))
            .hasMessage(
                "Graph with name `%s` does not exist on database `%s`. Did you mean `%s`?",
                GRAPH_NAME + "x",
                DATABASE_ID.name(),
                GRAPH_NAME + "y"
            );
    }

    @Test
    void getAsAdminFailsOnMultipleGraphsMatching() {
        GraphStoreCatalog.set(GraphCreateFromStoreConfig.emptyWithName("alice", GRAPH_NAME), graphStore);
        GraphStoreCatalog.set(GraphCreateFromStoreConfig.emptyWithName("bob", GRAPH_NAME), graphStore);

        assertThatThrownBy(() -> GraphStoreCatalog.get(CatalogRequest.ofAdmin("admin", DATABASE_ID), GRAPH_NAME))
            .hasMessage("Multiple graphs that match '%s' are found from the users alice and bob.", GRAPH_NAME);
    }

    @Test
    void getAsAdminUsesTheUserOverrideToSelectFromMultipleGraphsMatching() {
        var aliceConfig = GraphCreateFromStoreConfig.emptyWithName("alice", GRAPH_NAME);
        GraphStoreCatalog.set(aliceConfig, graphStore);
        GraphStoreCatalog.set(GraphCreateFromStoreConfig.emptyWithName("bob", GRAPH_NAME), graphStore);

        var graphStoreWithConfig = GraphStoreCatalog.get(
            CatalogRequest.ofAdmin("admin", Optional.of("alice"), DATABASE_ID),
            GRAPH_NAME
        );
        assertEquals(graphStore, graphStoreWithConfig.graphStore());
        assertEquals(aliceConfig, graphStoreWithConfig.config());
    }

    @Test
    void getAsAdminReturnsTheGraphFromTheOverrideEvenIfAGraphFromOwnCatalogIsAvailable() {
        var adminConfig = GraphCreateFromStoreConfig.emptyWithName("admin", GRAPH_NAME);
        GraphStoreCatalog.set(adminConfig, graphStore);
        GraphStoreCatalog.set(CONFIG, graphStore);

        var graphStoreWithConfig = GraphStoreCatalog.get(
            CatalogRequest.ofAdmin("admin", Optional.of(USER_NAME), DATABASE_ID),
            GRAPH_NAME
        );
        assertEquals(graphStore, graphStoreWithConfig.graphStore());
        assertEquals(CONFIG, graphStoreWithConfig.config());
        assertNotEquals(adminConfig, graphStoreWithConfig.config());
    }

    @Test
    void getAsAdminReturnsOnlyTheGraphFromTheOverrideEvenIfAGraphFromOwnCatalogIsAvailable() {
        var adminConfig = GraphCreateFromStoreConfig.emptyWithName("admin", GRAPH_NAME);
        GraphStoreCatalog.set(adminConfig, graphStore);

        assertThatThrownBy(() -> GraphStoreCatalog.get(
            CatalogRequest.ofAdmin("admin", Optional.of(USER_NAME), DATABASE_ID),
            GRAPH_NAME
        ))
            .hasMessage("Graph with name `%s` does not exist on database `%s`.", GRAPH_NAME, DATABASE_ID.name());
    }

    @Test
    void getAsAdminReturnsOnlyTheGraphFromTheOverrideEvenIfAGraphFromADifferentUserIsAvailable() {
        var bobConfig = GraphCreateFromStoreConfig.emptyWithName("bob", GRAPH_NAME);
        GraphStoreCatalog.set(bobConfig, graphStore);

        assertThatThrownBy(() -> GraphStoreCatalog.get(
            CatalogRequest.ofAdmin("admin", Optional.of(USER_NAME), DATABASE_ID),
            GRAPH_NAME
        ))
            .hasMessage("Graph with name `%s` does not exist on database `%s`.", GRAPH_NAME, DATABASE_ID.name());
    }

    @Test
    void remove() {
        GraphStoreCatalog.set(CONFIG, graphStore);
        assertTrue(GraphStoreCatalog.exists(USER_NAME, DATABASE_ID, GRAPH_NAME));
        GraphStoreCatalog.remove(
            CatalogRequest.of(USER_NAME, DATABASE_ID),
            GRAPH_NAME,
            graphStoreWithConfig -> {},
            true
        );
        assertFalse(GraphStoreCatalog.exists(USER_NAME, DATABASE_ID, GRAPH_NAME));
    }

    @Test
    void removeAsAdmin() {
        GraphStoreCatalog.set(CONFIG, graphStore);
        GraphStoreCatalog.remove(
            CatalogRequest.ofAdmin("admin", DATABASE_ID),
            GRAPH_NAME,
            graphStoreWithConfig -> {
                assertEquals(graphStore, graphStoreWithConfig.graphStore());
            },
            true
        );
        assertFalse(GraphStoreCatalog.exists(USER_NAME, DATABASE_ID, GRAPH_NAME));
    }

    @Test
    void removeAsAdminPrefersOwnGraphCatalog() {
        var adminConfig = GraphCreateFromStoreConfig.emptyWithName("admin", GRAPH_NAME);
        GraphStoreCatalog.set(adminConfig, graphStore);
        GraphStoreCatalog.set(CONFIG, graphStore);

        GraphStoreCatalog.remove(
            CatalogRequest.ofAdmin("admin", DATABASE_ID),
            GRAPH_NAME,
            graphStoreWithConfig -> {
                assertEquals(graphStore, graphStoreWithConfig.graphStore());
            },
            true
        );
        assertFalse(GraphStoreCatalog.exists("admin", DATABASE_ID, GRAPH_NAME));
        assertTrue(GraphStoreCatalog.exists(USER_NAME, DATABASE_ID, GRAPH_NAME));
    }

    @Test
    void removeAsAdminThrowsIfNoGraphIsMatching() {
        assertThatThrownBy(() -> GraphStoreCatalog.remove(
            CatalogRequest.ofAdmin("admin", DATABASE_ID),
            GRAPH_NAME,
            graphStoreWithConfig -> fail("How did you remove a graph that never existed?"),
            true
        ))
            .hasMessage("Graph with name `%s` does not exist on database `%s`.", GRAPH_NAME, DATABASE_ID.name());
    }

    @Test
    void removeAsAdminDoesNotThrowIfNoGraphAreMatchingIfFailIfMissingFlagIsFalse() {
        assertThatCode(() -> GraphStoreCatalog.remove(
            CatalogRequest.ofAdmin("admin", DATABASE_ID),
            GRAPH_NAME,
            graphStoreWithConfig -> fail("How did you remove a graph that never existed?"),
            false
        ))
            .doesNotThrowAnyException();
    }

    @Test
    void removeAsAdminSuggestsOnlyOwnGraphs() {
        GraphStoreCatalog.set(GraphCreateFromStoreConfig.emptyWithName("admin", GRAPH_NAME + "y"), graphStore);
        GraphStoreCatalog.set(GraphCreateFromStoreConfig.emptyWithName("alice", GRAPH_NAME + "z"), graphStore);

        assertThatThrownBy(() -> GraphStoreCatalog.remove(
            CatalogRequest.ofAdmin("admin", DATABASE_ID),
            GRAPH_NAME + "x",
            graphStoreWithConfig -> fail("Should not have removed the graph"),
            true
        ))
            .hasMessage(
                "Graph with name `%s` does not exist on database `%s`. Did you mean `%s`?",
                GRAPH_NAME + "x",
                DATABASE_ID.name(),
                GRAPH_NAME + "y"
            );
    }

    @Test
    void removeAsAdminFailsOnMultipleGraphsMatching() {
        GraphStoreCatalog.set(GraphCreateFromStoreConfig.emptyWithName("alice", GRAPH_NAME), graphStore);
        GraphStoreCatalog.set(GraphCreateFromStoreConfig.emptyWithName("bob", GRAPH_NAME), graphStore);

        assertThatThrownBy(() -> GraphStoreCatalog.remove(
            CatalogRequest.ofAdmin("admin", DATABASE_ID),
            GRAPH_NAME,
            graphStoreWithConfig -> fail("Should not have removed the graph"),
            true
        ))
            .hasMessage("Multiple graphs that match '%s' are found from the users alice and bob.", GRAPH_NAME);
    }

    @Test
    void removeAsAdminUsesTheUserOverrideToSelectFromMultipleGraphsMatching() {
        GraphStoreCatalog.set(GraphCreateFromStoreConfig.emptyWithName("alice", GRAPH_NAME), graphStore);
        GraphStoreCatalog.set(GraphCreateFromStoreConfig.emptyWithName("bob", GRAPH_NAME), graphStore);

        GraphStoreCatalog.remove(
            CatalogRequest.ofAdmin("admin", Optional.of("alice"), DATABASE_ID),
            GRAPH_NAME,
            graphStoreWithConfig -> {
                assertEquals(graphStore, graphStoreWithConfig.graphStore());
            },
            true
        );
        assertFalse(GraphStoreCatalog.exists("alice", DATABASE_ID, GRAPH_NAME));
        assertTrue(GraphStoreCatalog.exists("bob", DATABASE_ID, GRAPH_NAME));
    }

    @Test
    void removeDoesNotRemoveOtherUsersGraphs() {
        GraphStoreCatalog.set(GraphCreateFromStoreConfig.emptyWithName("bob", GRAPH_NAME), graphStore);

        GraphStoreCatalog.remove(
            CatalogRequest.of(USER_NAME, DATABASE_ID),
            GRAPH_NAME,
            graphStoreWithConfig -> fail("Should not have removed the graph"),
            false
        );
        assertTrue(GraphStoreCatalog.exists("bob", DATABASE_ID, GRAPH_NAME));
    }

    @Test
    void removeAsAdminReturnsTheGraphFromTheOverrideEvenIfAGraphFromOwnCatalogIsAvailable() {
        var adminConfig = GraphCreateFromStoreConfig.emptyWithName("admin", GRAPH_NAME);
        GraphStoreCatalog.set(adminConfig, graphStore);
        GraphStoreCatalog.set(CONFIG, graphStore);

        GraphStoreCatalog.remove(
            CatalogRequest.ofAdmin("admin", Optional.of(USER_NAME), DATABASE_ID),
            GRAPH_NAME,
            graphStoreWithConfig -> {
                assertEquals(graphStore, graphStoreWithConfig.graphStore());
            },
            true
        );
        assertFalse(GraphStoreCatalog.exists(USER_NAME, DATABASE_ID, GRAPH_NAME));
        assertTrue(GraphStoreCatalog.exists("admin", DATABASE_ID, GRAPH_NAME));
    }

    @Test
    void removeAsAdminReturnsOnlyTheGraphFromTheOverrideEvenIfAGraphFromOwnCatalogIsAvailable() {
        GraphStoreCatalog.set(GraphCreateFromStoreConfig.emptyWithName("admin", GRAPH_NAME), graphStore);

        assertThatThrownBy(() -> GraphStoreCatalog.remove(
            CatalogRequest.ofAdmin("admin", Optional.of(USER_NAME), DATABASE_ID),
            GRAPH_NAME,
            graphStoreWithConfig -> fail("Should not have removed the graph"),
            true
        ))
            .hasMessage("Graph with name `%s` does not exist on database `%s`.", GRAPH_NAME, DATABASE_ID.name());

        assertTrue(GraphStoreCatalog.exists("admin", DATABASE_ID, GRAPH_NAME));
    }

    @Test
    void removeAsAdminReturnsOnlyTheGraphFromTheOverrideEvenIfAGraphFromADifferentUserIsAvailable() {
        var bobConfig = GraphCreateFromStoreConfig.emptyWithName("bob", GRAPH_NAME);
        GraphStoreCatalog.set(bobConfig, graphStore);

        assertThatThrownBy(() -> GraphStoreCatalog.remove(
            CatalogRequest.ofAdmin("admin", Optional.of(USER_NAME), DATABASE_ID),
            GRAPH_NAME,
            graphStoreWithConfig -> fail("Should not have removed the graph"),
            true
        ))
            .hasMessage("Graph with name `%s` does not exist on database `%s`.", GRAPH_NAME, DATABASE_ID.name());

        assertTrue(GraphStoreCatalog.exists("bob", DATABASE_ID, GRAPH_NAME));
    }

    @Test
    void graphStoresCount() {
        assertEquals(0, GraphStoreCatalog.graphStoresCount(DATABASE_ID));
        GraphStoreCatalog.set(CONFIG, graphStore);
        assertEquals(1, GraphStoreCatalog.graphStoresCount(DATABASE_ID));
        GraphStoreCatalog.remove(
            CatalogRequest.of(USER_NAME, DATABASE_ID),
            GRAPH_NAME,
            graphStoreWithConfig -> {},
            true
        );
        assertEquals(0, GraphStoreCatalog.graphStoresCount(DATABASE_ID));
    }

    @Test
    void graphStoresCountAcrossUsers() {
        assertEquals(0, GraphStoreCatalog.graphStoresCount());
        GraphStoreCatalog.set(CONFIG, graphStore);
        assertEquals(1, GraphStoreCatalog.graphStoresCount());
        GraphStoreCatalog.set(GraphCreateFromStoreConfig.emptyWithName("bob", GRAPH_NAME), graphStore);
        assertEquals(2, GraphStoreCatalog.graphStoresCount());
        GraphStoreCatalog.set(GraphCreateFromStoreConfig.emptyWithName("clive", GRAPH_NAME), graphStore);
        assertEquals(3, GraphStoreCatalog.graphStoresCount());
        GraphStoreCatalog.remove(
            CatalogRequest.of(USER_NAME, DATABASE_ID),
            GRAPH_NAME,
            graphStoreWithConfig -> {},
            true
        );
        assertEquals(2, GraphStoreCatalog.graphStoresCount());
    }

    @Test
    void removeAllLoadedGraphs() {
        GraphStoreCatalog.set(CONFIG, graphStore);
        assertEquals(1, GraphStoreCatalog.graphStoresCount(DATABASE_ID));
        GraphStoreCatalog.removeAllLoadedGraphs();
        assertEquals(0, GraphStoreCatalog.graphStoresCount(DATABASE_ID));
    }

    @Test
    void multipleDatabaseIds() {
        GraphCreateFromStoreConfig config0 = GraphCreateFromStoreConfig.emptyWithName(USER_NAME, "graph0");
        GraphCreateFromStoreConfig config1 = GraphCreateFromStoreConfig.emptyWithName(USER_NAME, "graph1");

        NamedDatabaseId namedDatabaseId0 = DatabaseIdFactory.from("DB_0", UUID.fromString("0-0-0-0-0"));
        NamedDatabaseId namedDatabaseId1 = DatabaseIdFactory.from("DB_1", UUID.fromString("0-0-0-0-1"));

        GraphStore graphStore0 = GdlFactory
            .builder()
            .gdlGraph("()-->()")
            .namedDatabaseId(namedDatabaseId0)
            .build()
            .build()
            .graphStore();
        GraphStore graphStore1 = GdlFactory
            .builder()
            .gdlGraph("()-->()-->()")
            .namedDatabaseId(namedDatabaseId1)
            .build()
            .build()
            .graphStore();

        GraphStoreCatalog.set(config0, graphStore0);
        GraphStoreCatalog.set(config1, graphStore1);

        assertTrue(GraphStoreCatalog.exists(USER_NAME, namedDatabaseId0, "graph0"));
        assertFalse(GraphStoreCatalog.exists(USER_NAME, namedDatabaseId0, "graph1"));

        assertTrue(GraphStoreCatalog.exists(USER_NAME, namedDatabaseId1, "graph1"));
        assertFalse(GraphStoreCatalog.exists(USER_NAME, namedDatabaseId1, "graph0"));
    }

    static Stream<Arguments> graphInput() {
        return Stream.of(
            Arguments.of("db_0", List.of(), "graph", "Graph with name `graph` does not exist on database `db_0`."),
            Arguments.of(
                "db_1",
                List.of("graph0"),
                "graph1",
                "Graph with name `graph1` does not exist on database `db_1`. Did you mean `graph0`?"
            ),
            Arguments.of(
                "db_2",
                List.of("graph0", "graph1"),
                "graph2",
                "Graph with name `graph2` does not exist on database `db_2`. Did you mean one of [`graph0`, `graph1`]?"
            ),
            Arguments.of(
                "db_42",
                List.of("graph0", "graph1", "foobar"),
                "graph2",
                "Graph with name `graph2` does not exist on database `db_42`. Did you mean one of [`graph0`, `graph1`]?"
            )
        );
    }

    @ParameterizedTest
    @MethodSource("graphInput")
    void shouldThrowOnMissingGraph(
        String dbName,
        Iterable<String> existingGraphs,
        String searchGraphName,
        String expectedMessage
    ) {
        var dummyDatabaseId = DatabaseIdFactory.from(dbName, UUID.fromString("0-0-0-0-0"));
        var dummyGraphStore = GdlFactory.of("()").build().graphStore();

        existingGraphs.forEach(existingGraphName -> {
            var config = GraphCreateFromStoreConfig.emptyWithName(USER_NAME, existingGraphName);
            GraphStoreCatalog.set(config, dummyGraphStore);
        });

        // test the get code path
        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> GraphStoreCatalog.get(
                CatalogRequest.of(USER_NAME, dummyDatabaseId),
                searchGraphName
            ))
            .withMessage(expectedMessage);

        // test the drop code path
        assertThatExceptionOfType(NoSuchElementException.class)
            .isThrownBy(() -> GraphStoreCatalog.remove(
                CatalogRequest.of(USER_NAME, dummyDatabaseId),
                searchGraphName,
                graphStoreWithConfig -> {},
                true
            ))
            .withMessage(expectedMessage);
    }
}
