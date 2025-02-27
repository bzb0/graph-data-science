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
package org.neo4j.gds;

import org.junit.jupiter.api.DynamicTest;
import org.neo4j.gds.config.WritePropertyConfig;
import org.neo4j.gds.core.CypherMapWrapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.neo4j.gds.ConfigProcTestHelpers.GRAPH_NAME;

public final class WritePropertyConfigProcTest {

    public static List<DynamicTest> test(
        AlgoBaseProc<?, ?, ?> proc,
        CypherMapWrapper config
    ) {
        return List.of(
            unspecifiedWriteProperty(proc, config),
            nullWriteProperty(proc, config),
            whitespaceWriteProperty(proc, config),
            validWriteProperty(proc, config),
            validWriteConcurrency(proc, config)
        );
    }

    private WritePropertyConfigProcTest() {}

    private static DynamicTest unspecifiedWriteProperty(
        AlgoBaseProc<?, ?, ?> proc,
        CypherMapWrapper config
    ) {
        return DynamicTest.dynamicTest("unspecifiedWriteProperty", () -> {
            assertThatThrownBy(() -> proc.newConfig(GRAPH_NAME, config.withoutEntry("writeProperty")))
                .hasMessageContaining("writeProperty")
                .hasMessageContaining("mandatory");
        });
    }

    private static DynamicTest nullWriteProperty(
        AlgoBaseProc<?, ?, ?> proc,
        CypherMapWrapper config
    ) {
        return DynamicTest.dynamicTest("nullWriteProperty", () -> {
            assertThatThrownBy(() -> proc.newConfig(GRAPH_NAME, config.withString("writeProperty", null)))
                .hasMessageContaining("writeProperty")
                .hasMessageContaining("null")
                .hasMessageContaining("type")
                .hasMessageContaining("String");
        });
    }

    private static DynamicTest whitespaceWriteProperty(
        AlgoBaseProc<?, ?, ?> proc,
        CypherMapWrapper config
    ) {
        return DynamicTest.dynamicTest("whitespaceWriteProperty", () -> {
            assertThatThrownBy(() -> proc.newConfig(GRAPH_NAME, config.withString("writeProperty", "  ")))
                .hasMessageContaining("writeProperty")
                .hasMessageContaining("mandatory");
        });
    }

    private static DynamicTest validWriteProperty(
        AlgoBaseProc<?, ?, ?> proc,
        CypherMapWrapper config
    ) {
        return DynamicTest.dynamicTest("validWriteProperty", () -> {
            var wpConfig = config.withString("writeProperty", "w");
            var algoConfig = ((WritePropertyConfig) proc.newConfig(GRAPH_NAME, wpConfig));
            assertThat(algoConfig.writeProperty()).isEqualTo("w");
        });
    }

    private static DynamicTest validWriteConcurrency(
        AlgoBaseProc<?, ?, ?> proc,
        CypherMapWrapper config
    ) {
        return DynamicTest.dynamicTest("validWriteConcurrency", () -> {
            var wpConfig = config.withNumber("writeConcurrency", 3L);
            var algoConfig = ((WritePropertyConfig) proc.newConfig(GRAPH_NAME, wpConfig));
            assertThat(algoConfig.writeConcurrency()).isEqualTo(3);
        });
    }
}
