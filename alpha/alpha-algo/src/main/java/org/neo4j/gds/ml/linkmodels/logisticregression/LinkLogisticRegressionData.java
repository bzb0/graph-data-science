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
package org.neo4j.gds.ml.linkmodels.logisticregression;

import org.immutables.value.Value;
import org.neo4j.gds.annotation.ValueClass;
import org.neo4j.gds.ml.LinkFeatureCombiner;
import org.neo4j.gds.ml.core.features.FeatureExtraction;
import org.neo4j.gds.ml.core.functions.Weights;
import org.neo4j.gds.ml.core.tensor.Matrix;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.core.utils.mem.MemoryEstimation;
import org.neo4j.gds.core.utils.mem.MemoryEstimations;
import org.neo4j.gds.core.utils.mem.MemoryUsage;

import java.util.List;

@ValueClass
public interface LinkLogisticRegressionData {

    static MemoryEstimation memoryEstimation(int numberOfFeatures) {
        return MemoryEstimations.builder("model data")
            .fixed("instance", MemoryUsage.sizeOfInstance(ImmutableLinkLogisticRegressionData.class))
            .fixed("weights", Weights.sizeInBytes(1, numberOfFeatures))
            .build();
    }

    Weights<Matrix> weights();

    LinkFeatureCombiner linkFeatureCombiner();

    @Value.Derived
    default int linkFeatureDimension() {
        return linkFeatureCombiner().linkFeatureDimension(nodeFeatureDimension());
    }

    int nodeFeatureDimension();

    static LinkLogisticRegressionData from(
        Graph graph,
        List<String> featureProperties,
        LinkFeatureCombiner linkFeatureCombiner
    ) {
        var nodeFeatureDimension = FeatureExtraction.featureCount(
            FeatureExtraction.propertyExtractors(graph, featureProperties)
        );
        var numberOfFeatures = linkFeatureCombiner.linkFeatureDimension(nodeFeatureDimension);
        var weights = Weights.ofMatrix(1, numberOfFeatures);

        return builder()
            .weights(weights)
            .linkFeatureCombiner(linkFeatureCombiner)
            .nodeFeatureDimension(nodeFeatureDimension)
            .build();
    }

    static ImmutableLinkLogisticRegressionData.Builder builder() {
        return ImmutableLinkLogisticRegressionData.builder();
    }
}
