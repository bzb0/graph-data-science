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
package org.neo4j.gds.compat.dev;

import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.compat.AbstractInMemoryNodePropertyCursor;
import org.neo4j.storageengine.api.LongReference;
import org.neo4j.storageengine.api.PropertySelection;
import org.neo4j.storageengine.api.Reference;
import org.neo4j.storageengine.api.StorageRelationshipCursor;
import org.neo4j.token.TokenHolders;

public class InMemoryNodePropertyCursor extends AbstractInMemoryNodePropertyCursor {

    public InMemoryNodePropertyCursor(GraphStore graphStore, TokenHolders tokenHolders) {
        super(graphStore, tokenHolders);
    }

    @Override
    public void initNodeProperties(Reference reference, PropertySelection selection, long ownerReference) {
        setId(((LongReference) reference).id);
    }

    @Override
    public void initRelationshipProperties(Reference reference, PropertySelection selection, long ownerReference) {
    }

    @Override
    public void initRelationshipProperties(Reference reference, PropertySelection selection) {
    }

    @Override
    public void initRelationshipProperties(StorageRelationshipCursor relationshipCursor, PropertySelection selection) {
    }
}
