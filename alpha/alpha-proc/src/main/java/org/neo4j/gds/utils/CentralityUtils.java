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
package org.neo4j.gds.utils;

import org.neo4j.gds.result.CentralityResult;
import org.neo4j.gds.results.CentralityScore;
import org.neo4j.gds.api.Graph;

import java.util.stream.LongStream;
import java.util.stream.Stream;

public final class CentralityUtils {

    private CentralityUtils() {}

    public static Stream<CentralityScore> streamResults(Graph graph, CentralityResult scores) {
            return LongStream.range(0, graph.nodeCount())
                    .mapToObj(i -> {
                        final long nodeId = graph.toOriginalNodeId(i);
                        return new CentralityScore(
                                nodeId,
                                scores.score(i)
                        );
                    });
    }
}
