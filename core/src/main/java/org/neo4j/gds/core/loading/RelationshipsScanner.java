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

import org.neo4j.gds.api.GraphLoaderContext;
import org.neo4j.gds.api.IdMapping;
import org.neo4j.gds.core.TransactionContext;
import org.neo4j.gds.core.utils.RawValues;
import org.neo4j.gds.core.utils.StatementAction;
import org.neo4j.gds.core.utils.TerminationFlag;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.kernel.api.KernelTransaction;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class RelationshipsScanner extends StatementAction implements RecordScanner {

    public static InternalImporter.CreateScanner of(
        GraphLoaderContext loadingContext,
        ProgressTracker progressTracker,
        IdMapping idMap,
        StoreScanner<RelationshipReference> scanner,
        Collection<SingleTypeRelationshipImporter.Builder> importerBuilders
    ) {
        List<SingleTypeRelationshipImporter.Builder.WithImporter> builders = importerBuilders
            .stream()
            .map(relImporter -> relImporter.loadImporter(relImporter.loadProperties()))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        if (builders.isEmpty()) {
            return InternalImporter.createEmptyScanner();
        }
        return new RelationshipsScanner.Creator(
            loadingContext.transactionContext(),
            progressTracker,
            idMap,
            scanner,
            builders,
            loadingContext.terminationFlag()
        );
    }

    static final class Creator implements InternalImporter.CreateScanner {
        private final TransactionContext tx;
        private final ProgressTracker progressTracker;
        private final IdMapping idMap;
        private final StoreScanner<RelationshipReference> scanner;
        private final List<SingleTypeRelationshipImporter.Builder.WithImporter> importerBuilders;
        private final TerminationFlag terminationFlag;

        Creator(
                TransactionContext tx,
                ProgressTracker progressTracker,
                IdMapping idMap,
                StoreScanner<RelationshipReference> scanner,
                List<SingleTypeRelationshipImporter.Builder.WithImporter> importerBuilders,
                TerminationFlag terminationFlag) {
            this.tx = tx;
            this.progressTracker = progressTracker;
            this.idMap = idMap;
            this.scanner = scanner;
            this.importerBuilders = importerBuilders;
            this.terminationFlag = terminationFlag;
        }

        @Override
        public RecordScanner create(final int index) {
            return new RelationshipsScanner(
                    tx,
                    terminationFlag,
                    progressTracker,
                    idMap,
                    scanner,
                    index,
                    importerBuilders
            );
        }

        @Override
        public Collection<Runnable> flushTasks() {
            return importerBuilders.stream()
                    .flatMap(SingleTypeRelationshipImporter.Builder.WithImporter::flushTasks)
                    .collect(Collectors.toList());
        }
    }

    private final TerminationFlag terminationFlag;
    private final ProgressTracker progressTracker;
    private final IdMapping idMap;
    private final StoreScanner<RelationshipReference> scanner;
    private final int scannerIndex;
    private final List<SingleTypeRelationshipImporter.Builder.WithImporter> importerBuilders;

    private long relationshipsImported;
    private long weightsImported;

    private RelationshipsScanner(
            TransactionContext tx,
            TerminationFlag terminationFlag,
            ProgressTracker progressTracker,
            IdMapping idMap,
            StoreScanner<RelationshipReference> scanner,
            int threadIndex,
            List<SingleTypeRelationshipImporter.Builder.WithImporter> importerBuilders) {
        super(tx);
        this.terminationFlag = terminationFlag;
        this.progressTracker = progressTracker;
        this.idMap = idMap;
        this.scanner = scanner;
        this.scannerIndex = threadIndex;
        this.importerBuilders = importerBuilders;
    }

    @Override
    public String threadName() {
        return "relationship-store-scan-" + scannerIndex;
    }

    @Override
    public void accept(KernelTransaction transaction) {
        try (StoreScanner.ScanCursor<RelationshipReference> cursor = scanner.getCursor(transaction)) {
            List<SingleTypeRelationshipImporter> importers = this.importerBuilders.stream()
                    .map(imports -> imports.withBuffer(idMap, scanner.bufferSize(), transaction))
                    .collect(Collectors.toList());

            RelationshipsBatchBuffer[] buffers = importers
                    .stream()
                    .map(SingleTypeRelationshipImporter::buffer)
                    .toArray(RelationshipsBatchBuffer[]::new);

            RecordsBatchBuffer<RelationshipReference> buffer = CompositeRelationshipsBatchBuffer.of(buffers);

            long allImportedRels = 0L;
            long allImportedWeights = 0L;
            while (buffer.scan(cursor)) {
                terminationFlag.assertRunning();
                long imported = 0L;
                for (SingleTypeRelationshipImporter importer : importers) {
                    imported += importer.importRelationships();
                }
                int importedRels = RawValues.getHead(imported);
                int importedWeights = RawValues.getTail(imported);
                progressTracker.progressLogger().logProgress(importedRels);
                allImportedRels += importedRels;
                allImportedWeights += importedWeights;
            }
            relationshipsImported = allImportedRels;
            weightsImported = allImportedWeights;
        }
    }

    @Override
    public long propertiesImported() {
        return weightsImported;
    }

    @Override
    public long recordsImported() {
        return relationshipsImported;
    }

}
