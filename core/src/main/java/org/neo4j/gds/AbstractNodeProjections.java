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
package org.neo4j.gds;

import org.apache.commons.lang3.StringUtils;
import org.immutables.value.Value;
import org.jetbrains.annotations.Nullable;
import org.neo4j.gds.annotation.DataClass;

import java.util.LinkedHashMap;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static java.util.Collections.unmodifiableMap;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static org.neo4j.gds.compat.MapUtil.genericMap;
import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;
import static org.neo4j.gds.NodeLabel.ALL_NODES;

@DataClass
@Value.Immutable(singleton = true)
public abstract class AbstractNodeProjections extends AbstractProjections<NodeLabel, NodeProjection> {

    public static final NodeProjections ALL = create(singletonMap(ALL_NODES, NodeProjection.all()));

    public abstract Map<NodeLabel, NodeProjection> projections();

    public static NodeProjections fromObject(Object object) {
        if (object == null) {
            return fromMap(emptyMap());
        }
        if (object instanceof NodeProjections) {
            return (NodeProjections) object;
        }
        if (object instanceof String) {
            return fromString((String) object);
        }
        if (object instanceof Map) {
            @SuppressWarnings("unchecked") Map<String, ?> map = (Map) object;
            return fromMap(map);
        }
        if (object instanceof Iterable) {
            Iterable<?> list = (Iterable) object;
            return fromList(list);
        }
        throw new IllegalArgumentException(formatWithLocale(
            "Cannot construct a node projection out of a %s",
            object.getClass().getName()
        ));
    }

    public static NodeProjections fromString(@Nullable String labelString) {
        validateIdentifierName(labelString);

        if (StringUtils.isEmpty(labelString)) {
            create(emptyMap());
        }
        if (labelString.equals(ElementProjection.PROJECT_ALL)) {
            return create(singletonMap(ALL_NODES, NodeProjection.all()));
        }

        NodeLabel nodeLabel = new NodeLabel(labelString);
        NodeProjection projection = NodeProjection.fromString(labelString);
        return create(singletonMap(nodeLabel, projection));
    }

    private static NodeProjections fromMap(Map<String, ?> map) {
        Map<NodeLabel, NodeProjection> projections = new LinkedHashMap<>();
        map.forEach((name, spec) -> {
            NodeLabel nodeLabel = new NodeLabel(name);
            NodeProjection projection = NodeProjection.fromObject(spec, nodeLabel);
            // sanity
            if (projections.put(nodeLabel, projection) != null) {
                throw new IllegalStateException(formatWithLocale("Duplicate key: %s", name));
            }
        });
        return create(projections);
    }

    private static NodeProjections fromList(Iterable<?> items) {
        Map<NodeLabel, NodeProjection> projections = new LinkedHashMap<>();
        for (Object item : items) {
            NodeProjections nodeProjections = fromObject(item);
            projections.putAll(nodeProjections.projections());
        }
        return create(projections);
    }

    public static NodeProjections create(Map<NodeLabel, NodeProjection> projections) {
        if (projections.isEmpty()) {
            throw new IllegalArgumentException(
                "An empty node projection was given; at least one node label must be projected."
            );
        }
        return NodeProjections.of(unmodifiableMap(projections));
    }

    public static NodeProjections single(NodeLabel label, NodeProjection projection) {
        return NodeProjections.of(genericMap(label, projection));
    }

    public static NodeProjections all() {
        return ALL;
    }

    public NodeProjections addPropertyMappings(PropertyMappings mappings) {
        if (!mappings.hasMappings()) {
            return NodeProjections.copyOf(this);
        }
        Map<NodeLabel, NodeProjection> newProjections = projections().entrySet().stream().collect(toMap(
            Map.Entry::getKey,
            e -> e.getValue().withAdditionalPropertyMappings(mappings)
        ));
        if (newProjections.isEmpty()) {
            newProjections.put(ALL_NODES, NodeProjection.all().withAdditionalPropertyMappings(mappings));
        }
        return create(newProjections);
    }

    public String labelProjection() {
        if (isEmpty()) {
            return "";
        }
        return projections()
            .values()
            .stream()
            .map(NodeProjection::label)
            .collect(joining(", ")
        );
    }

    public boolean isEmpty() {
        return this == NodeProjections.of();
    }

    public Map<String, Object> toObject() {
        Map<String, Object> value = new LinkedHashMap<>();
        projections().forEach((identifier, projection) -> {
            value.put(identifier.name, projection.toObject());
        });
        return value;
    }

    private static void validateIdentifierName(String identifier) {
        if (identifier.equals(ALL_NODES.name())) {
            throw new IllegalArgumentException(formatWithLocale(
                "%s is a reserved node label and may not be used",
                ALL_NODES.name()
            ));
        }
    }
}
