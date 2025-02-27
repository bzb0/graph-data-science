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

import org.neo4j.gds.core.huge.TransientCompressedList;
import org.neo4j.gds.core.utils.paged.HugeIntArray;
import org.neo4j.gds.core.utils.mem.AllocationTracker;
import org.neo4j.gds.core.utils.paged.HugeLongArray;

import java.util.Arrays;

import static org.neo4j.gds.core.utils.mem.MemoryUsage.sizeOfByteArray;

public final class TransientCompressedListBuilder implements CsrListBuilder<byte[], TransientCompressedList> {

    private final BumpAllocator<byte[]> builder;
    private final AllocationTracker allocationTracker;

    TransientCompressedListBuilder(AllocationTracker allocationTracker) {
        this.builder = new BumpAllocator<>(allocationTracker, Factory.INSTANCE);
        this.allocationTracker = allocationTracker;
    }

    @Override
    public Allocator newAllocator() {
        return new Allocator(this.builder.newLocalAllocator());
    }

    @Override
    public TransientCompressedList build(HugeIntArray degrees, HugeLongArray offsets) {
        var intoPages = builder.intoPages();
        reorder(intoPages, offsets, degrees);
        return new TransientCompressedList(intoPages, degrees, offsets);
    }

    @Override
    public void flush() {
    }

    private enum Factory implements BumpAllocator.Factory<byte[]> {
        INSTANCE;

        @Override
        public byte[][] newEmptyPages() {
            return new byte[0][];
        }

        @Override
        public byte[] newPage(int length) {
            return new byte[length];
        }

        @Override
        public byte[] copyOfPage(byte[] bytes, int length) {
            return Arrays.copyOf(bytes, length);
        }

        @Override
        public int lengthOfPage(byte[] bytes) {
            return bytes.length;
        }

        @Override
        public long memorySizeOfPage(int length) {
            return sizeOfByteArray(length);
        }
    }

    static final class Allocator implements CsrListBuilder.Allocator<byte[]> {

        private final BumpAllocator.LocalAllocator<byte[]> allocator;

        private Allocator(BumpAllocator.LocalAllocator<byte[]> allocator) {
            this.allocator = allocator;
        }

        @Override
        public void close() {
        }

        @Override
        public long write(byte[] targets, int length) {
            return allocator.insert(targets, length);
        }
    }
}
