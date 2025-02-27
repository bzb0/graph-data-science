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
package org.neo4j.gds.ml.linkmodels.pipeline.linkFeatures.linkfunctions;

import org.junit.jupiter.api.Test;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.ml.linkmodels.pipeline.linkFeatures.LinkFeatureExtractor;
import org.neo4j.gds.ml.linkmodels.pipeline.linkFeatures.LinkFeatureStepFactory;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

final class CosineLinkFeatureStepTest extends FeatureStepBaseTest {

    @Test
    public void runCosineLinkFeatureStep() {

        var step = LinkFeatureStepFactory.create(
            "cosine",
            Map.of("nodeProperties", List.of("noise", "z", "array"))
        );

        var linkFeatures = LinkFeatureExtractor.extractFeatures(graph, List.of(step), 4, ProgressTracker.NULL_TRACKER);

        var delta = 0.0001D;

        var norm0 = Math.sqrt(42 * 42 + 13 * 13 + 3 * 3 + 2 * 2);
        var norm1 = Math.sqrt(1337 * 1337 + 0 * 0 + 1 * 1 + 1 * 1);
        var norm2 = Math.sqrt(42 * 42 + 2 * 2 + 8 * 8 + 2.3 * 2.3);
        var norm3 = Math.sqrt(42 * 42 + 9 * 9 + 0.1 * 0.1 + 91 * 91);

        assertThat(linkFeatures.get(0)[0])
            .isEqualTo((42 * 1337 + 13 * 0D + 3 * 1D + 2 * 1D) / norm0 / norm1, offset(delta));
        assertThat(linkFeatures.get(1)[0])
            .isEqualTo((42 * 42 + 13 * 2 + 3 * 8 + 2 * 2.3D) / norm0 / norm2, offset(delta));
        assertThat(linkFeatures.get(2)[0])
            .isEqualTo((42 * 42 + 13 * 9 + 3 * 0.1D + 2 * 91.0D) / norm0 / norm3, offset(delta));
    }

    @Test
    public void handlesZeroVectors() {
        var step = LinkFeatureStepFactory.create(
            "cosine",
            Map.of("nodeProperties", List.of("zeros"))
        );

        var linkFeatures = LinkFeatureExtractor.extractFeatures(graph, List.of(step), 4, ProgressTracker.NULL_TRACKER);

        for (long i = 0; i < linkFeatures.size(); i++) {
            assertThat(linkFeatures.get(i)).hasSize(1).containsExactly(0.0);
        }
    }

    @Test
    public void failsOnNaNValues() {
        var step = LinkFeatureStepFactory.create(
            "cosine",
            Map.of("nodeProperties", List.of("invalidValue", "z"))
        );

        assertThatThrownBy(() -> LinkFeatureExtractor.extractFeatures(graph, List.of(step), 4, ProgressTracker.NULL_TRACKER))
            .hasMessage("Encountered NaN in the nodeProperty `invalidValue` for nodes ['1'] when computing the cosine feature vector. " +
                        "Either define a default value if its a stored property or check the nodePropertyStep");
    }
}
