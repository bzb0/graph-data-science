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
package org.neo4j.gds.core.compress;

import org.neo4j.gds.core.loading.CompressedLongArray;

public interface AdjacencyCompressor extends AutoCloseable {

    /**
     * Compress a list of target ids into an adjacency list.
     * The input {@code values} are an unsorted and separately compressed list of target ids.
     * Those ids can be unpacked into a {@code long[]} using {@link org.neo4j.gds.core.loading.CompressedLongArray#uncompress(long[])}.
     * The provided {@code long[]} must be able to hold at least {@link org.neo4j.gds.core.loading.CompressedLongArray#length()} elements.
     *
     * The input {@code values} might also store properties (called 'weights').
     * This can be checked with {@link org.neo4j.gds.core.loading.CompressedLongArray#hasWeights()}
     * and read with {@link org.neo4j.gds.core.loading.CompressedLongArray#weights()}.
     * The returned {@code long[][]} has the number of properties in the first dimension, followed by an
     * uncompressed {@code long[]} for each property. Those values belong to the target id that is stored at
     * the same array index in the uncompressed target list. Implementors need to make sure to maintain that order
     * when re-ordering the target ids.
     *
     * Implementors of this method can use the provided {@link LongArrayBuffer} to reuse existing allocations
     * of a {@code long[]} or share newly created buffers with next invocations of this method.
     * The buffer exists solely so that implementors can reduce allocations of {@code long[]}.
     * It is not expected that the buffer contains any useful data after this method invocation.
     *
     * The provided {@code values} array will not be used after this method call and should be released at the end
     * using {@link org.neo4j.gds.core.loading.CompressedLongArray#release()}.
     *
     * Implementors will need to write the resulting target list somewhere. Where exactly is up to the implementation.
     * The results should end up in the data that is returned by the
     * {@link AdjacencyCompressorBlueprint#build()} method.
     * The method only needs to return the degree of the compressed adjacency list. This value can be different from
     * {@link org.neo4j.gds.core.loading.CompressedLongArray#length()} due to possible deduplication, though it
     * should never be larger. The return value is only used for tracking progress and reporting, it is not stored in
     * the graph. How the degree is stored so that it can be used by the Graph is up the implementor of this method.
     *
     * @param nodeId The node id that is the source node for this adjacency list.
     *              The id is from the GDS internal scope, it is *not* the Neo4j ID.
     * @param values A list of target ids, unsorted and compressed.
     * @param buffer A long array that may or may not be used during the compression.
     * @return the degree of the compressed adjacency list
     */
    int compress(
        long nodeId,
        CompressedLongArray values,
        LongArrayBuffer buffer
    );

    /**
     * Closing this compressor will release some internal data structures, making them eligible for garbage collection.
     * The compressor cannot be used after it has been closed.
     */
    @Override
    void close();
}
