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
package org.neo4j.gds.doc;

import org.neo4j.gds.betweenness.BetweennessCentralityMutateProc;
import org.neo4j.gds.betweenness.BetweennessCentralityStatsProc;
import org.neo4j.gds.betweenness.BetweennessCentralityStreamProc;
import org.neo4j.gds.betweenness.BetweennessCentralityWriteProc;
import org.neo4j.gds.catalog.GraphCreateProc;

import java.util.Arrays;
import java.util.List;

class BetweennessCentralityDocTest extends DocTestBase {

    @Override
    List<Class<?>> procedures() {
        return Arrays.asList(
            BetweennessCentralityStreamProc.class,
            BetweennessCentralityWriteProc.class,
            BetweennessCentralityMutateProc.class,
            BetweennessCentralityStatsProc.class,
            GraphCreateProc.class
        );
    }

    @Override
    String adocFile() {
        return "algorithms/betweenness-centrality/betweenness-centrality.adoc";
    }
}
