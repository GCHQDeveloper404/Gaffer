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

package gaffer.accumulostore;

import gaffer.accumulostore.utils.Constants;
import gaffer.accumulostore.utils.TableUtilException;
import gaffer.accumulostore.utils.TableUtils;
import gaffer.data.elementdefinition.schema.DataSchema;
import gaffer.graph.Graph;
import gaffer.store.StoreException;
import gaffer.store.schema.StoreSchema;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.MapWritable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Factory for creating new {@link gaffer.graph.Graph} instances of {@link gaffer.accumulostore.AccumuloStore}.
 */
public class AccumuloStoreBackedGraphFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccumuloStoreBackedGraphFactory.class);

    /**
     * Creates a new {@link gaffer.accumulostore.AccumuloStore} from a properties file only, provided the table name specified in that properties file already exists
     *
     * @param propertiesFileLocation
     * @return A new Instance of the AccumuloStore
     * @throws gaffer.store.StoreException
     */
    public static Graph getGraph(final Path propertiesFileLocation) throws StoreException {
        final AccumuloProperties props = new AccumuloProperties(propertiesFileLocation);
        final MapWritable map;
        try {
            map = TableUtils.getStoreConstructorInfo(props);
        } catch (TableUtilException e) {
            throw new StoreException(e);
        }

        final DataSchema dataSchema = DataSchema.fromJson(((BytesWritable) map.get(Constants.DATA_SCHEMA_KEY)).getBytes());
        final StoreSchema storeSchema = StoreSchema.fromJson(((BytesWritable) map.get(Constants.STORE_SCHEMA_KEY)).getBytes());
        final String keyPackageClass = new String(((BytesWritable) map.get(Constants.KEY_PACKAGE_KEY)).getBytes());

        if (!props.getKeyPackageClass().equals(keyPackageClass)) {
            LOGGER.warn("Key package class " + props.getKeyPackageClass()
                    + " will be overridden by cached class " + keyPackageClass);
            props.setKeyPackageClass(keyPackageClass);
        }

        return new Graph(dataSchema, storeSchema, props);
    }

}