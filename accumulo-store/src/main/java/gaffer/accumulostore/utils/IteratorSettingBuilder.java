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

import gaffer.accumulostore.key.exception.IteratorSettingException;
import gaffer.accumulostore.key.AccumuloElementConverter;
import gaffer.data.elementdefinition.schema.DataSchema;
import gaffer.data.elementdefinition.view.View;
import gaffer.operation.GetOperation;
import gaffer.store.schema.StoreSchema;
import org.apache.accumulo.core.client.IteratorSetting;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.iterators.SortedKeyValueIterator;
import org.apache.hadoop.util.bloom.BloomFilter;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class IteratorSettingBuilder {
    private IteratorSetting setting;

    public IteratorSettingBuilder(final IteratorSetting setting) {
        this.setting = setting;
    }

    public IteratorSettingBuilder(final int priority, final String name, final Class<? extends SortedKeyValueIterator<Key, Value>> iteratorClass) {
        setting = new IteratorSetting(priority, name, iteratorClass);
    }

    public IteratorSettingBuilder(final int priority, final String name, final String iteratorClass) {
        setting = new IteratorSetting(priority, name, iteratorClass);
    }


    public IteratorSettingBuilder option(final String option, String value) {
        setting.addOption(option, value);
        return this;
    }

    public IteratorSettingBuilder all() {
        setting.addOption("all", "true");
        return this;
    }

    public IteratorSettingBuilder bloomFilter(final BloomFilter filter) throws IteratorSettingException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            filter.write(new DataOutputStream(baos));
        } catch (IOException e) {
            throw new IteratorSettingException("Failed to write bloom filter", e);
        }

        try {
            setting.addOption(Constants.BLOOM_FILTER, new String(baos.toByteArray(), Constants.BLOOM_FILTER_CHARSET));
        } catch (UnsupportedEncodingException e) {
            throw new IteratorSettingException("Failed to encode the bloom filter to a string", e);
        }

        return this;
    }


    public IteratorSettingBuilder includeEdges(final GetOperation.IncludeEdgeType includeEdgeType) {
        if (GetOperation.IncludeEdgeType.DIRECTED == includeEdgeType) {
            setting.addOption(Constants.DIRECTED_EDGE_ONLY, "true");
        } else if (GetOperation.IncludeEdgeType.UNDIRECTED == includeEdgeType) {
            setting.addOption(Constants.UNDIRECTED_EDGE_ONLY, "true");
        }

        return this;
    }

    public IteratorSettingBuilder includeIncomingOutgoing(final GetOperation.IncludeIncomingOutgoingType includeIncomingOutGoing) {
        if (GetOperation.IncludeIncomingOutgoingType.INCOMING == includeIncomingOutGoing) {
            setting.addOption(Constants.INCOMING_EDGE_ONLY, "true");
        } else if (GetOperation.IncludeIncomingOutgoingType.OUTGOING == includeIncomingOutGoing) {
            setting.addOption(Constants.OUTGOING_EDGE_ONLY, "true");
        }
        return this;
    }

    public IteratorSettingBuilder includeEntities(final boolean includeEntities) {
        if (includeEntities) {
            setting.addOption(Constants.ENTITY_ONLY, "true");
        }
        return this;
    }

    public IteratorSettingBuilder dataSchema(final DataSchema dataSchema) {
        setting.addOption(Constants.DATA_SCHEMA, new String(dataSchema.toJson(false)));
        return this;
    }

    public IteratorSettingBuilder storeSchema(final StoreSchema storeSchema) {
        setting.addOption(Constants.STORE_SCHEMA, new String(storeSchema.toJson(false)));
        return this;
    }

    public IteratorSettingBuilder view(final View view) {
        setting.addOption(Constants.VIEW, new String(view.toJson(false)));
        return this;
    }

    public IteratorSettingBuilder keyConverter(final Class<? extends AccumuloElementConverter> converter) {
        setting.addOption(Constants.ACCUMULO_KEY_CONVERTER, converter.getName());
        return this;
    }

    public IteratorSettingBuilder keyConverter(final AccumuloElementConverter converter) {
        return keyConverter(converter.getClass());
    }

    public IteratorSetting build() {
        return setting;
    }
}