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

import org.neo4j.gds.core.CypherMapWrapper;
import org.neo4j.gds.ml.linkmodels.pipeline.linkFeatures.linkfunctions.CosineFeatureStep;
import org.neo4j.gds.ml.linkmodels.pipeline.linkFeatures.linkfunctions.HadamardFeatureStep;
import org.neo4j.gds.ml.linkmodels.pipeline.linkFeatures.linkfunctions.L2FeatureStep;
import org.neo4j.gds.ml.linkmodels.pipeline.linkFeatures.linkfunctions.LinkFeatureStepConfigurationImpl;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;
import static org.neo4j.gds.utils.StringFormatting.toUpperCaseWithLocale;

public enum LinkFeatureStepFactory {
    HADAMARD {
        @Override
        public LinkFeatureStep create(CypherMapWrapper config) {
            var typedConfig = new LinkFeatureStepConfigurationImpl(config);
            config.requireOnlyKeysFrom(typedConfig.configKeys());

            return new HadamardFeatureStep(typedConfig.nodeProperties());
        }
    },
    COSINE {
        @Override
        protected LinkFeatureStep create(CypherMapWrapper config) {
            var typedConfig = new LinkFeatureStepConfigurationImpl(config);
            config.requireOnlyKeysFrom(typedConfig.configKeys());

            return new CosineFeatureStep(typedConfig.nodeProperties());
        }
    },
    L2 {
        @Override
        protected LinkFeatureStep create(CypherMapWrapper config) {
            var typedConfig = new LinkFeatureStepConfigurationImpl(config);
            config.requireOnlyKeysFrom(typedConfig.configKeys());

            return new L2FeatureStep(typedConfig.nodeProperties());
        }
    };

    protected abstract LinkFeatureStep create(CypherMapWrapper config);

    public static final List<String> VALUES = Arrays
        .stream(LinkFeatureStepFactory.values())
        .map(LinkFeatureStepFactory::name)
        .collect(Collectors.toList());

    private static LinkFeatureStepFactory parse(String input) {
        var inputString = toUpperCaseWithLocale(input);

        if (VALUES.contains(inputString)) {
            return LinkFeatureStepFactory.valueOf(inputString);
        }

        throw new IllegalArgumentException(formatWithLocale(
            "LinkFeatureStep `%s` is not supported. Must be one of: %s.",
            input,
            VALUES
        ));
    }

    public static LinkFeatureStep create(String taskName, Map<String, Object> config) {
        LinkFeatureStepFactory factory = parse(taskName);
        return factory.create(CypherMapWrapper.create(config));
    }
}
