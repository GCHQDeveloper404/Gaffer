/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package gaffer.accumulostore.utils;

import org.apache.hadoop.util.bloom.BloomFilter;
import org.apache.hadoop.util.hash.Hash;

/**
 * Utilities for the creation of Bloom Filters
 */
public class BloomFilterUtils {

    private BloomFilterUtils() {
    }

    /**
     * Calculates the size of the {@link org.apache.hadoop.util.bloom.BloomFilter} needed to achieve the desired false positive rate given that the
     * specified number of items will be added to the set, but with the maximum size limited as specified.
     *
     * @param falsePositiveRate
     * @param numItemsToBeAdded
     * @param maximumSize
     * @return An Integer representing the size of the bloom filter needed.
     */
    public static int calculateBloomFilterSize(final double falsePositiveRate, final int numItemsToBeAdded, final int maximumSize) {
        int size = (int) (-numItemsToBeAdded * Math.log(falsePositiveRate) / (Math.pow(Math.log(2.0), 2.0)));
        return Math.min(size, maximumSize);
    }

    /**
     * Calculates the optimal number of hash functions to use in a {@link org.apache.hadoop.util.bloom.BloomFilter} of the given size, to which the
     * given number of items will be added.
     *
     * @param bloomFilterSize
     * @param numItemsToBeAdded
     * @return An integer representing the optimal number of hashes to use
     */
    public static int calculateNumHashes(final int bloomFilterSize, final int numItemsToBeAdded) {
        return Math.max(1, (int) ((bloomFilterSize / numItemsToBeAdded) * Math.log(2.0)));
    }

    /**
     * Returns a {@link org.apache.hadoop.util.bloom.BloomFilter} of the necessary size to achieve the given false positive rate (subject
     * to the given maximum size), configured with the optimal number of hash functions.
     *
     * @param falsePositiveRate
     * @param numItemsToBeAdded
     * @param maximumSize
     * @return A new BloomFilter with the desired Settings
     */
    public static BloomFilter getBloomFilter(final double falsePositiveRate, final int numItemsToBeAdded, final int maximumSize) {
        final int size = calculateBloomFilterSize(falsePositiveRate, numItemsToBeAdded, maximumSize);
        final int numHashes = calculateNumHashes(size, numItemsToBeAdded);
        return new BloomFilter(size, numHashes, Hash.MURMUR_HASH);
    }

    /**
     * Returns a {@link org.apache.hadoop.util.bloom.BloomFilter} of the given size.
     *
     * @param size
     * @return A new BloomFilter of the desired size
     */
    public static BloomFilter getBloomFilter(final int size) {
        return new BloomFilter(size, 13, Hash.MURMUR_HASH);
    }
}