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
package org.neo4j.gds.ml.linkmodels.pipeline.linkFeatures;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.gds.ml.linkmodels.pipeline.linkFeatures.linkfunctions.CosineFeatureStep;
import org.neo4j.gds.ml.linkmodels.pipeline.linkFeatures.linkfunctions.HadamardFeatureStep;
import org.neo4j.gds.ml.linkmodels.pipeline.linkFeatures.linkfunctions.L2FeatureStep;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.neo4j.gds.ml.linkmodels.pipeline.linkFeatures.LinkFeatureStep.INPUT_NODE_PROPERTIES;

final class LinkFeatureStepFactoryTest {

    @Test
    public void testCreateHadamard() {
        List<String> nodeProperties = List.of("noise", "z", "array");
        var step = LinkFeatureStepFactory.create(
            "hadaMard",
            Map.of("nodeProperties", nodeProperties)
        );

        assertThat(step).isInstanceOf(HadamardFeatureStep.class);
        assertThat(step.inputNodeProperties()).isEqualTo(nodeProperties);
    }

    @Test
    public void testCreateCosine() {
        List<String> featureProperties = List.of("noise", "z", "array");
        var step = LinkFeatureStepFactory.create(
            "coSine",
            Map.of("nodeProperties", featureProperties)
        );

        assertThat(step).isInstanceOf(CosineFeatureStep.class);
        assertThat(step.inputNodeProperties()).isEqualTo(featureProperties);
    }

    @Test
    public void testCreateL2() {
        List<String> featureProperties = List.of("noise", "z", "array");
        var step = LinkFeatureStepFactory.create(
            "L2",
            Map.of("nodeProperties", featureProperties)
        );

        assertThat(step).isInstanceOf(L2FeatureStep.class);
        assertThat(step.inputNodeProperties()).isEqualTo(featureProperties);
    }

    @ParameterizedTest
    @MethodSource("org.neo4j.gds.ml.linkmodels.pipeline.linkFeatures.LinkFeatureStepFactory#values")
    public void shouldFailOnMissingFeatureProperties(LinkFeatureStepFactory factory) {
        assertThatThrownBy(() -> LinkFeatureStepFactory.create(factory.name(), Map.of()))
            .isExactlyInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No value specified for the mandatory configuration parameter `nodeProperties`");
    }

    @ParameterizedTest
    @MethodSource("org.neo4j.gds.ml.linkmodels.pipeline.linkFeatures.LinkFeatureStepFactory#values")
    public void shouldFailOnEmptyFeatureProperties(LinkFeatureStepFactory factory) {
        assertThatThrownBy(() -> LinkFeatureStepFactory.create(factory.name(), Map.of(INPUT_NODE_PROPERTIES, List.of())))
            .isExactlyInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("`nodeProperties` must be non-empty.");
    }

    @ParameterizedTest
    @MethodSource("org.neo4j.gds.ml.linkmodels.pipeline.linkFeatures.LinkFeatureStepFactory#values")
    public void shouldFailOnNotListFeatureProperties(LinkFeatureStepFactory factory) {
        assertThatThrownBy(() -> LinkFeatureStepFactory.create(factory.name(), Map.of(INPUT_NODE_PROPERTIES, Map.of())))
            .isExactlyInstanceOf(IllegalArgumentException.class)
            .hasMessage("The value of `nodeProperties` must be of type `List` but was `MapN`.");
    }

    @ParameterizedTest
    @MethodSource("org.neo4j.gds.ml.linkmodels.pipeline.linkFeatures.LinkFeatureStepFactory#values")
    public void shouldFailOnUnexpectedConfigurationKey(LinkFeatureStepFactory factory) {
        assertThatThrownBy(() -> LinkFeatureStepFactory.create(factory.name(), Map.of(INPUT_NODE_PROPERTIES,  List.of("noise", "z", "array"), "otherThing", 1)))
            .isExactlyInstanceOf(IllegalArgumentException.class)
            .hasMessage("Unexpected configuration key: otherThing");
    }

    @ParameterizedTest
    @MethodSource("org.neo4j.gds.ml.linkmodels.pipeline.linkFeatures.LinkFeatureStepFactory#values")
    public void shouldFailOnListOfNonStringsFeatureProperties(LinkFeatureStepFactory factory) {
        assertThatThrownBy(() -> LinkFeatureStepFactory.create(factory.name(), Map.of(INPUT_NODE_PROPERTIES, List.of("foo", List.of(), "  "))))
            .isExactlyInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid property names defined in `nodeProperties`: ['  ', '[]']. Expecting a String with at least one non-white space character.");
    }
}
