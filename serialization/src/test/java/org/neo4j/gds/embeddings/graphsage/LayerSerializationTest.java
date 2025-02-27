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
package org.neo4j.gds.embeddings.graphsage;

import org.junit.jupiter.api.Test;
import org.neo4j.gds.embeddings.graphsage.algo.ImmutableGraphSageTrainConfig;
import org.neo4j.gds.core.model.proto.GraphSageProto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LayerSerializationTest {

    @Test
    void shouldWork() throws IOException {
        var config = ImmutableGraphSageTrainConfig.builder()
            .modelName("bogus")
            .addFeatureProperties("a")
            .aggregator(Aggregator.AggregatorType.MEAN)
            .activationFunction(ActivationFunction.SIGMOID)
            .sampleSizes(List.of(25))
            .build();

        var layer = config.layerConfigs(42).stream()
            .map(LayerFactory::createLayer)
            .findFirst()
            .orElseThrow();

        var serializableLayer = LayerSerializer.toSerializable(layer);

        var byteArrayOutputStream = new ByteArrayOutputStream();
        var bytesBeforeWrite = byteArrayOutputStream.toByteArray();
        assertThat(bytesBeforeWrite).isEmpty();
        serializableLayer.writeTo(byteArrayOutputStream);
        assertThat(byteArrayOutputStream.toByteArray()).isNotEmpty();

        var deserializedLayer = LayerSerializer.fromSerializable(GraphSageProto.Layer.parseFrom(byteArrayOutputStream.toByteArray()));

        assertThat(deserializedLayer)
            .isNotNull()
            .usingRecursiveComparison()
            .withStrictTypeChecking()
            .isEqualTo(layer);

    }

}
