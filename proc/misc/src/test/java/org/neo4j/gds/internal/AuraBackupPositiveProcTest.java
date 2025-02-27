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
package org.neo4j.gds.internal;

import org.apache.commons.lang3.mutable.MutableInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.gds.TestLog;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.neo4j.gds.internal.AuraTestSupport.assertGraph;
import static org.neo4j.gds.internal.AuraTestSupport.assertModel;

class AuraBackupPositiveProcTest extends AuraBackupBaseProcTest {

    @TempDir
    Path tempDir;

    @Override
    Path getBackupLocation() {
        return tempDir;
    }

    @Test
    void shouldPersistGraphStoresAndModels() {
        var backupQuery = "CALL gds.internal.backup()";

        var graphCount = new MutableInt(0);
        var modelCount = new MutableInt(0);

        runQueryWithRowConsumer(backupQuery, row -> {
            assertThat(row.getBoolean("done")).isTrue();
            assertThat(row.getString("backupName")).isNotEmpty();
            assertThat(row.getNumber("backupMillis").longValue()).isGreaterThanOrEqualTo(0L);

            var path = row.getString("path");

            if (row.getString("type").equals("graph")) {
                graphCount.increment();
                assertGraph(path);
            } else {
                modelCount.increment();
                assertModel(path);
            }
        });

        assertThat(graphCount.longValue()).isEqualTo(2);
        assertThat(modelCount.longValue()).isEqualTo(2);

        assertThat(testLog.getMessages(TestLog.INFO))
            .anySatisfy(msg -> assertThat(msg)
                .matches(
                    "Backup finished within the given timeout, it took \\d+ seconds and the provided timeout was 42 seconds."
                ));
    }

}
