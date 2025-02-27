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
package org.neo4j.gds.core.utils.paged;

import org.neo4j.gds.core.utils.mem.AllocationTracker;
import org.neo4j.gds.core.utils.mem.MemoryUsage;
import org.neo4j.gds.core.utils.AtomicDoubleArray;

public final class PagedAtomicDoubleArray extends PagedDataStructure<AtomicDoubleArray> {

    private static final PageAllocator.Factory<AtomicDoubleArray> ALLOCATOR_FACTORY;

    static {
        int pageSize = PageUtil.pageSizeFor(Double.BYTES);
        long pageUsage = MemoryUsage.sizeOfInstance(AtomicDoubleArray.class) + MemoryUsage.sizeOfIntArray(pageSize);

        ALLOCATOR_FACTORY = PageAllocator.of(
                pageSize,
                pageUsage,
                () -> new AtomicDoubleArray(pageSize),
                new AtomicDoubleArray[0]);
    }

    public static PagedAtomicDoubleArray newArray(long size, AllocationTracker allocationTracker) {
        return new PagedAtomicDoubleArray(size, ALLOCATOR_FACTORY.newAllocator(allocationTracker));
    }

    private PagedAtomicDoubleArray(
            final long size,
            final PageAllocator<AtomicDoubleArray> allocator) {
        super(size, allocator);
    }

    public double get(long index) {
        assert index < capacity();
        final int pageIndex = pageIndex(index);
        final int indexInPage = indexInPage(index);
        return pages[pageIndex].get(indexInPage);
    }

    public void set(long index, double value) {
        assert index < capacity();
        final int pageIndex = pageIndex(index);
        final int indexInPage = indexInPage(index);
        pages[pageIndex].set(indexInPage, value);
    }

    public void add(long index, double delta) {
        assert index < capacity();
        final int pageIndex = pageIndex(index);
        final int indexInPage = indexInPage(index);
        pages[pageIndex].add(indexInPage, delta);
    }
}
