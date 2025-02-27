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
package org.neo4j.gds.core.loading;

import com.carrotsearch.hppc.LongHashSet;
import com.carrotsearch.hppc.LongSet;
import org.immutables.builder.Builder;
import org.neo4j.gds.compat.Neo4jProxy;
import org.neo4j.gds.compat.PropertyReference;
import org.neo4j.token.api.TokenConstants;

import java.util.Optional;

import static org.neo4j.gds.core.GraphDimensions.IGNORE;
import static org.neo4j.gds.utils.GdsFeatureToggles.SKIP_ORPHANS;
import static org.neo4j.kernel.impl.store.record.AbstractBaseRecord.NO_ID;

public final class NodesBatchBuffer extends RecordsBatchBuffer<NodeReference> {

    private final LongSet nodeLabelIds;
    private final boolean hasLabelInformation;
    private final long[][] labelIds;
    private final boolean skipOrphans;

    // property ids, consecutive
    private final PropertyReference[] properties;

    @Builder.Constructor
    NodesBatchBuffer(
        int capacity,
        Optional<LongSet> nodeLabelIds,
        Optional<Boolean> hasLabelInformation,
        Optional<Boolean> readProperty
    ) {
        super(capacity);
        this.nodeLabelIds = nodeLabelIds.orElseGet(LongHashSet::new);
        this.hasLabelInformation = hasLabelInformation.orElse(false);
        this.properties = readProperty.orElse(false) ? new PropertyReference[capacity] : null;
        this.labelIds = new long[capacity][];
        this.skipOrphans = SKIP_ORPHANS.isEnabled();
    }

    @Override
    public void offer(final NodeReference record) {
        if (skipOrphans && record.relationshipReference() == NO_ID) {
            return;
        }
        if (nodeLabelIds.isEmpty()) {
            var propertiesReference = properties == null ? Neo4jProxy.noPropertyReference() : record.propertiesReference();
            add(record.nodeId(), propertiesReference, new long[]{TokenConstants.ANY_LABEL});
        } else {
            boolean atLeastOneLabelFound = false;
            var labels = record.labels();
            for (int i = 0; i < labels.length; i++) {
                long l = labels[i];
                if (!nodeLabelIds.contains(l) && !nodeLabelIds.contains(TokenConstants.ANY_LABEL)) {
                    labels[i] = IGNORE;
                } else {
                    atLeastOneLabelFound = true;
                }
            }
            if (atLeastOneLabelFound) {
                var propertiesReference = properties == null ? Neo4jProxy.noPropertyReference() : record.propertiesReference();
                add(record.nodeId(), propertiesReference, labels);
            }
        }
    }

    public void add(long nodeId, PropertyReference propertyReference, long[] labels) {
        int len = length++;
        buffer[len] = nodeId;
        if (properties != null) {
            properties[len] = propertyReference;
        }
        if (labelIds != null) {
            labelIds[len] = labels;
        }
    }

    public PropertyReference[] properties() {
        return this.properties;
    }

    public boolean hasLabelInformation() {
        return hasLabelInformation;
    }

    public long[][] labelIds() {
        return this.labelIds;
    }
}
