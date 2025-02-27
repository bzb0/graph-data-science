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
package org.neo4j.gds.core.loading.construction;

import com.carrotsearch.hppc.IntObjectHashMap;
import com.carrotsearch.hppc.ObjectIntScatterMap;
import org.apache.commons.lang3.mutable.MutableInt;
import org.immutables.builder.Builder;
import org.immutables.value.Value;
import org.neo4j.gds.NodeLabel;
import org.neo4j.gds.Orientation;
import org.neo4j.gds.RelationshipProjection;
import org.neo4j.gds.RelationshipType;
import org.neo4j.gds.annotation.ValueClass;
import org.neo4j.gds.api.DefaultValue;
import org.neo4j.gds.api.IdMapping;
import org.neo4j.gds.api.NodeMapping;
import org.neo4j.gds.api.NodeProperties;
import org.neo4j.gds.api.Relationships;
import org.neo4j.gds.api.nodeproperties.ValueType;
import org.neo4j.gds.api.schema.GraphSchema;
import org.neo4j.gds.api.schema.NodeSchema;
import org.neo4j.gds.api.schema.RelationshipSchema;
import org.neo4j.gds.core.Aggregation;
import org.neo4j.gds.core.compress.AdjacencyFactory;
import org.neo4j.gds.core.concurrency.Pools;
import org.neo4j.gds.core.huge.HugeGraph;
import org.neo4j.gds.core.loading.AdjacencyBuilder;
import org.neo4j.gds.core.loading.AdjacencyListWithPropertiesBuilder;
import org.neo4j.gds.core.loading.IdMapImplementations;
import org.neo4j.gds.core.loading.IdMappingAllocator;
import org.neo4j.gds.core.loading.ImportSizing;
import org.neo4j.gds.core.loading.InternalBitIdMappingBuilder;
import org.neo4j.gds.core.loading.InternalHugeIdMappingBuilder;
import org.neo4j.gds.core.loading.InternalIdMappingBuilder;
import org.neo4j.gds.core.loading.InternalSequentialBitIdMappingBuilder;
import org.neo4j.gds.core.loading.NodeMappingBuilder;
import org.neo4j.gds.core.loading.RecordsBatchBuffer;
import org.neo4j.gds.core.loading.RelationshipImporter;
import org.neo4j.gds.core.loading.SingleTypeRelationshipImporter;
import org.neo4j.gds.core.loading.nodeproperties.NodePropertiesFromStoreBuilder;
import org.neo4j.gds.core.utils.mem.AllocationTracker;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.IntStream;

import static org.neo4j.gds.core.GraphDimensions.ANY_LABEL;
import static org.neo4j.kernel.api.StatementConstants.NO_SUCH_RELATIONSHIP_TYPE;

@Value.Style(
    builderVisibility = Value.Style.BuilderVisibility.PUBLIC,
    depluralize = true,
    deepImmutablesDetection = true
)
public final class GraphFactory {

    private static final String DUMMY_PROPERTY = "property";

    private GraphFactory() {}

    public static NodesBuilderBuilder initNodesBuilder() {
        return new NodesBuilderBuilder();
    }

    public static NodesBuilderBuilder initNodesBuilder(NodeSchema nodeSchema) {
        return new NodesBuilderBuilder().nodeSchema(nodeSchema);
    }

