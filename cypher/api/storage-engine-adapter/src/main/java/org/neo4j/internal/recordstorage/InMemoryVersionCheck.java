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

import org.neo4j.io.pagecache.context.CursorContext;
import org.neo4j.kernel.impl.store.MetaDataStore;
import org.neo4j.storageengine.api.StoreVersion;
import org.neo4j.storageengine.api.StoreVersionCheck;

import java.util.Optional;

public class InMemoryVersionCheck implements StoreVersionCheck {

    public InMemoryVersionCheck() {
    }

    @Override
    public Optional<String> storeVersion(CursorContext cursorContext) {
        return Optional.of(InMemoryStoreVersion.STORE_VERSION);
    }

    @Override
    public String configuredVersion() {
        return "gds-experimental";
    }

    @Override
    public StoreVersion versionInformation(String storeVersion) {
        return new InMemoryStoreVersion();
    }

    @Override
    public Result checkUpgrade(String desiredVersion, CursorContext cursorContext) {
        return new StoreVersionCheck.Result(Outcome.ok, InMemoryStoreVersion.STORE_VERSION, null);
    }

    @Override
    public String storeVersionToString(long storeVersion) {
        return MetaDataStore.versionLongToString(storeVersion);
    }

    @Override
    public boolean isVersionConfigured() {
        return true;
    }
}
