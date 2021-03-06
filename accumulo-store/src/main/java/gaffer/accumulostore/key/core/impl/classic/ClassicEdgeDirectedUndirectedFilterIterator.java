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
package gaffer.accumulostore.key.core.impl.classic;

import gaffer.accumulostore.utils.ByteArrayEscapeUtils;
import gaffer.accumulostore.utils.Constants;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.iterators.Filter;
import org.apache.accumulo.core.iterators.IteratorEnvironment;
import org.apache.accumulo.core.iterators.SortedKeyValueIterator;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ClassicEdgeDirectedUndirectedFilterIterator extends Filter {

    private boolean unDirectedEdges = false;
    private boolean directedEdges = false;
    private boolean entities = false;
    private boolean incomingEdges = false;
    private boolean outgoingEdges = false;
    private static final byte UNDIRECTED = (byte) 1;
    private static final byte DIRECTED_SOURCE_FIRST = (byte) 2;
    private static final byte DIRECTED_DESTINATION_FIRST = (byte) 3;

    @Override
    public boolean accept(final Key key, final Value value) {
        byte[] rowID = key.getRowData().getBackingArray();
        if (!entities) {
            return checkEdge(rowID);
        } else {
            boolean foundDelimiter = false;
            for (final byte aRowID : rowID) {
                if (aRowID == ByteArrayEscapeUtils.DELIMITER) {
                    foundDelimiter = true;
                    break;
                }
            }
            return !foundDelimiter || checkEdge(rowID);
        }
    }

    private boolean checkEdge(final byte[] rowID) {
        byte flag = rowID[rowID.length - 1];
        if (unDirectedEdges) {
            return flag == UNDIRECTED;
        } else if (directedEdges) {
            return flag != UNDIRECTED && checkDirection(flag);
        } else {
           return checkDirection(flag);
        }
    }

    private boolean checkDirection(final byte flag) {
    	if (incomingEdges) {
            if (flag == DIRECTED_SOURCE_FIRST) {
                return false;
            }
        } else if (outgoingEdges) {
            if (flag == DIRECTED_DESTINATION_FIRST) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void init(final SortedKeyValueIterator<Key, Value> source, final Map<String, String> options, final IteratorEnvironment env) throws IOException {
        validateOptions(options);
        super.init(source, options, env);
    }

    @Override
    public boolean validateOptions(Map<String, String> options) {
        if (options.containsKey(Constants.DIRECTED_EDGE_ONLY) && options.containsKey(Constants.UNDIRECTED_EDGE_ONLY)) {
            throw new IllegalArgumentException("Must specify ONLY ONE of " + Constants.DIRECTED_EDGE_ONLY
                    + " or " + Constants.UNDIRECTED_EDGE_ONLY);
        }
        if (options.containsKey(Constants.INCOMING_EDGE_ONLY) && options.containsKey(Constants.OUTGOING_EDGE_ONLY)) {
            throw new IllegalArgumentException("Must specify ONLY ONE of " + Constants.INCOMING_EDGE_ONLY
                    + " or " + Constants.OUTGOING_EDGE_ONLY);
        }
        if (options.containsKey(Constants.INCOMING_EDGE_ONLY)) {
            incomingEdges = true;
        }
        if (options.containsKey(Constants.OUTGOING_EDGE_ONLY)) {
            outgoingEdges = true;
        }
        if (options.containsKey(Constants.DIRECTED_EDGE_ONLY)) {
            directedEdges = true;
        }
        if (options.containsKey(Constants.UNDIRECTED_EDGE_ONLY)) {
            unDirectedEdges = true;
        }
        if (options.containsKey(Constants.ENTITY_ONLY)) {
            entities = true;
        }
        return true;
    }

    @Override
    public IteratorOptions describeOptions() {
        Map<String, String> namedOptions = new HashMap<>();
        namedOptions.put(Constants.DIRECTED_EDGE_ONLY, "set if only want directed edges (value is ignored)");
        namedOptions.put(Constants.UNDIRECTED_EDGE_ONLY, "set if only want undirected edges (value is ignored)");
        return new IteratorOptions("EntityOrEdgeOnlyFilterIterator", "Only returns Entities or Edges as specified by the user's options",
                namedOptions, null);
    }
}