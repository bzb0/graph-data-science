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
package org.neo4j.gds.ml.nodemodels;

import org.junit.jupiter.api.Test;
import org.neo4j.gds.NodeLabel;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.core.utils.paged.HugeLongArray;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.extension.GdlExtension;
import org.neo4j.gds.extension.GdlGraph;
import org.neo4j.gds.extension.Inject;
import org.neo4j.gds.ml.core.batch.BatchTransformer;
import org.neo4j.gds.ml.core.batch.ListBatch;
import org.neo4j.gds.ml.nodemodels.logisticregression.NodeLogisticRegressionData;
import org.neo4j.gds.ml.nodemodels.logisticregression.NodeLogisticRegressionPredictor;
import org.neo4j.gds.nodeproperties.DoubleArrayTestProperties;
import org.neo4j.gds.nodeproperties.DoubleTestProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@GdlExtension
class NodeClassificationPredictConsumerTest {


    @GdlGraph
    private static final String DB_CYPHER = "CREATE (a {class: 0}), (b {class: 1})";

    @Inject
    GraphStore graphStore;

    @Test
    void shouldThrowWhenFeaturePropertiesContainNaN() {
        graphStore.addNodeProperty(
            NodeLabel.ALL_NODES,
            "nan-embedding-1",
            new DoubleArrayTestProperties((long nodeId) -> new double[] {Double.NaN, Double.NaN})
        );
        graphStore.addNodeProperty(
            NodeLabel.ALL_NODES,
            "nan-embedding-2",
            new DoubleArrayTestProperties((long nodeId) -> new double[] {Double.NaN, Double.NaN})
        );
        graphStore.addNodeProperty(
            NodeLabel.ALL_NODES,
            "without-nan",
            new DoubleTestProperties(nodeId -> 4.2)
        );
        Graph graph = graphStore.getUnion();
        var featureProperties = List.of("nan-embedding-1", "nan-embedding-2");
        var data = NodeLogisticRegressionData.from(graph, featureProperties, "class");
        var predictor = new NodeLogisticRegressionPredictor(data, featureProperties);
        var consumer = new NodeClassificationPredictConsumer(
            graph,
            BatchTransformer.IDENTITY,
            predictor,
            null,
            HugeLongArray.of(0, 1),
            featureProperties,
            ProgressTracker.NULL_TRACKER
        );
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> consumer.accept(new ListBatch(List.of(0L, 1L))))
            .withMessage("Node with ID 0 has invalid feature property value NaN. Properties with NaN values: [nan-embedding-1, nan-embedding-2]");
    }

}
