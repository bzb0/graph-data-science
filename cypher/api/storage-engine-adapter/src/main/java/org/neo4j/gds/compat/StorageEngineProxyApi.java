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
package org.neo4j.gds.compat;

import org.neo4j.common.Edition;
import org.neo4j.configuration.Config;
import org.neo4j.counts.CountsAccessor;
import org.neo4j.counts.CountsStore;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.io.layout.DatabaseLayout;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.storageengine.api.CommandCreationContext;
import org.neo4j.storageengine.api.StorageReader;
import org.neo4j.storageengine.api.StorageRelationshipTraversalCursor;
import org.neo4j.token.TokenHolders;

public interface StorageEngineProxyApi {

    <ENGINE extends AbstractInMemoryStorageEngine, BUILDER extends InMemoryStorageEngineBuilder<ENGINE>> BUILDER inMemoryStorageEngineBuilder(
        DatabaseLayout databaseLayout,
        TokenHolders tokenHolders
    );

    CountsStore inMemoryCountsStore(GraphStore graphStore, TokenHolders tokenHolders);

    CommandCreationContext inMemoryCommandCreationContext();

    void initRelationshipTraversalCursorForRelType(
        StorageRelationshipTraversalCursor cursor,
        long sourceNodeId,
        int relTypeToken
    );

    StorageReader inMemoryStorageReader(
        GraphStore graphStore,
        TokenHolders tokenHolders,
        CountsAccessor counts
    );

    void createInMemoryDatabase(
        DatabaseManagementService dbms,
        String dbName,
        Config config
    );

    GraphDatabaseAPI startAndGetInMemoryDatabase(DatabaseManagementService dbms, String dbName);

    DatabaseManagementServiceBuilder setSkipDefaultIndexesOnCreationSetting(DatabaseManagementServiceBuilder dbmsBuilder);

    AbstractInMemoryNodeCursor inMemoryNodeCursor(GraphStore graphStore, TokenHolders tokenHolders);

    AbstractInMemoryNodePropertyCursor inMemoryNodePropertyCursor(GraphStore graphStore, TokenHolders tokenHolders);

    AbstractInMemoryRelationshipTraversalCursor inMemoryRelationshipTraversalCursor(
        GraphStore graphStore,
        TokenHolders tokenHolders
    );

    Edition dbmsEdition(GraphDatabaseAPI api);
}
