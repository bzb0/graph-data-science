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
import org.neo4j.kernel.KernelVersion;
import org.neo4j.storageengine.api.ExternalStoreId;
import org.neo4j.storageengine.api.MetadataProvider;
import org.neo4j.storageengine.api.StoreId;
import org.neo4j.storageengine.api.TransactionId;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

public abstract class AbstractInMemoryMetaDataProvider implements MetadataProvider {
    private final InMemoryLogVersionRepository logVersionRepository = new InMemoryLogVersionRepository();
    private final ExternalStoreId externalStoreId = new ExternalStoreId(UUID.randomUUID());

    public abstract AbstractTransactionIdStore transactionIdStore();

    public InMemoryLogVersionRepository logVersionRepository() {
        return logVersionRepository;
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public long getCurrentLogVersion() {
        return this.logVersionRepository.getCurrentLogVersion();
    }

    @Override
    public void setCurrentLogVersion(long version, CursorContext cursorContext) {
        this.logVersionRepository.setCurrentLogVersion(version, cursorContext);
    }

    @Override
    public long incrementAndGetVersion(CursorContext cursorContext) {
        return this.logVersionRepository.incrementAndGetVersion(cursorContext);
    }

    @Override
    public long getCheckpointLogVersion() {
        return this.logVersionRepository.getCheckpointLogVersion();
    }

    @Override
    public void setCheckpointLogVersion(long version, CursorContext cursorContext) {
        this.logVersionRepository.setCheckpointLogVersion(version, cursorContext);
    }

    @Override
    public long incrementAndGetCheckpointLogVersion(CursorContext cursorContext) {
        return this.logVersionRepository.incrementAndGetCheckpointLogVersion(cursorContext);
    }

    @Override
    public StoreId getStoreId() {
        return StoreId.UNKNOWN;
    }

    @Override
    public Optional<ExternalStoreId> getExternalStoreId() {
        return Optional.of(this.externalStoreId);
    }

    @Override
    public long nextCommittingTransactionId() {
        return this.transactionIdStore().nextCommittingTransactionId();
    }

    @Override
    public long committingTransactionId() {
        return this.transactionIdStore().committingTransactionId();
    }


    @Override
    public void transactionCommitted(long transactionId, int checksum, long commitTimestamp, CursorContext cursorContext) {
        this.transactionIdStore().transactionCommitted(transactionId, checksum, commitTimestamp, cursorContext);
    }

    @Override
    public long getLastCommittedTransactionId() {
        return this.transactionIdStore().getLastCommittedTransactionId();
    }

    @Override
    public TransactionId getLastCommittedTransaction() {
        return this.transactionIdStore().getLastCommittedTransaction();
    }

    @Override
    public TransactionId getUpgradeTransaction() {
        return this.transactionIdStore().getUpgradeTransaction();
    }

    @Override
    public long getLastClosedTransactionId() {
        return this.transactionIdStore().getLastClosedTransactionId();
    }

    @Override
    public void setLastCommittedAndClosedTransactionId(
        long transactionId,
        int checksum,
        long commitTimestamp,
        long byteOffset,
        long logVersion,
        CursorContext cursorContext
    ) {
        this.transactionIdStore().setLastCommittedAndClosedTransactionId(
            transactionId,
            checksum,
            commitTimestamp,
            byteOffset,
            logVersion,
            cursorContext
        );
    }

    @Override
    public void transactionClosed(
        long transactionId, long logVersion, long byteOffset, CursorContext cursorContext
    ) {
        this.transactionIdStore().transactionClosed(transactionId, logVersion, byteOffset, cursorContext);
    }

    @Override
    public void resetLastClosedTransaction(
        long transactionId, long logVersion, long byteOffset, boolean missingLogs, CursorContext cursorContext
    ) {
        this.transactionIdStore().resetLastClosedTransaction(
            transactionId,
            logVersion,
            byteOffset,
            missingLogs,
            cursorContext
        );
    }

    @Override
    public void flush(CursorContext cursorContext) {
        this.transactionIdStore().flush(cursorContext);
    }

    @Override
    public KernelVersion kernelVersion() {
        return KernelVersion.LATEST;
    }

    @Override
    public Optional<UUID> getDatabaseIdUuid(CursorContext cursorTracer) {
        throw new IllegalStateException("Not supported");
    }

    @Override
    public void setDatabaseIdUuid(UUID uuid, CursorContext cursorContext) {
        throw new IllegalStateException("Not supported");
    }
}
