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
package gaffer.accumulostore.key.core.impl;

import gaffer.accumulostore.key.AccumuloElementConverter;
import gaffer.accumulostore.key.core.impl.model.ColumnQualifierColumnVisibilityValueTriple;
import gaffer.accumulostore.key.exception.AccumuloElementConversionException;
import gaffer.accumulostore.key.exception.AggregationException;
import gaffer.accumulostore.utils.Constants;
import gaffer.accumulostore.utils.IteratorUtils;
import gaffer.data.element.Properties;
import gaffer.data.element.function.ElementAggregator;
import gaffer.data.elementdefinition.schema.DataSchema;
import gaffer.store.schema.StoreSchema;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.iterators.IteratorEnvironment;
import org.apache.accumulo.core.iterators.SortedKeyValueIterator;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;
import java.util.Map;

public class CoreKeyColumnQualifierVisibilityValueAggregatorIterator extends CoreKeyColumnQualifierColumnVisibilityValueCombiner {
    private DataSchema dataSchema;
    private ElementAggregator aggregator;

    @Override
    public ColumnQualifierColumnVisibilityValueTriple reduce(final Key key, final Iterator<ColumnQualifierColumnVisibilityValueTriple> iter) {
        ColumnQualifierColumnVisibilityValueTriple triple;
        final String group;
        try {
            group = elementConverter.getGroupFromColumnFamily(key.getColumnFamilyData().getBackingArray());
        } catch (AccumuloElementConversionException e) {
            throw new RuntimeException(e);
        }
        aggregator = dataSchema.getElement(group).getAggregator();
        triple = iter.next();
        if(!iter.hasNext()) {
        	return triple;
        }
        while (iter.hasNext()) {
        	aggregateProperties(group, triple);
            triple = iter.next();
        }
    	aggregateProperties(group, triple);
        Properties properties = new Properties();
        aggregator.state(properties);
        final ColumnQualifierColumnVisibilityValueTriple result;
        try {
            result = new ColumnQualifierColumnVisibilityValueTriple(elementConverter.buildColumnQualifier(group, properties),
                    elementConverter.buildColumnVisibility(group, properties), elementConverter.getValueFromProperties(properties, group));
        } catch (AccumuloElementConversionException e) {
            throw new AggregationException("ColumnQualifierVisibilityAggregatorIterator failed to re-create an element", e);
        }
        return result;
    }

    @Override
    public void init(final SortedKeyValueIterator<Key, Value> source, final Map<String, String> options, final IteratorEnvironment env) throws IOException {
        super.init(source, options, env);
        validateOptions(options);
    }

    @Override
    public boolean validateOptions(final Map<String, String> options) {
        super.validateOptions(options);
        if (!options.containsKey(Constants.DATA_SCHEMA)) {
            throw new IllegalArgumentException("Must specify the " + Constants.DATA_SCHEMA);
        }
        dataSchema = DataSchema.fromJson(options.get(Constants.DATA_SCHEMA).getBytes());
        try {
            Class<?> elementConverterClass = Class.forName(options.get(Constants.ACCUMULO_KEY_CONVERTER));
            elementConverter = (AccumuloElementConverter) elementConverterClass.getConstructor(StoreSchema.class).newInstance(storeSchema);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                | IllegalArgumentException | InvocationTargetException
                | NoSuchMethodException | SecurityException e) {
            throw new AggregationException("Failed to load element converter from class name provided : " + options.get(Constants.ACCUMULO_KEY_CONVERTER));
        }
        return true;
    }

    @Override
    public IteratorOptions describeOptions() {
        return IteratorUtils.describeOptions("ColumnQualifierVisibilityAggregatorIterator",
                "Combines properties over elements which differ only in column qualifier and visibility",
                super.describeOptions());
    }

    private void aggregateProperties(final String group, final ColumnQualifierColumnVisibilityValueTriple triple) {
        Properties properties = new Properties();
        try {
            properties.putAll(elementConverter.getPropertiesFromColumnQualifier(group, triple.getColumnQualifier()));
            properties.putAll(elementConverter.getPropertiesFromColumnVisibility(group, triple.getColumnVisibility()));
            properties.putAll(elementConverter.getPropertiesFromValue(group, triple.getValue()));
        } catch (AccumuloElementConversionException e) {
            throw new RuntimeException(e);
        }
        aggregator.aggregate(properties);
    }
}