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
package org.neo4j.gds.paths.astar;

import org.neo4j.gds.Algorithm;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.NodeProperties;
import org.neo4j.gds.core.utils.mem.AllocationTracker;
import org.neo4j.gds.core.utils.mem.MemoryEstimation;
import org.neo4j.gds.core.utils.mem.MemoryEstimations;
import org.neo4j.gds.core.utils.paged.HugeLongDoubleMap;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.paths.astar.config.ShortestPathAStarBaseConfig;
import org.neo4j.gds.paths.dijkstra.Dijkstra;
import org.neo4j.gds.paths.dijkstra.DijkstraResult;

import java.util.Optional;

import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;

public final class AStar extends Algorithm<AStar, DijkstraResult> {

    private final Dijkstra dijkstra;

    private AStar(Dijkstra dijkstra) {
        this.dijkstra = dijkstra;
    }

    public static AStar sourceTarget(
        Graph graph,
        ShortestPathAStarBaseConfig config,
        ProgressTracker progressTracker,
        AllocationTracker allocationTracker
    ) {
        var latitudeProperty = config.latitudeProperty();
        var longitudeProperty = config.longitudeProperty();

        if (!graph.availableNodeProperties().contains(latitudeProperty)) {
            throw new IllegalArgumentException(formatWithLocale(
                "The property `%s` has not been loaded",
                latitudeProperty
            ));
        }
        if (!graph.availableNodeProperties().contains(longitudeProperty)) {
            throw new IllegalArgumentException(formatWithLocale(
                "The property `%s` has not been loaded",
                longitudeProperty
            ));
        }

        var latitudeProperties = graph.nodeProperties(latitudeProperty);
        var longitudeProperties = graph.nodeProperties(longitudeProperty);
        var targetNode = graph.toMappedNodeId(config.targetNode());

        var heuristic = new HaversineHeuristic(latitudeProperties, longitudeProperties, targetNode, allocationTracker);

        // Init dijkstra algorithm for computing shortest paths
        var dijkstra = Dijkstra.sourceTarget(graph, config, Optional.of(heuristic), progressTracker, allocationTracker);
        return new AStar(dijkstra);
    }

    public static MemoryEstimation memoryEstimation() {
        return MemoryEstimations.builder(AStar.class)
            .add("Dijkstra", Dijkstra.memoryEstimation(false))
            .add("distanceCache", HugeLongDoubleMap.memoryEstimation())
            .build();
    }

    @Override
    public DijkstraResult compute() {
        return dijkstra.compute();
    }

    @Override
    public AStar me() {
        return this;
    }

    @Override
    public void release() {
        dijkstra.release();
    }

    public static class HaversineHeuristic implements Dijkstra.HeuristicFunction {

        static final double DEFAULT_DISTANCE = Double.NaN;
        // kilometer to nautical mile
        static final double KM_TO_NM = 0.539957;
        static final double EARTH_RADIUS_IN_NM = 6371 * KM_TO_NM;

        private final double targetLatitude;
        private final double targetLongitude;

        private final NodeProperties latitudeProperties;
        private final NodeProperties longitudeProperties;

        private final HugeLongDoubleMap distanceCache;

        HaversineHeuristic(
            NodeProperties latitudeProperties,
            NodeProperties longitudeProperties,
            long targetNode,
            AllocationTracker allocationTracker
        ) {
            this.latitudeProperties = latitudeProperties;
            this.longitudeProperties = longitudeProperties;
            this.targetLatitude = latitudeProperties.doubleValue(targetNode);
            this.targetLongitude = longitudeProperties.doubleValue(targetNode);
            this.distanceCache = new HugeLongDoubleMap(allocationTracker);
        }

        @Override
        public double applyAsDouble(long source) {
            var distance = distanceCache.getOrDefault(source, DEFAULT_DISTANCE);

            if (Double.isNaN(distance)) {
                var sourceLatitude = latitudeProperties.doubleValue(source);
                var sourceLongitude = longitudeProperties.doubleValue(source);
                distance = distance(sourceLatitude, sourceLongitude, targetLatitude, targetLongitude);
                distanceCache.addTo(source, distance);
            }

            return distance;
        }

        // https://rosettacode.org/wiki/Haversine_formula#Java
        public static double distance(
            double sourceLatitude,
            double sourceLongitude,
            double targetLatitude,
            double targetLongitude
        ) {
            var latitudeDistance = Math.toRadians(targetLatitude - sourceLatitude);
            var longitudeDistance = Math.toRadians(targetLongitude - sourceLongitude);
            var lat1 = Math.toRadians(sourceLatitude);
            var lat2 = Math.toRadians(targetLatitude);

            var a = Math.pow(Math.sin(latitudeDistance / 2), 2)
                    + Math.pow(Math.sin(longitudeDistance / 2), 2)
                      * Math.cos(lat1) * Math.cos(lat2);

            var c = 2 * Math.asin(Math.sqrt(a));

            return EARTH_RADIUS_IN_NM * c;
        }
    }
}
