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
import org.neo4j.gds.core.CypherMapWrapper;
import org.neo4j.gds.config.IterationsConfig;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.neo4j.gds.ConfigProcTestHelpers.GRAPH_NAME;

public final class IterationsConfigProcTest {

    public static List<DynamicTest> test(
        AlgoBaseProc<?, ?, ?> proc,
        CypherMapWrapper config
    ) {
        return List.of(
            invalidMaxIterations(
                proc,
                config
            ),
            validMaxIterations(
                proc,
                config
            )
        );
    }

    private IterationsConfigProcTest() {}

    private static DynamicTest invalidMaxIterations(
        AlgoBaseProc<?, ?, ?> proc,
        CypherMapWrapper config
    ) {
        return DynamicTest.dynamicTest("invalidMaxIterations", () -> {
            assertThatThrownBy(() -> proc
                .newConfig(GRAPH_NAME, config.withNumber("maxIterations", 0)))
                .hasMessageContaining("maxIterations")
                .hasMessageContaining("0");
            assertThatThrownBy(() -> proc
                .newConfig(GRAPH_NAME, config.withNumber("maxIterations", -10)))
                .hasMessageContaining("maxIterations")
                .hasMessageContaining("-10");
        });
    }

    private static DynamicTest validMaxIterations(
        AlgoBaseProc<?, ?, ?> proc,
        CypherMapWrapper config
    ) {
        return DynamicTest.dynamicTest("validMaxIterations", () -> {
            var iterationsConfig = config.withNumber("maxIterations", 3);
            var algoConfig = ((IterationsConfig) proc.newConfig(GRAPH_NAME, iterationsConfig));
            assertThat(algoConfig.maxIterations()).isEqualTo(3);
        });
    }
}
