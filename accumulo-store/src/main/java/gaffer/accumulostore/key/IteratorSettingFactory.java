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

package gaffer.accumulostore.key;

import gaffer.accumulostore.key.exception.IteratorSettingException;
import gaffer.accumulostore.AccumuloStore;
import gaffer.data.elementdefinition.view.View;
import gaffer.operation.GetOperation;
import org.apache.accumulo.core.client.IteratorSetting;
import org.apache.hadoop.util.bloom.BloomFilter;

/**
 * The iterator settings factory is designed to enable the AccumuloStore to easily set all iterators that will be commonly required by different implementations of the gaffer.accumulostore.key.
 * These methods may return null if the specified iterator is not required/desired for your particular{@link AccumuloKeyPackage} implementation.
 */
public interface IteratorSettingFactory {

    /**
     * Returns an {@link org.apache.accumulo.core.client.IteratorSetting} that can be used to apply
     * an iterator that will filter elements based on their vertices membership in a given {@link org.apache.hadoop.util.bloom.BloomFilter} to a {@link org.apache.accumulo.core.client.Scanner}.
     *
     * @param filter
     * @return A new {@link IteratorSetting} for an Iterator capable of filtering elements based on checking its serialised form for membership in a {@link BloomFilter}
     * @throws gaffer.accumulostore.key.exception.IteratorSettingException
     */
    IteratorSetting getBloomFilterIteratorSetting(final BloomFilter filter) throws IteratorSettingException;

    /**
     * Returns an {@link org.apache.accumulo.core.client.IteratorSetting} that can be used to apply
     * an iterator that will filter elements based on predicates to a {@link org.apache.accumulo.core.client.Scanner}.
     *
     * @param view
     * @param store
     * @return A new {@link IteratorSetting} for an Iterator capable of filtering {@link gaffer.data.element.Element}s based on a {@link View}
     * @throws gaffer.accumulostore.key.exception.IteratorSettingException
     */
    IteratorSetting getElementFilterIteratorSetting(final View view, final AccumuloStore store) throws IteratorSettingException;

    /**
     * Returns an Iterator that will filter out Edges/Entities/Undirected/Directed Edges based on the options in the gaffer.accumulostore.operation
     * May return null if this type of iterator is not required for example if Key are constructed to enable this filtering via the Accumulo Key
     *
     * @param operation
     * @return A new {@link IteratorSetting} for an Iterator capable of filtering {@link gaffer.data.element.Element}s based on the options defined in the gaffer.accumulostore.operation
     */
    IteratorSetting getEdgeEntityDirectionFilterIteratorSetting(GetOperation<?, ?> operation);

    /**
     * Returns an Iterator that will aggregate values in the accumulo table, this iterator  will be applied to the table on creation
     *
     * @param store
     * @return A new {@link IteratorSetting} for an Iterator that will aggregate elements where they have the same key based on the {@link gaffer.data.elementdefinition.schema.DataSchema}
     */
    IteratorSetting getAggregatorIteratorSetting(final AccumuloStore store) throws IteratorSettingException;

    /**
     * Returns an Iterator that will aggregate values at query time this is to be used for the summarise option on getElement queries.
     *
     * @param store
     * @return A new {@link IteratorSetting} for an Iterator that will aggregate elements at query time on the {@link gaffer.data.elementdefinition.schema.DataSchema}
     */
    IteratorSetting getQueryTimeAggregatorIteratorSetting(final AccumuloStore store) throws IteratorSettingException;
}