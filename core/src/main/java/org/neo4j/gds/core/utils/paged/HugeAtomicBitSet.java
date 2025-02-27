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

import com.carrotsearch.hppc.BitSet;
import org.neo4j.gds.core.utils.ArrayUtil;
import org.neo4j.gds.core.utils.BitUtil;
import org.neo4j.gds.core.utils.mem.AllocationTracker;
import org.neo4j.gds.core.utils.mem.MemoryUsage;
import org.neo4j.gds.utils.StringFormatting;

public final class HugeAtomicBitSet {
    private static final int NUM_BITS = Long.SIZE;

    private final HugeAtomicLongArray bits;
    private final long numBits;
    private final int remainder;

    public static long memoryEstimation(long size) {
        var wordsSize = BitUtil.ceilDiv(size, NUM_BITS);
        return HugeAtomicLongArray.memoryEstimation(wordsSize) + MemoryUsage.sizeOfInstance(HugeAtomicBitSet.class);
    }

    public static HugeAtomicBitSet create(long size, AllocationTracker allocationTracker) {
        var wordsSize = BitUtil.ceilDiv(size, NUM_BITS);
        int remainder = (int) (size % NUM_BITS);
        return new HugeAtomicBitSet(HugeAtomicLongArray.newArray(wordsSize, allocationTracker), size, remainder);
    }

    private HugeAtomicBitSet(HugeAtomicLongArray bits, long numBits, int remainder) {
        this.bits = bits;
        this.numBits = numBits;
        this.remainder = remainder;
    }

    /**
     * Returns the state of the bit at the given index.
     */
    public boolean get(long index) {
        assert(index < numBits);
        long wordIndex = index / NUM_BITS;
        int bitIndex = (int) index % NUM_BITS;
        long bitmask = 1L << bitIndex;
        return (bits.get(wordIndex) & bitmask) != 0;
    }

    /**
     * Sets the bit at the given index to true.
     */
    public void set(long index) {
        assert(index < numBits);

        long wordIndex = index / NUM_BITS;
        int bitIndex = (int) index % NUM_BITS;
        long bitmask = 1L << bitIndex;

        long oldWord = bits.get(wordIndex);
        while (true) {
            long newWord = oldWord | bitmask;
            if (newWord == oldWord) {
                // nothing to set
                return;
            }
            long currentWord = bits.compareAndExchange(wordIndex, oldWord, newWord);
            if (currentWord == oldWord) {
                // CAS successful
                return;
            }
            // CAS unsuccessful, try again
            oldWord = currentWord;
        }
    }

    /**
     * Sets the bits from the startIndex (inclusive) to the endIndex (exclusive).
     */
    public void set(long startIndex, long endIndex) {
        long startWordIndex = startIndex / NUM_BITS;
        // since endIndex is exclusive, we need the word before that index
        long endWordIndex = (endIndex - 1) / NUM_BITS;

        long startBitMask = -1L << startIndex;
        long endBitMask = -1L >>> -endIndex;

        if (startWordIndex == endWordIndex) {
            // set within single word
            setWord(startWordIndex, startBitMask & endBitMask);
        } else {
            // set within range
            setWord(startWordIndex, startBitMask);
            for (long wordIndex = startWordIndex + 1; wordIndex < endWordIndex; wordIndex++) {
                bits.set(wordIndex, -1L);
            }
            setWord(endWordIndex, endBitMask);
        }
    }

    private void setWord(long wordIndex, long bitMask) {
        var oldWord = bits.get(wordIndex);
        while (true) {
            var newWord = oldWord | bitMask;
            if (newWord == oldWord) {
                // already set
                return;
            }
            var currentWord = bits.compareAndExchange(wordIndex, oldWord, newWord);
            if (currentWord == oldWord) {
                // CAX successful
                return;
            }
            oldWord = currentWord;
        }
    }

    /**
     * Sets a bit and returns the previous value.
     * The index should be less than the BitSet size.
     */
    public boolean getAndSet(long index) {
        assert(index < numBits);

        long wordIndex = index / NUM_BITS;
        int bitIndex = (int) index % NUM_BITS;
        long bitmask = 1L << bitIndex;

        long oldWord = bits.get(wordIndex);
        while (true) {
            long newWord = oldWord | bitmask;
            if (newWord == oldWord) {
                // already set
                return true;
            }
            long currentWord = bits.compareAndExchange(wordIndex, oldWord, newWord);
            if (currentWord == oldWord) {
                // CAS successful
                return false;
            }
            // CAS unsuccessful, try again
            oldWord = currentWord;
        }
    }

    /**
     * Toggles the bit at the given index.
     */
    public void flip(long index) {
        assert(index < numBits);

        long wordIndex = index / NUM_BITS;
        int bitIndex = (int) index % NUM_BITS;
        long bitmask = 1L << bitIndex;

        long oldWord = bits.get(wordIndex);
        while (true) {
            long newWord = oldWord ^ bitmask;
            long currentWord = bits.compareAndExchange(wordIndex, oldWord, newWord);
            if (currentWord == oldWord) {
                // CAS successful
                return;
            }
            // CAS unsuccessful, try again
            oldWord = currentWord;
        }
    }

    /**
     * Returns the number of set bits in the bit set.
     * <p>
     * Note: this method is not thread-safe.
     */
    public long cardinality() {
        long setBitCount = 0;

        for (long wordIndex = 0; wordIndex < bits.size(); wordIndex++) {
            setBitCount += Long.bitCount(bits.get(wordIndex));
        }

        return setBitCount;
    }

    /**
     * Returns true iff no bit is set.
     * <p>
     * Note: this method is not thread-safe.
     */
    public boolean isEmpty() {
        for (long wordIndex = 0; wordIndex < bits.size(); wordIndex++) {
            if (Long.bitCount(bits.get(wordIndex)) > 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true iff all bits are set.
     * <p>
     * Note: this method is not thread-safe.
     */
    public boolean allSet() {
        for (long wordIndex = 0; wordIndex < bits.size() - 1; wordIndex++) {
            if (Long.bitCount(bits.get(wordIndex)) < NUM_BITS) {
                return false;
            }
        }
        return Long.bitCount(bits.get(bits.size() - 1)) >= remainder;
    }

    /**
     * Returns the number of bits in the bitset.
     */
    public long size() {
        return numBits;
    }

    /**
     * Resets all bits in the bit set.
     * <p>
     * Note: this method is not thread-safe.
     */
    public void clear() {
        bits.setAll(0);
    }

    /**
     * Resets the bit at the given index.
     */
    public void clear(long index) {
        assert(index < numBits);

        long wordIndex = index / NUM_BITS;
        int bitIndex = (int) index % NUM_BITS;
        long bitmask = ~(1L << bitIndex);

        long oldWord = bits.get(wordIndex);
        while (true) {
            long newWord = oldWord & bitmask;
            if (newWord == oldWord) {
                // already cleared
                return;
            }
            long currentWord = bits.compareAndExchange(wordIndex, oldWord, newWord);
            if (currentWord == oldWord) {
                // CAS successful
                return;
            }
            // CAS unsuccessful, try again
            oldWord = currentWord;
        }
    }

    public BitSet toBitSet() {
        if (bits.size() <= ArrayUtil.MAX_ARRAY_LENGTH) {
            return new BitSet(((HugeAtomicLongArray.SingleHugeAtomicLongArray) bits).page(), (int) bits.size());
        }
        throw new UnsupportedOperationException(StringFormatting.formatWithLocale(
            "Cannot convert HugeAtomicBitSet with more than %s entries.",
            ArrayUtil.MAX_ARRAY_LENGTH
        ));
    }
}
