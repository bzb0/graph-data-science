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
package org.neo4j.gds.catalog;

import org.assertj.core.api.Assertions;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.neo4j.gds.BaseProcTest;
import org.neo4j.gds.GdsCypher;
import org.neo4j.gds.TestLog;
import org.neo4j.gds.core.TransactionContext;
import org.neo4j.gds.core.loading.GraphStoreCatalog;
import org.neo4j.gds.core.utils.progress.EmptyTaskRegistry;
import org.neo4j.gds.core.write.NativeRelationshipExporter;
import org.neo4j.internal.kernel.api.procs.ProcedureCallContext;

import java.util.Map;

import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.neo4j.gds.assertj.Extractors.removingThreadId;
import static org.neo4j.gds.compat.GraphDatabaseApiProxy.newKernelTransaction;
import static org.neo4j.gds.compat.MapUtil.map;
import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;

class GraphWriteRelationshipProcTest extends BaseProcTest {
    private static final String TEST_GRAPH_NAME = "testGraph";

    private static final String DB_CYPHER =
        "CREATE" +
        "  (a:Node)" +
        ", (b:Node)" +
        ", (c:Node)" +
        ", (d:Node)" +
        ", (e:Node)" +
        ", (f:Node)" +
        ", (a)-[:REL1 { relProp1: 1.0 }]->(b)" +
        ", (a)-[:REL1 { relProp1: 2.0 }]->(c)" +
        ", (a)-[:REL2 { relProp2: 3.0 }]->(d)" +
        ", (d)-[:REL2 { relProp2: 4.0 }]->(e)";

    @BeforeEach
    void setup() throws Exception {
        registerProcedures(GraphCreateProc.class, GraphWriteRelationshipProc.class);
        runQuery(DB_CYPHER);

        runQuery(GdsCypher.call()
            .withAnyLabel()
            .withRelationshipType("NEW_REL1", "REL1")
            .withRelationshipType("NEW_REL2", "REL2")
            .withRelationshipProperty("newRelProp1", "relProp1")
            .withRelationshipProperty("newRelProp2", "relProp2")
            .graphCreate(TEST_GRAPH_NAME)
            .yields()
        );
    }

    @AfterEach
    void tearDown() {
        GraphStoreCatalog.removeAllLoadedGraphs();
    }

    @Test
    void writeRelationship() {
        String graphWriteQuery = formatWithLocale(
            "CALL gds.graph.writeRelationship('%s', 'NEW_REL1')" +
            "YIELD writeMillis, graphName, relationshipType, relationshipProperty, relationshipsWritten, propertiesWritten",
            TEST_GRAPH_NAME
        );

        runQueryWithRowConsumer(graphWriteQuery, row -> {
            assertThat(-1L, Matchers.lessThan(row.getNumber("writeMillis").longValue()));
            assertEquals(TEST_GRAPH_NAME, row.getString("graphName"));
            assertEquals("NEW_REL1", row.get("relationshipType"));
            assertNull(row.get("relationshipProperty"));
            assertEquals(2L, row.getNumber("relationshipsWritten").longValue());
            assertEquals(0L, row.getNumber("propertiesWritten").longValue());
        });

        String validationQuery =
            "MATCH (n)-[r:NEW_REL1]->(m) " +
            "RETURN type(r) AS relType, count(r) AS count";

        assertCypherResult(validationQuery, singletonList(
            map("relType", "NEW_REL1", "count", 2L)
        ));
    }

    @ParameterizedTest
    @CsvSource(value = {
        "NEW_REL1,newRelProp1,3.0,2",
        "NEW_REL1,newRelProp2,0.0,2",
        "NEW_REL2,newRelProp1,0.0,2",
        "NEW_REL2,newRelProp2,7.0,2"
    })
    void writeRelationshipAndProperty(
        String relType,
        String relProperty,
        double sum,
        int relationshipsWritten
    ) {
        String graphWriteQuery = formatWithLocale(
            "CALL gds.graph.writeRelationship('%s', '%s', '%s')" +
            "YIELD writeMillis, graphName, relationshipType, relationshipProperty, relationshipsWritten, propertiesWritten",
            TEST_GRAPH_NAME,
            relType,
            relProperty
        );

        runQueryWithRowConsumer(graphWriteQuery, row -> {
            assertThat(-1L, Matchers.lessThan(row.getNumber("writeMillis").longValue()));
            assertEquals(TEST_GRAPH_NAME, row.getString("graphName"));
            assertEquals(relType, row.get("relationshipType"));
            assertEquals(relProperty, row.get("relationshipProperty"));
            assertEquals(relationshipsWritten, row.getNumber("relationshipsWritten").longValue());
            assertEquals(relationshipsWritten, row.getNumber("propertiesWritten").longValue());
        });

        String validationQuery = formatWithLocale(
            "MATCH (n)-[r:%s]->(m) " +
            "RETURN type(r) AS relType, count(r) AS count, toFloat(sum(r.%s)) AS sum",
            relType,
            relProperty
        );

        assertCypherResult(validationQuery, singletonList(
            map("relType", relType, "count", 2L, "sum", sum)
        ));
    }

    @Test
    void shouldFailOnNonExistingRelationshipType() {
        assertError(
            "CALL gds.graph.writeRelationship($graph, 'NEW_REL42')",
            Map.of("graph", TEST_GRAPH_NAME),
            "Relationship type `NEW_REL42` not found. " +
            "Available types: ['NEW_REL1', 'NEW_REL2']"
        );
    }

    @Test
    void shouldFailOnNonExistingRelationshipProperty() {
        assertError(
            "CALL gds.graph.writeRelationship($graph, 'NEW_REL1', 'nonExisting')",
            Map.of("graph", TEST_GRAPH_NAME),
            "Relationship property `nonExisting` not found for relationship type 'NEW_REL1'. " +
            "Available properties: ['newRelProp1', 'newRelProp2']"
        );
    }

    @Test
    void shouldLogProgress() {
        var log = new TestLog();

        try (var transactions = newKernelTransaction(db)) {
            var proc = new GraphWriteRelationshipProc();

            proc.procedureTransaction = transactions.tx();
            proc.transaction = transactions.ktx();
            proc.api = db;
            proc.callContext = ProcedureCallContext.EMPTY;
            proc.log = log;
            proc.taskRegistry = EmptyTaskRegistry.INSTANCE;
            proc.relationshipExporterBuilder = new NativeRelationshipExporter.Builder(TransactionContext.of(
                proc.api,
                proc.procedureTransaction
            ));

            proc.run(TEST_GRAPH_NAME, "NEW_REL1", "newRelProp1", Map.of());
        }

        Assertions.assertThat(log.getMessages(TestLog.INFO))
            .extracting(removingThreadId())
            .contains(
                "WriteRelationships :: Start",
                "WriteRelationships 50%",
                "WriteRelationships 100%",
                "WriteRelationships :: Finished"
            );
    }
}
