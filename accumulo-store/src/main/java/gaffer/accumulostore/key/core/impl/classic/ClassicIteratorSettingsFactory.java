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

import gaffer.accumulostore.utils.Constants;
import gaffer.accumulostore.utils.IteratorSettingBuilder;
import gaffer.accumulostore.key.core.AbstractCoreKeyIteratorSettingsFactory;
import gaffer.operation.GetOperation;
import gaffer.operation.GetOperation.IncludeEdgeType;
import gaffer.operation.GetOperation.IncludeIncomingOutgoingType;

import org.apache.accumulo.core.client.IteratorSetting;

public class ClassicIteratorSettingsFactory extends AbstractCoreKeyIteratorSettingsFactory {
    private static final String EDGE_DIRECTED_UNDIRECTED_FILTER = ClassicEdgeDirectedUndirectedFilterIterator.class.getName();

    @Override
    public IteratorSetting getEdgeEntityDirectionFilterIteratorSetting(final GetOperation<?, ?> operation) {
        if (operation.getIncludeIncomingOutGoing() == IncludeIncomingOutgoingType.BOTH && operation.getIncludeEdges() == IncludeEdgeType.ALL) {
            return null;
        }

        return new IteratorSettingBuilder(Constants.EDGE_ENTITY_DIRECTED_FILTER_ITERATOR_PRIORITY,
                Constants.EDGE_ENTITY_DIRECTED_UNDIRECTED_FILTER_ITERATOR_NAME, EDGE_DIRECTED_UNDIRECTED_FILTER)
                .includeEdges(operation.getIncludeEdges())
                .includeIncomingOutgoing(operation.getIncludeIncomingOutGoing())
                .includeEntities(operation.isIncludeEntities())
                .build();
    }

}