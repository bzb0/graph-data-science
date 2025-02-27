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
package org.neo4j.gds.compat._43drop040;

import org.neo4j.gds.compat.JobPromise;
import org.neo4j.gds.compat.JobRunner;
import org.neo4j.scheduler.Group;
import org.neo4j.scheduler.JobHandle;
import org.neo4j.scheduler.JobScheduler;

import java.util.concurrent.TimeUnit;

final class JobRunnerImpl implements JobRunner {
    private final JobScheduler scheduler;
    private final Group group;

    JobRunnerImpl(JobScheduler scheduler, Group group) {
        this.group = group;
        this.scheduler = scheduler;
    }

    @Override
    public JobPromise scheduleAtInterval(
        Runnable runnable,
        long initialDelay,
        long rate,
        TimeUnit timeUnit
    ) {
        JobHandle<?> jobHandle = this.scheduler.scheduleRecurring(group, runnable, initialDelay, rate, timeUnit);
        return jobHandle::cancel;
    }
}
