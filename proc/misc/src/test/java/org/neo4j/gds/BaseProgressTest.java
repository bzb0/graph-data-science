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

import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.gds.core.utils.ProgressLogger;
import org.neo4j.gds.core.utils.mem.MemoryRange;
import org.neo4j.gds.core.utils.progress.ProgressFeatureSettings;
import org.neo4j.gds.core.utils.progress.TaskRegistry;
import org.neo4j.gds.core.utils.progress.TaskRegistryExtension;
import org.neo4j.gds.core.utils.progress.tasks.Task;
import org.neo4j.gds.core.utils.progress.tasks.TaskProgressTracker;
import org.neo4j.gds.core.utils.progress.tasks.Tasks;
import org.neo4j.logging.Level;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;
import org.neo4j.test.extension.ExtensionCallback;

import java.util.stream.Stream;

public class BaseProgressTest extends BaseTest {

    protected static final MemoryRange MEMORY_ESTIMATION_RANGE = MemoryRange.of(10, 20);
    protected static final int MAX_CPU_CORES = 5;

    @Override
    @ExtensionCallback
    protected void configuration(TestDatabaseManagementServiceBuilder builder) {
        super.configuration(builder);
        builder.setConfig(GraphDatabaseSettings.store_internal_log_level, Level.DEBUG);
        builder.setConfig(ProgressFeatureSettings.progress_tracking_enabled, true);
        // make sure that we 1) have our extension under test and 2) have it only once
        builder.removeExtensions(ex -> ex instanceof TaskRegistryExtension);
        builder.addExtension(new TaskRegistryExtension());
    }

    public static class BaseProgressTestProc {

        @Context
        public TaskRegistry taskRegistry;

        @Procedure("gds.test.pl")
        public Stream<Bar> foo(
            @Name(value = "taskName") String taskName,
            @Name(value = "withMemoryEstimation", defaultValue = "false") boolean withMemoryEstimation,
            @Name(value = "withConcurrency", defaultValue = "false") boolean withConcurrency
        ) {
            var task = Tasks.task(taskName, Tasks.leaf("leaf", 3));
            if (withMemoryEstimation) {
                task.setEstimatedMemoryRangeInBytes(MEMORY_ESTIMATION_RANGE);
            }
            if (withConcurrency) {
                task.setMaxConcurrency(MAX_CPU_CORES);
            }
            var taskProgressTracker = new TaskProgressTracker(task, ProgressLogger.NULL_LOGGER, taskRegistry);
            taskProgressTracker.beginSubTask();
            taskProgressTracker.beginSubTask();
            taskProgressTracker.logProgress(1);
            return Stream.empty();
        }
    }

    public static class Bar {
        public final String field;

        public Bar(String field) {this.field = field;}
    }

    public static class NonReleasingTaskRegistry implements TaskRegistry {

        private final TaskRegistry taskRegistry;

        NonReleasingTaskRegistry(TaskRegistry taskRegistry) {
            this.taskRegistry = taskRegistry;
        }

        @Override
        public void registerTask(Task task) {
            taskRegistry.registerTask(task);
        }

        @Override
        public void unregisterTask() {
            // skip un registering the task because we want to observe the messages after the algo is done
        }
    }
}
