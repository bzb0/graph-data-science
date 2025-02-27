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
package org.neo4j.internal.recordstorage;

import org.neo4j.counts.CountsAccessor;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.compat.dev.InMemoryNodeCursor;
import org.neo4j.gds.compat.dev.InMemoryPropertyCursor;
import org.neo4j.gds.compat.dev.InMemoryRelationshipTraversalCursor;
import org.neo4j.internal.schema.IndexDescriptor;
import org.neo4j.internal.schema.IndexType;
import org.neo4j.internal.schema.SchemaDescriptor;
import org.neo4j.io.pagecache.context.CursorContext;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.storageengine.api.StorageNodeCursor;
import org.neo4j.storageengine.api.StoragePropertyCursor;
import org.neo4j.storageengine.api.StorageRelationshipScanCursor;
import org.neo4j.storageengine.api.StorageRelationshipTraversalCursor;
import org.neo4j.storageengine.api.cursor.StoreCursors;
import org.neo4j.token.TokenHolders;

public class InMemoryStorageReaderDev extends AbstractInMemoryStorageReader {

    public InMemoryStorageReaderDev(
        GraphStore graphStore,
        TokenHolders tokenHolders,
        CountsAccessor counts
    ) {
        super(graphStore, tokenHolders, counts);
    }

    @Override
    public long relationshipsGetCount(CursorContext cursorTracer) {
        return graphStore.relationshipCount();
    }

    @Override
    public boolean nodeExists(long id, StoreCursors storeCursors) {
        return super.nodeExists(id);
    }

    @Override
    public boolean relationshipExists(long id, StoreCursors storeCursors) {
        return true;
    }

    @Override
    public StorageNodeCursor allocateNodeCursor(
        CursorContext cursorContext, StoreCursors storeCursors
    ) {
        return new InMemoryNodeCursor(graphStore, tokenHolders);
    }

    @Override
    public StoragePropertyCursor allocatePropertyCursor(
        CursorContext cursorContext, StoreCursors storeCursors, MemoryTracker memoryTracker
    ) {
        return new InMemoryPropertyCursor(graphStore, tokenHolders);
    }

    @Override
    public StorageRelationshipTraversalCursor allocateRelationshipTraversalCursor(
        CursorContext cursorContext, StoreCursors storeCursors
    ) {
        return new InMemoryRelationshipTraversalCursor(graphStore, tokenHolders);
    }

    @Override
    public StorageRelationshipScanCursor allocateRelationshipScanCursor(
        CursorContext cursorContext, StoreCursors storeCursors
    ) {
        return new RecordRelationshipScanCursor(null, cursorContext);
    }

    @Override
    public IndexDescriptor indexGetForSchemaAndType(
        SchemaDescriptor descriptor, IndexType type
    ) {
        return null;
    }
}
