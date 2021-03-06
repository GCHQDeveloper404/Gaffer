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
import gaffer.commonutil.TestGroups;
import gaffer.data.element.Element;
import gaffer.data.element.Entity;
import gaffer.data.elementdefinition.view.View;
import gaffer.operation.OperationChain;
import gaffer.operation.OperationException;
import gaffer.operation.data.EntitySeed;
import gaffer.operation.impl.add.AddElements;
import gaffer.operation.impl.get.GetElements;
import gaffer.operation.impl.get.GetElementsSeed;
import gaffer.operation.impl.get.GetRelatedElements;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AccumuloStoreTest {

    private static AccumuloStore store;
    private static final String AUTHS = "Test";

    @BeforeClass
    public static void setup() throws Exception {
        store = new MockAccumuloStoreForTest();
    }

    @Test(expected = OperationException.class)
    public void testErrorThrownWhenNoAuthorisationAreProvided() throws OperationException {
        List<Element> elements = new ArrayList<>();
        Entity e = new Entity(TestGroups.ENTITY);
        e.setVertex("1");
        elements.add(e);
        store.execute(new OperationChain<>(new AddElements(elements)));
    }

    @Test
    public void testAbleToInsertAndRetrieveEntityQueryingEqualAndRelated() throws OperationException {
        List<Element> elements = new ArrayList<>();
        Entity e = new Entity(TestGroups.ENTITY);
        e.setVertex("1");
        elements.add(e);
        AddElements add = new AddElements.Builder()
                .elements(elements)
                .option(Constants.OPERATION_AUTHORISATIONS, AUTHS)
                .build();
        store.execute(new OperationChain<>(add));

        GetElements<EntitySeed, Element> getBySeed = new GetElementsSeed.Builder<EntitySeed, Element>()
                .view(new View.Builder()
                        .entity(TestGroups.ENTITY)
                        .build())
                .addSeed(new EntitySeed("1"))
                .option(Constants.OPERATION_AUTHORISATIONS, AUTHS)
                .build();
        Iterable<Element> results = store.execute(new OperationChain<>(getBySeed));
        Iterator<Element> resultsIter = results.iterator();
        assertTrue(resultsIter.hasNext());
        assertEquals(e, resultsIter.next());
        assertFalse(resultsIter.hasNext());


        GetRelatedElements<EntitySeed, Element> getRelated = new GetRelatedElements.Builder<EntitySeed, Element>()
                .view(new View.Builder()
                        .entity(TestGroups.ENTITY)
                        .build())
                .addSeed(new EntitySeed("1"))
                .option(Constants.OPERATION_AUTHORISATIONS, AUTHS)
                .build();
        results = store.execute(new OperationChain<>(getRelated));
        resultsIter = results.iterator();
        assertTrue(resultsIter.hasNext());
        assertEquals(e, resultsIter.next());
        assertFalse(resultsIter.hasNext());
    }

}
