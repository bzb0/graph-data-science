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
package org.neo4j.gds.core.utils.io.file;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.gds.core.utils.io.file.csv.CsvRelationshipSchemaVisitorTest;
import org.neo4j.gds.RelationshipType;
import org.neo4j.gds.api.DefaultValue;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.api.nodeproperties.ValueType;
import org.neo4j.gds.api.schema.RelationshipPropertySchema;
import org.neo4j.gds.core.Aggregation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.neo4j.gds.core.utils.io.file.csv.CsvRelationshipSchemaVisitor.RELATIONSHIP_SCHEMA_FILE_NAME;

class RelationshipSchemaLoaderTest {

    @TempDir Path exportDir;

    @Test
    void shouldLoadRelationshipSchemaCorrectly() throws IOException {
        var lines = List.of(
            String.join(", ", CsvRelationshipSchemaVisitorTest.RELATIONSHIP_SCHEMA_COLUMNS),
            "REL1, prop1, long, DefaultValue(42), SUM, PERSISTENT",
            "REL2, prop2, double, DefaultValue(13.37), COUNT, TRANSIENT"
        );
        FileUtils.writeLines(exportDir.resolve(RELATIONSHIP_SCHEMA_FILE_NAME).toFile(), lines);

        var schemaLoader = new RelationshipSchemaLoader(exportDir);
        var loadedRelationshipSchema = schemaLoader.load();

        assertThat(loadedRelationshipSchema.availableTypes()).containsExactlyInAnyOrder(RelationshipType.of("REL1"), RelationshipType.of("REL2"));

        var rel1Properties = loadedRelationshipSchema.filterProperties(Set.of(RelationshipType.of("REL1")));
        assertThat(rel1Properties)
            .containsExactlyInAnyOrderEntriesOf(Map.of(
                RelationshipType.of("REL1"),
                Map.of(
                    "prop1",
                    RelationshipPropertySchema.of(
                        "prop1",
                        ValueType.LONG,
                        DefaultValue.of(42L),
                        GraphStore.PropertyState.PERSISTENT,
                        Aggregation.SUM
                    )
                )
            ));

        var rel2Properties = loadedRelationshipSchema.filterProperties(Set.of(RelationshipType.of("REL2")));
        assertThat(rel2Properties)
            .containsExactlyInAnyOrderEntriesOf(Map.of(
                RelationshipType.of("REL2"),
                Map.of(
                    "prop2",
                    RelationshipPropertySchema.of(
                        "prop2",
                        ValueType.DOUBLE,
                        DefaultValue.of(13.37D),
                        GraphStore.PropertyState.TRANSIENT,
                        Aggregation.COUNT
                    )
                )
            ));
    }

    @Test
    void shouldLoadSchemaWithoutProperties() throws IOException {
        var lines = List.of(
            String.join(", ", CsvRelationshipSchemaVisitorTest.RELATIONSHIP_SCHEMA_COLUMNS),
            "A",
            "B"
        );
        FileUtils.writeLines(exportDir.resolve(RELATIONSHIP_SCHEMA_FILE_NAME).toFile(), lines);

        var schemaLoader = new RelationshipSchemaLoader(exportDir);
        var relationshipSchema = schemaLoader.load();

        assertThat(relationshipSchema).isNotNull();
        assertThat(relationshipSchema.availableTypes()).containsExactlyInAnyOrder(RelationshipType.of("A"), RelationshipType.of("B"));
    }

    @Test
    void shouldLoadMixedRelationshipSchema() throws IOException {
        var lines = List.of(
            String.join(", ", CsvRelationshipSchemaVisitorTest.RELATIONSHIP_SCHEMA_COLUMNS),
            "REL1, prop1, long, DefaultValue(42), SUM, PERSISTENT",
            "REL3"
        );
        FileUtils.writeLines(exportDir.resolve(RELATIONSHIP_SCHEMA_FILE_NAME).toFile(), lines);

        var schemaLoader = new RelationshipSchemaLoader(exportDir);
        var loadedRelationshipSchema = schemaLoader.load();

        assertThat(loadedRelationshipSchema.availableTypes()).containsExactlyInAnyOrder(RelationshipType.of("REL1"), RelationshipType.of("REL3"));

        var rel1Properties = loadedRelationshipSchema.filterProperties(Set.of(RelationshipType.of("REL1")));
        assertThat(rel1Properties)
            .containsExactlyInAnyOrderEntriesOf(Map.of(
                RelationshipType.of("REL1"),
                Map.of(
                    "prop1",
                    RelationshipPropertySchema.of(
                        "prop1",
                        ValueType.LONG,
                        DefaultValue.of(42L),
                        GraphStore.PropertyState.PERSISTENT,
                        Aggregation.SUM
                    )
                )
            ));

        var rel3Properties = loadedRelationshipSchema.filterProperties(Set.of(RelationshipType.of("REL3")));
        assertThat(rel3Properties)
            .containsExactlyInAnyOrderEntriesOf(Map.of(
                RelationshipType.of("REL3"),
                Map.of()
            ));
    }
}