    @Builder.Factory
    static NodesBuilder nodesBuilder(
        long maxOriginalId,
        Optional<Long> nodeCount,
        Optional<NodeSchema> nodeSchema,
        Optional<Boolean> hasDisjointPartitions,
        Optional<Boolean> hasLabelInformation,
        Optional<Boolean> hasProperties,
        Optional<Integer> concurrency,
        AllocationTracker allocationTracker
    ) {
        boolean useBitIdMap = IdMapImplementations.useBitIdMap();
        boolean disjointPartitions = hasDisjointPartitions.orElse(false);
        boolean labelInformation = nodeSchema
            .map(schema -> !(schema.availableLabels().isEmpty() && schema.containsOnlyAllNodesLabel()))
            .or(() -> hasLabelInformation).orElse(false);
        int threadCount = concurrency.orElse(1);

        NodeMappingBuilder.Capturing nodeMappingBuilder;
        InternalIdMappingBuilder<? extends IdMappingAllocator> internalIdMappingBuilder;

        boolean maxOriginalIdKnown = maxOriginalId != NodesBuilder.UNKNOWN_MAX_ID;
        if (useBitIdMap && disjointPartitions && maxOriginalIdKnown) {
            var idMappingBuilder = InternalBitIdMappingBuilder.of(maxOriginalId + 1, allocationTracker);
            nodeMappingBuilder = IdMapImplementations.bitIdMapBuilder(idMappingBuilder);
            internalIdMappingBuilder = idMappingBuilder;
        } else if (useBitIdMap && !labelInformation && maxOriginalIdKnown) {
            var idMappingBuilder = InternalSequentialBitIdMappingBuilder.of(maxOriginalId + 1, allocationTracker);
            nodeMappingBuilder = IdMapImplementations.sequentialBitIdMapBuilder(idMappingBuilder);
            internalIdMappingBuilder = idMappingBuilder;
        } else {
            long capacity = maxOriginalIdKnown
                ? maxOriginalId + 1
                : nodeCount.orElseThrow(() -> new IllegalArgumentException("Either `maxOriginalId` or `nodeCount` must be set"));
            var idMappingBuilder = InternalHugeIdMappingBuilder.of(capacity, allocationTracker);
            nodeMappingBuilder = IdMapImplementations.hugeIdMapBuilder(idMappingBuilder);
            internalIdMappingBuilder = idMappingBuilder;
        }

        return nodeSchema.map(schema -> fromSchema(
            maxOriginalId,
            nodeCount.orElseThrow(() -> new IllegalArgumentException("Required parameter [nodeCount] is missing")),
            nodeMappingBuilder,
            internalIdMappingBuilder,
            threadCount,
            schema,
            labelInformation,
            allocationTracker
        )).orElseGet(() -> {
            boolean nodeProperties = hasProperties.orElse(false);
            long nodes = nodeCount.orElse(-1L);

            if (nodeProperties && nodes <= 0) {
                throw new IllegalArgumentException("NodesBuilder with properties requires a node count greater than 0");
            }

            return new NodesBuilder(
                maxOriginalId,
                nodes,
                threadCount,
                new ObjectIntScatterMap<>(),
                new IntObjectHashMap<>(),
                new IntObjectHashMap<>(),
                nodeMappingBuilder,
                internalIdMappingBuilder,
                labelInformation,
                nodeProperties,
                allocationTracker
            );
        });
    }

    private static NodesBuilder fromSchema(
        long maxOriginalId,
        long nodeCount,
        NodeMappingBuilder.Capturing nodeMappingBuilder,
        InternalIdMappingBuilder<? extends IdMappingAllocator> internalIdMappingBuilder,
        int concurrency,
        NodeSchema nodeSchema,
        boolean hasLabelInformation,
        AllocationTracker allocationTracker
    ) {
        var nodeLabels = nodeSchema.availableLabels();

        var elementIdentifierLabelTokenMapping = new ObjectIntScatterMap<NodeLabel>();
        var labelTokenNodeLabelMapping = new IntObjectHashMap<List<NodeLabel>>();
        var builderByLabelTokenAndPropertyToken = new IntObjectHashMap<Map<String, NodePropertiesFromStoreBuilder>>();

        var labelTokenCounter = new MutableInt(0);
        nodeLabels.forEach(nodeLabel -> {
            int labelToken = nodeLabel == NodeLabel.ALL_NODES
                ? ANY_LABEL
                : labelTokenCounter.getAndIncrement();

            elementIdentifierLabelTokenMapping.put(nodeLabel, labelToken);
            labelTokenNodeLabelMapping.put(labelToken, List.of(nodeLabel));
            builderByLabelTokenAndPropertyToken.put(labelToken, new HashMap<>());

            nodeSchema.properties().get(nodeLabel).forEach((propertyKey, propertySchema) ->
                builderByLabelTokenAndPropertyToken.get(labelToken).put(
                    propertyKey,
                    NodePropertiesFromStoreBuilder.of(nodeCount, allocationTracker, propertySchema.defaultValue())
                ));
        });

        return new NodesBuilder(
            maxOriginalId,
            nodeCount,
            concurrency,
            elementIdentifierLabelTokenMapping,
            labelTokenNodeLabelMapping,
            builderByLabelTokenAndPropertyToken,
            nodeMappingBuilder,
            internalIdMappingBuilder,
            hasLabelInformation,
            nodeSchema.hasProperties(),
            allocationTracker
        );
    }

    @ValueClass
    public interface PropertyConfig {

        @Value.Default
        default Aggregation aggregation() {
            return Aggregation.NONE;
        }

        @Value.Default
        default DefaultValue defaultValue() {
            return DefaultValue.forDouble();
        }

        static PropertyConfig withDefaults() {
            return of(Optional.empty(), Optional.empty());
        }

        static PropertyConfig of(Aggregation aggregation, DefaultValue defaultValue) {
            return ImmutablePropertyConfig.of(aggregation, defaultValue);
        }

        static PropertyConfig of(Optional<Aggregation> aggregation, Optional<DefaultValue> defaultValue) {
            return of(aggregation.orElse(Aggregation.NONE), defaultValue.orElse(DefaultValue.forDouble()));
        }
    }

    public static RelationshipsBuilderBuilder initRelationshipsBuilder() {
        return new RelationshipsBuilderBuilder();
    }

