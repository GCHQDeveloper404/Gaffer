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
package gaffer.accumulostore.key.core;

import gaffer.accumulostore.key.AccumuloElementConverter;
import gaffer.accumulostore.key.exception.AccumuloElementConversionException;
import gaffer.accumulostore.utils.ByteArrayEscapeUtils;
import gaffer.accumulostore.utils.Constants;
import gaffer.accumulostore.utils.Pair;
import gaffer.accumulostore.utils.StorePositions;
import gaffer.data.element.Edge;
import gaffer.data.element.Element;
import gaffer.data.element.Entity;
import gaffer.data.element.Properties;
import gaffer.exception.SerialisationException;
import gaffer.serialisation.Serialisation;
import gaffer.store.schema.StoreElementDefinition;
import gaffer.store.schema.StorePropertyDefinition;
import gaffer.store.schema.StoreSchema;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Value;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.MapWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableUtils;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public abstract class AbstractCoreKeyAccumuloElementConverter implements AccumuloElementConverter {
    protected static final byte[] DELIMITER_ARRAY = new byte[]{0};
    protected final StoreSchema storeSchema;

    public AbstractCoreKeyAccumuloElementConverter(final StoreSchema storeSchema) {
        this.storeSchema = storeSchema;
    }

    @Override
    public Pair<Key> getKeysFromElement(final Element element) throws AccumuloElementConversionException {
        if (element.getClass().equals(Entity.class)) {
            final Key key = getKeyFromEntity((Entity) element);
            return new Pair<>(key, null);
        }

        return getKeysFromEdge((Edge) element);
    }

    @Override
    public Pair<Key> getKeysFromEdge(final Edge edge) throws AccumuloElementConversionException {
        // Get pair of row keys
        Pair<byte[]> rowKeys = getRowKeysFromEdge(edge);
        byte[] columnFamily = buildColumnFamily(edge.getGroup());
        byte[] columnQualifier = buildColumnQualifier(edge.getGroup(), edge.getProperties());
        byte[] columnVisibility = buildColumnVisibility(edge.getGroup(), edge.getProperties());
        long timeStamp = buildTimestamp(edge);
        // Create Accumulo keys - note that second row key may be null (if it's a self-edge) and
        // in that case we should return null second key
        Key key1 = new Key(rowKeys.getFirst(), columnFamily, columnQualifier, columnVisibility, timeStamp);
        Key key2 = rowKeys.getSecond() != null ? new Key(rowKeys.getSecond(), columnFamily, columnQualifier, columnVisibility, timeStamp) : null;
        // Return pair of keys
        return new Pair<>(key1, key2);
    }

    @Override
    public Key getKeyFromEntity(final Entity entity) throws AccumuloElementConversionException {
        // Row key is formed from identifier and group
        byte[] rowKey = getRowKeyFromEntity(entity);
        byte[] columnFamily = buildColumnFamily(entity.getGroup());
        byte[] columnQualifier = buildColumnQualifier(entity.getGroup(), entity.getProperties());

        // Column visibility is formed from the visibility
        byte[] columnVisibility = buildColumnVisibility(entity.getGroup(), entity.getProperties());

        long timeStamp = buildTimestamp(entity);

        // Create and return key
        return new Key(rowKey, columnFamily, columnQualifier, columnVisibility, timeStamp);
    }

    @Override
    public Value getValueFromProperties(final Properties properties, final String group) throws AccumuloElementConversionException {
        MapWritable map = new MapWritable();
        for (String propertyName : properties.keySet()) {
            StorePropertyDefinition propertyDefinition = storeSchema.getElement(group).getProperty(propertyName);
            if (propertyDefinition != null) {
                if (StorePositions.VALUE.isEqual(propertyDefinition.getPosition())) {
                    try {
                        map.put(new Text(propertyName), new BytesWritable(propertyDefinition.getSerialiser().serialise(properties.get(propertyName))));
                    } catch (SerialisationException e) {
                        throw new AccumuloElementConversionException("Failed to serialise property " + propertyName, e);
                    }
                }
            }
        }
        if (map.isEmpty()) {
            return new Value();
        }
        return new Value(WritableUtils.toByteArray(map));
    }

    @Override
    public Value getValueFromElement(final Element element) throws AccumuloElementConversionException {
        return getValueFromProperties(element.getProperties(), element.getGroup());
    }

    @Override
    public Properties getPropertiesFromValue(final String group, final Value value) throws AccumuloElementConversionException {
        Properties properties = new Properties();
        if (value == null || value.getSize() == 0) {
            return properties;
        }
        MapWritable map = new MapWritable();
        try {
            map.readFields(new DataInputStream(new ByteArrayInputStream(value.get())));
        } catch (IOException e) {
            throw new AccumuloElementConversionException("Failed to read map writable from value", e);
        }
        StoreElementDefinition elementDefinition = storeSchema.getElement(group);
        for (Writable writeableKey : map.keySet()) {
            String propertyName = writeableKey.toString();
            BytesWritable propertyValueBytes = (BytesWritable) map.get(writeableKey);
            try {
                properties.put(propertyName, elementDefinition.getProperty(propertyName).getSerialiser().deserialise(propertyValueBytes.getBytes()));
            } catch (SerialisationException e) {
                throw new AccumuloElementConversionException("Failed to deserialise property " + propertyName, e);
            }
        }
        return properties;
    }

    @Override
    public Element getElementFromKey(final Key key) throws AccumuloElementConversionException {
        return getElementFromKey(key, null);
    }

    @Override
    public Element getElementFromKey(final Key key, final Map<String, String> options) throws AccumuloElementConversionException {
        boolean keyRepresentsEntity = doesKeyRepresentEntity(key.getRowData().getBackingArray());
        if (keyRepresentsEntity) {
            return getEntityFromKey(key);
        }
        return getEdgeFromKey(key, options);
    }

    @Override
    public Element getFullElement(final Key key, final Value value) throws AccumuloElementConversionException {
        return getFullElement(key, value, null);
    }

    @Override
    public Element getFullElement(final Key key, final Value value, final Map<String, String> options) throws AccumuloElementConversionException {
        final Element element = getElementFromKey(key, options);
        element.copyProperties(getPropertiesFromValue(element.getGroup(), value));
        return element;
    }

    @Override
    public byte[] buildColumnFamily(final String group) throws AccumuloElementConversionException {
        try {
            return group.getBytes(Constants.UTF_8_CHARSET);
        } catch (UnsupportedEncodingException e) {
            throw new AccumuloElementConversionException(e.getMessage(), e);
        }
    }

    @Override
    public String getGroupFromColumnFamily(final byte[] columnFamily) throws AccumuloElementConversionException {
        try {
            return new String(columnFamily, Constants.UTF_8_CHARSET);
        } catch (UnsupportedEncodingException e) {
            throw new AccumuloElementConversionException(e.getMessage(), e);
        }
    }

    @Override
    public byte[] buildColumnVisibility(final String group, final Properties properties) throws AccumuloElementConversionException {
        StoreElementDefinition elDef = storeSchema.getElement(group);
        if (elDef == null) {
            throw new AccumuloElementConversionException("No element definition found for element class: " + group);
        }
        for (String propertyName : elDef.getProperties()) {
            Object property = properties.get(propertyName);
            if (property != null) {
                StorePropertyDefinition propertyDef = elDef.getProperty(propertyName);
                if (StorePositions.VISIBILITY.isEqual(propertyDef.getPosition())) {
                    try {
                        return ByteArrayEscapeUtils.escape(propertyDef.getSerialiser().serialise(property));
                    } catch (SerialisationException e) {
                        throw new AccumuloElementConversionException(e.getMessage(), e);
                    }
                }
            }
        }
        return Constants.EMPTY_BYTES;
    }

    @Override
    public Properties getPropertiesFromColumnVisibility(final String group, final byte[] columnVisibility) throws AccumuloElementConversionException {
        Properties properties = new Properties();
        if (columnVisibility == null || columnVisibility.length == 0) {
            return properties;
        }
        StoreElementDefinition elDef = storeSchema.getElement(group);
        if (elDef == null) {
            throw new AccumuloElementConversionException("No element definition found for element class: " + group);
        }
        for (String propertyName : elDef.getProperties()) {
            StorePropertyDefinition property = elDef.getProperty(propertyName);
            if (StorePositions.VISIBILITY.isEqual(property.getPosition())) {
                Serialisation serialiser = property.getSerialiser();
                try {
                    properties.put(propertyName, serialiser.deserialise(ByteArrayEscapeUtils.unEscape(columnVisibility)));
                } catch (SerialisationException e) {
                    throw new AccumuloElementConversionException(e.getMessage(), e);
                }
                return properties;
            }
        }
        return properties;
    }

    @Override
    public byte[] buildColumnQualifier(final String group, final Properties properties) throws AccumuloElementConversionException {
        StoreElementDefinition elDef = storeSchema.getElement(group);
        if (elDef == null) {
            throw new AccumuloElementConversionException("No element definition found for element class: " + group);
        }
        Boolean first = true;
        List<byte[]> bytes = new ArrayList<>();
        int totalLength = 0;
        byte[] byteHolder;
        for (String propertyName : elDef.getProperties()) {
            StorePropertyDefinition property = elDef.getProperty(propertyName);
            if (StorePositions.COLUMN_QUALIFIER.isEqual(property.getPosition())) {
                final Object value = properties.get(propertyName);
                if (value != null) {
                    if (!first) {
                        totalLength += 1;
                        bytes.add(DELIMITER_ARRAY);
                    }
                    byteHolder = ByteArrayEscapeUtils.escape(propertyName.getBytes());
                    bytes.add(byteHolder);
                    totalLength += byteHolder.length + 1;
                    bytes.add(DELIMITER_ARRAY);
                    final Serialisation serialiser = property.getSerialiser();
                    if (serialiser == null) {
                        bytes.add(value.toString().getBytes());
                    } else {
                        try {
                            byteHolder = ByteArrayEscapeUtils.escape(property.getSerialiser().serialise(value));
                            bytes.add(byteHolder);
                            totalLength += byteHolder.length;
                        } catch (SerialisationException e) {
                            throw new AccumuloElementConversionException("Failed to serialise Value for property " + propertyName, e);
                        }
                    }
                    first = false;
                }
            }
        }
        byte[] returnArr = new byte[totalLength];
        int currentLength = 0;
        for (byte[] byteArr : bytes) {
            System.arraycopy(byteArr, 0, returnArr, currentLength, byteArr.length);
            currentLength += byteArr.length;
        }
        return returnArr;
    }

    @Override
    public Properties getPropertiesFromColumnQualifier(final String group, final byte[] keyPortion) throws AccumuloElementConversionException {
        Properties result = new Properties();
        if (keyPortion == null || keyPortion.length == 0) {
            return result;
        }
        StoreElementDefinition elDef = storeSchema.getElement(group);
        List<Integer> positionsOfDelimiters = new ArrayList<>();
        for (int i = 0; i < keyPortion.length; i++) {
            if (keyPortion[i] == ByteArrayEscapeUtils.DELIMITER) {
                positionsOfDelimiters.add(i);
            }
        }
        Iterator<Integer> delimiters = positionsOfDelimiters.iterator();
        String propertyName;
        Object propertyValue;
        if (delimiters.hasNext()) {
            Integer last = delimiters.next();
            propertyName = new String(ByteArrayEscapeUtils.unEscape(Arrays.copyOfRange(keyPortion, 0, last)));
            int nextPos;
            if (delimiters.hasNext()) {
                nextPos = delimiters.next();
            } else {
                nextPos = keyPortion.length;
            }
            try {
                propertyValue = elDef.getProperty(propertyName).getSerialiser().deserialise(ByteArrayEscapeUtils.unEscape(Arrays.copyOfRange(keyPortion, last + 1, nextPos)));
            } catch (SerialisationException e) {
                throw new AccumuloElementConversionException("Failed to deserialise property " + propertyName, e);
            }
            last = nextPos;
            result.put(propertyName, propertyValue);
            while (delimiters.hasNext()) {
                propertyName = new String(ByteArrayEscapeUtils.unEscape(Arrays.copyOfRange(keyPortion, last + 1, last = delimiters.next())));
                if (delimiters.hasNext()) {
                    nextPos = delimiters.next();
                } else {
                    nextPos = keyPortion.length;
                }
                try {
                    propertyValue = elDef.getProperty(propertyName).getSerialiser().deserialise(ByteArrayEscapeUtils.unEscape(Arrays.copyOfRange(keyPortion, last + 1, nextPos)));
                } catch (SerialisationException e) {
                    throw new AccumuloElementConversionException("Failed to serialise property " + propertyName, e);
                }
                result.put(propertyName, propertyValue);
                last = nextPos;
            }
        }
        return result;
    }

    /**
     * Get the properties for a given group defined in the StoreSchema as being stored in the
     * Accumulo timestamp column.
     *
     * @param group     The {@link Element} type to be queried
     * @param timestamp
     * @return The Properties stored within the Timestamp part of the {@link Key}
     * @throws AccumuloElementConversionException If the supplied group has not been defined
     */
    public Properties getPropertiesFromTimestamp(final String group, final long timestamp) throws AccumuloElementConversionException {
        StoreElementDefinition elDef = storeSchema.getElement(group);
        if (elDef == null) {
            throw new AccumuloElementConversionException("No element definition found for element class: " + group);
        }
        final Properties properties = new Properties();
        for (String propertyName : elDef.getProperties()) {
            StorePropertyDefinition property = elDef.getProperty(propertyName);
            if (StorePositions.TIMESTAMP.isEqual(property.getPosition())) {
                properties.put(propertyName, timestamp);
                return properties;
            }
        }
        return properties;
    }

    @Override
    public byte[] serialiseVertexForBloomKey(final Object vertex) throws AccumuloElementConversionException {
        try {
            return ByteArrayEscapeUtils.escape(this.storeSchema.getVertexSerialiser().serialise(vertex));
        } catch (SerialisationException e) {
            throw new AccumuloElementConversionException("Failed to serialise given identifier object for use in the bloom filter", e);
        }
    }

    protected abstract byte[] getRowKeyFromEntity(final Entity entity) throws AccumuloElementConversionException;

    protected abstract Pair<byte[]> getRowKeysFromEdge(final Edge edge) throws AccumuloElementConversionException;

    protected abstract boolean doesKeyRepresentEntity(final byte[] row) throws AccumuloElementConversionException;

    protected abstract Entity getEntityFromKey(final Key key) throws AccumuloElementConversionException;

    protected abstract boolean getSourceAndDestinationFromRowKey(final byte[] rowKey, final byte[][] sourceValueDestinationValue,
                                                                 final Map<String, String> options) throws AccumuloElementConversionException;

    protected boolean selfEdge(final Edge edge) {
        return edge.getSource().equals(edge.getDestination());
    }

    protected void addPropertiesToElement(final Element element, final Key key) throws AccumuloElementConversionException {
        element.copyProperties(getPropertiesFromColumnQualifier(element.getGroup(), key.getColumnQualifierData().getBackingArray()));
        element.copyProperties(getPropertiesFromColumnVisibility(element.getGroup(), key.getColumnVisibilityData().getBackingArray()));
        element.copyProperties(getPropertiesFromTimestamp(element.getGroup(), key.getTimestamp()));
    }

    protected Serialisation getVertexSerialiser() {
        return storeSchema.getVertexSerialiser();
    }

    protected Edge getEdgeFromKey(final Key key, final Map<String, String> options) throws AccumuloElementConversionException {
        byte[][] result = new byte[3][];
        boolean directed = getSourceAndDestinationFromRowKey(key.getRowData().getBackingArray(), result, options);
        String group;
        try {
            group = new String(key.getColumnFamilyData().getBackingArray(), Constants.UTF_8_CHARSET);
        } catch (UnsupportedEncodingException e) {
            throw new AccumuloElementConversionException(e.getMessage(), e);
        }
        try {
            Edge edge = new Edge(group, getVertexSerialiser().deserialise(result[0]), getVertexSerialiser().deserialise(result[1]), directed);
            addPropertiesToElement(edge, key);
            return edge;
        } catch (SerialisationException e) {
            throw new AccumuloElementConversionException("Failed to re-create Edge from key", e);
        }
    }

    protected byte[] getSerialisedSource(final Edge edge) throws AccumuloElementConversionException {
        try {
            return ByteArrayEscapeUtils.escape(getVertexSerialiser().serialise(edge.getSource()));
        } catch (SerialisationException e) {
            throw new AccumuloElementConversionException("Failed to serialise Edge Source", e);
        }
    }

    protected byte[] getSerialisedDestination(final Edge edge) throws AccumuloElementConversionException {
        try {
            return ByteArrayEscapeUtils.escape(getVertexSerialiser().serialise(edge.getDestination()));
        } catch (SerialisationException e) {
            throw new AccumuloElementConversionException("Failed to serialise Edge Destination", e);
        }
    }

    protected String getGroupFromKey(final Key key) {
        return new String(key.getColumnFamilyData().getBackingArray());
    }

    private long buildTimestamp(final Element element) throws AccumuloElementConversionException {
        StoreElementDefinition elDef = storeSchema.getElement(element.getGroup());
        if (elDef == null) {
            throw new AccumuloElementConversionException("No element definition found for element class: " + element.getGroup());
        }
        for (String propertyName : elDef.getProperties()) {
            Object property = element.getProperty(propertyName);
            if (property != null) {
                StorePropertyDefinition propertyDef = elDef.getProperty(propertyName);
                if (StorePositions.TIMESTAMP.isEqual(propertyDef.getPosition())) {
                    return (Long) property;
                }
            }
        }
        return new Date().getTime();
    }
}