    @Builder.Factory
    static RelationshipsBuilder relationshipsBuilder(
        IdMapping nodes,
        Optional<Orientation> orientation,
        List<PropertyConfig> propertyConfigs,
        Optional<Aggregation> aggregation,
        Optional<Boolean> preAggregate,
        Optional<Integer> concurrency,
        Optional<ExecutorService> executorService,
        AllocationTracker allocationTracker
    ) {
        var loadRelationshipProperties = !propertyConfigs.isEmpty();

        var aggregations = propertyConfigs.isEmpty()
            ? new Aggregation[]{aggregation.orElse(Aggregation.NONE)}
            : propertyConfigs.stream()
                .map(GraphFactory.PropertyConfig::aggregation)
                .map(Aggregation::resolve)
                .toArray(Aggregation[]::new);

        var relationshipType = RelationshipType.ALL_RELATIONSHIPS;
        var isMultiGraph = Arrays.stream(aggregations).allMatch(Aggregation::equivalentToNone);

        var projectionBuilder = RelationshipProjection
            .builder()
            .type(relationshipType.name())
            .orientation(orientation.orElse(Orientation.NATURAL));

        propertyConfigs.forEach(propertyConfig -> projectionBuilder.addProperty(
            GraphFactory.DUMMY_PROPERTY,
            GraphFactory.DUMMY_PROPERTY,
            DefaultValue.of(propertyConfig.defaultValue()),
            propertyConfig.aggregation()
        ));

        var projection = projectionBuilder.build();

        int[] propertyKeyIds = IntStream.range(0, propertyConfigs.size()).toArray();
        double[] defaultValues = propertyConfigs.stream().mapToDouble(c -> c.defaultValue().doubleValue()).toArray();

        var importSizing = ImportSizing.of(concurrency.orElse(1), nodes.rootNodeCount());
        int pageSize = importSizing.pageSize();
        int numberOfPages = importSizing.numberOfPages();
        int bufferSize = (int) Math.min(nodes.rootNodeCount(), RecordsBatchBuffer.DEFAULT_BUFFER_SIZE);

        var relationshipCounter = new LongAdder();

        var adjacencyListWithPropertiesBuilder = AdjacencyListWithPropertiesBuilder.create(
            nodes.rootNodeCount(),
            AdjacencyFactory.configured(),
            projection,
            aggregations,
            propertyKeyIds,
            defaultValues,
            allocationTracker
        );

        AdjacencyBuilder adjacencyBuilder = AdjacencyBuilder.compressing(
            adjacencyListWithPropertiesBuilder,
            numberOfPages,
            pageSize,
            allocationTracker,
            relationshipCounter,
            preAggregate.orElse(false)
        );

        var relationshipImporter = new RelationshipImporter(allocationTracker, adjacencyBuilder);

        var importerBuilder = new SingleTypeRelationshipImporter.Builder(
            relationshipType,
            projection,
            loadRelationshipProperties,
            NO_SUCH_RELATIONSHIP_TYPE,
            relationshipImporter,
            relationshipCounter,
            false
        ).loadImporter(loadRelationshipProperties);

        return new RelationshipsBuilder(
            nodes,
            orientation.orElse(Orientation.NATURAL),
            bufferSize,
            propertyKeyIds,
            adjacencyListWithPropertiesBuilder,
            importerBuilder,
            relationshipCounter,
            loadRelationshipProperties,
            isMultiGraph,
            concurrency.orElse(1),
            executorService.orElse(Pools.DEFAULT)
        );
    }

    public static Relationships emptyRelationships(IdMapping nodeMapping, AllocationTracker allocationTracker) {
        return initRelationshipsBuilder().nodes(nodeMapping).allocationTracker(allocationTracker).build().build();
    }

    public static HugeGraph create(NodeMapping idMap, Relationships relationships, AllocationTracker allocationTracker) {
        var nodeSchemaBuilder = NodeSchema.builder();
        idMap.availableNodeLabels().forEach(nodeSchemaBuilder::addLabel);
        return create(
            idMap,
            nodeSchemaBuilder.build(),
            Collections.emptyMap(),
            RelationshipType.of("REL"),
            relationships,
            allocationTracker
        );
    }

    public static HugeGraph create(
        NodeMapping idMap,
        NodeSchema nodeSchema,
        Map<String, NodeProperties> nodeProperties,
        RelationshipType relationshipType,
        Relationships relationships,
        AllocationTracker allocationTracker
    ) {
        var relationshipSchemaBuilder = RelationshipSchema.builder();
        if (relationships.properties().isPresent()) {
            relationshipSchemaBuilder.addProperty(
                relationshipType,
                "property",
                ValueType.DOUBLE
            );
        } else {
            relationshipSchemaBuilder.addRelationshipType(relationshipType);
        }
        return HugeGraph.create(
            idMap,
            GraphSchema.of(nodeSchema, relationshipSchemaBuilder.build()),
            nodeProperties,
            relationships.topology(),
            relationships.properties(),
            allocationTracker
        );
    }
}
