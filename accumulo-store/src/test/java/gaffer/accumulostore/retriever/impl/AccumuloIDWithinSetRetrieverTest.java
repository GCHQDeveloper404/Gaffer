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

package gaffer.accumulostore.retriever.impl;

import gaffer.accumulostore.AccumuloStore;
import gaffer.accumulostore.MockAccumuloStoreForTest;
import gaffer.accumulostore.key.core.impl.byteEntity.ByteEntityKeyPackage;
import gaffer.accumulostore.key.core.impl.classic.ClassicKeyPackage;
import gaffer.accumulostore.key.exception.AccumuloElementConversionException;
import gaffer.accumulostore.key.exception.IteratorSettingException;
import gaffer.accumulostore.utils.AccumuloPropertyNames;
import gaffer.accumulostore.utils.Constants;
import gaffer.accumulostore.utils.TableUtils;
import gaffer.commonutil.TestGroups;
import gaffer.data.element.Edge;
import gaffer.data.element.Element;
import gaffer.data.element.Entity;
import gaffer.data.elementdefinition.view.View;
import gaffer.data.elementdefinition.view.ViewEdgeDefinition;
import gaffer.data.elementdefinition.view.ViewEntityDefinition;
import gaffer.operation.GetOperation;
import gaffer.operation.GetOperation.IncludeEdgeType;
import gaffer.operation.GetOperation.IncludeIncomingOutgoingType;
import gaffer.operation.OperationChain;
import gaffer.operation.OperationException;
import gaffer.operation.data.EntitySeed;
import gaffer.operation.impl.add.AddElements;
import gaffer.operation.impl.get.GetElements;
import gaffer.operation.impl.get.GetRelatedElements;
import gaffer.store.StoreException;

import org.apache.accumulo.core.client.AccumuloException;
import org.apache.accumulo.core.client.TableExistsException;
import org.apache.hadoop.util.bloom.BloomFilter;
import org.apache.hadoop.util.hash.Hash;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AccumuloIDWithinSetRetrieverTest {

    private static final String AUTHS = "Test";
    private static View defaultView;
    private static AccumuloStore byteEntityStore;
    private static AccumuloStore gaffer1KeyStore;

    @Before
    public void setup() throws IOException, StoreException {
        byteEntityStore = new MockAccumuloStoreForTest(ByteEntityKeyPackage.class);
        gaffer1KeyStore = new MockAccumuloStoreForTest(ClassicKeyPackage.class);
        setupGraph(byteEntityStore);
        setupGraph(gaffer1KeyStore);
        defaultView = new View.Builder().edge(TestGroups.EDGE, new ViewEdgeDefinition()).entity(TestGroups.ENTITY, new ViewEntityDefinition()).build();
    }

    /**
     * Tests that the correct {@link gaffer.data.element.Edge}s are returned. Tests that {@link gaffer.data.element.Entity}s are also returned
     * (unless the return edges only option has been set on the {@link gaffer.operation.impl.get.GetElements}). It is desirable
     * for {@link gaffer.data.element.Entity}s to be returned as a common use-case is to use this method to complete the "half-hop"
     * in a breadth-first search, and then getting all the information about the nodes is often required.
     */
    @Test
    public void testGetCorrectEdges() throws StoreException {
        testGetCorrectEdges(gaffer1KeyStore, true);
        testGetCorrectEdges(gaffer1KeyStore, false);
        testGetCorrectEdges(byteEntityStore, false);
        testGetCorrectEdges(byteEntityStore, true);

    }

    static void testGetCorrectEdges(final AccumuloStore store, final boolean loadIntoMemory) throws StoreException {
        // Query for all edges in set {customer|A0, customer|A23}
        Set<EntitySeed> seeds = new HashSet<>();
        seeds.add(new EntitySeed("A0"));
        seeds.add(new EntitySeed("A23"));
        GetElements<EntitySeed, ?> op = new GetRelatedElements<>(defaultView, seeds);
        op.addOption(Constants.OPERATION_AUTHORISATIONS, AUTHS);
        AccumuloIDWithinSetRetriever retriever = new AccumuloIDWithinSetRetriever(store, op, loadIntoMemory);
        Set<Element> results = new HashSet<>();
        for (Element elm : retriever) {
            results.add(elm);
        }
        retriever.close();

        Set<Element> expectedResults = new HashSet<>();
        Element expectedElement1 = new Edge(TestGroups.EDGE, "A0", "A23", true);
        expectedElement1.putProperty(AccumuloPropertyNames.COUNT, 23);
        expectedResults.add(expectedElement1);
        Element expectedElement2 = new Entity(TestGroups.ENTITY, "A0");
        expectedElement2.putProperty(AccumuloPropertyNames.COUNT, 10000);
        expectedResults.add(expectedElement2);
        Element expectedElement3 = new Entity(TestGroups.ENTITY, "A23");
        expectedElement3.putProperty(AccumuloPropertyNames.COUNT, 23);
        expectedResults.add(expectedElement3);

        for (Element expectedResult : expectedResults) {
            assertTrue(results.contains(expectedResult));
        }

        // Query for all edges in set {customer|A1} - there shouldn't be any, but we will get the entity for A1
        seeds.clear();
        seeds.add(new EntitySeed("A1"));
        op = new GetRelatedElements<>(defaultView, seeds);
        op.addOption(Constants.OPERATION_AUTHORISATIONS, AUTHS);
        retriever = new AccumuloIDWithinSetRetriever(store, op, loadIntoMemory);
        results.clear();
        int count = 0;
        for (Element element : retriever) {
            count++;
            results.add(element);
        }
        expectedResults.clear();
        expectedElement1 = new Entity(TestGroups.ENTITY, "A1");
        expectedElement1.putProperty(AccumuloPropertyNames.COUNT, 1);
        expectedResults.add(expectedElement1);
        assertEquals(1, count);
        for (Element expectedResult : expectedResults) {
            assertTrue(results.contains(expectedResult));
        }

        // Query for all edges in set {customer|A1, customer|A2} - there shouldn't be any edges but will
        // get the two entities
        seeds.clear();
        seeds.add(new EntitySeed("A1"));
        seeds.add(new EntitySeed("A2"));
        op = new GetRelatedElements<>(defaultView, seeds);
        op.addOption(Constants.OPERATION_AUTHORISATIONS, AUTHS);
        retriever = new AccumuloIDWithinSetRetriever(store, op, loadIntoMemory);
        results.clear();
        count = 0;
        for (Element element : retriever) {
            count++;
            results.add(element);
        }
        expectedElement1 = new Entity(TestGroups.ENTITY, "A1");
        expectedElement1.putProperty(AccumuloPropertyNames.COUNT, 1);
        expectedResults.add(expectedElement1);
        expectedElement2 = new Entity(TestGroups.ENTITY, "A2");
        expectedElement2.putProperty(AccumuloPropertyNames.COUNT, 2);
        expectedResults.add(expectedElement2);
        assertEquals(2, count);
        for (Element expectedResult : expectedResults) {
            assertTrue(results.contains(expectedResult));
        }
    }

    /**
     * Tests that the subtle case of setting outgoing or incoming edges only option is dealt with correctly.
     * When querying for edges within a set, the outgoing or incoming edges only needs to be turned off, for
     * two reasons. First, it doesn't make conceptual sense. If the each is from a member of set X to another
     * member of set X, what would it mean for it to be "outgoing"? (It makes sense to ask for directed edges
     * only, or undirected edges only.) Second, if the option is left on then results can be missed. For example,
     * suppose we have a graph with an edge A->B and we ask for all edges with both ends in the set {A,B}. Consider
     * what happens using the batching mechanism, with A in the first batch and B in the second batch. When the
     * first batch is queried for, the Bloom filter will consist solely of {A}. Thus the edge A->B will not be
     * returned. When the next batch is queried for, the Bloom filter will consist of A and B, so normally the
     * edge A->B will be returned. But if the outgoing edges only option is turned on then the edge will not be
     * returned, as it is not an edge out of B.
     * <p/>
     * This unit tests creates that situation and tests that the edge is still returned. Effectively this is
     * testing that the outgoing edges only (and incoming edges only) option is ignored when the
     * <code>getGraphElementsWithStatisticsWithinSet</code> method is used.
     */
    @Test
    public void testDealWithOutgoingEdgesOnlyOption() {
        testDealWithOutgoingEdgesOnlyOption(byteEntityStore);
        testDealWithOutgoingEdgesOnlyOption(gaffer1KeyStore);
    }

    public void testDealWithOutgoingEdgesOnlyOption(final AccumuloStore store) {
        try {
            // Create set of GraphElementWithStatistics to gaffer.accumulostore data before adding it to the graph.
            Set<Element> data = new HashSet<>();

            // Create edge A -> B
            Edge edge = new Edge(TestGroups.EDGE, "A", "B", true);
            edge.putProperty(AccumuloPropertyNames.COUNT, 1);
            data.add(edge);

            // Add data
            addElements(data, store);


            // Set outgoing edges only option, and query for the set {A,B}.
            store.getProperties().setMaxEntriesForBatchScanner("1");
            List<EntitySeed> seeds = new ArrayList<>();
            seeds.add(new EntitySeed("A"));
            seeds.add(new EntitySeed("B"));
            GetElements<EntitySeed, ?> op = new GetRelatedElements<>(defaultView, seeds);
            op.addOption(Constants.OPERATION_AUTHORISATIONS, AUTHS);
            op.setIncludeIncomingOutGoing(IncludeIncomingOutgoingType.OUTGOING);
            AccumuloIDWithinSetRetriever retriever = new AccumuloIDWithinSetRetriever(store, op, true);
            Set<Element> results = new HashSet<>();
            for (Element element : retriever) {
                results.add(element);
            }
            retriever.close();
            Set<Element> expectedResults = new HashSet<>();
            expectedResults.add(edge);
            assertEquals(expectedResults, results);

            // Set set edges only option, and query for the set {A,B}.
            op.setIncludeIncomingOutGoing(GetOperation.IncludeIncomingOutgoingType.INCOMING);

            retriever = new AccumuloIDWithinSetRetriever(store, op, true);
            results.clear();
            for (Element element : retriever) {
                results.add(element);
            }
            retriever.close();
            assertEquals(expectedResults, results);


        } catch (StoreException e) {
            fail("Failed to set up graph in Accumulo with exception: " + e);
        }

    }

    /**
     * Tests that the directed edges only and undirected edges only options are respected.
     *
     * @throws gaffer.store.StoreException
     */
    @Test
    public void testDealWithDirectedEdgesOnlyOption() throws StoreException {
        testDealWithDirectedEdgesOnlyOption(byteEntityStore);
        testDealWithDirectedEdgesOnlyOption(gaffer1KeyStore);
    }

    public void testDealWithDirectedEdgesOnlyOption(final AccumuloStore store) throws StoreException {
        /*Tests fail due to getting both versions of edges aka A->B AND B->A*/
        testDealWithDirectedEdgesOnlyOption(true, store);
        testDealWithDirectedEdgesOnlyOption(false, store);
    }

    static void testDealWithDirectedEdgesOnlyOption(final boolean loadIntoMemory, final AccumuloStore store) throws StoreException {
        // Create set of GraphElementWithStatistics to gaffer.accumulostore data before adding it to the graph.
        Set<Element> data = new HashSet<>();

        // Create directed edge A -> B and undirected edge A - B
        Edge edge1 = new Edge(TestGroups.EDGE, "A", "B", true);
        Edge edge2 = new Edge(TestGroups.EDGE, "A", "B", false);
        edge1.putProperty(AccumuloPropertyNames.COUNT, 1);
        edge2.putProperty(AccumuloPropertyNames.COUNT, 2);
        data.add(edge1);
        data.add(edge2);

        // Add data
        addElements(data, store);

        Set<EntitySeed> seeds = new HashSet<>();
        seeds.add(new EntitySeed("A"));
        seeds.add(new EntitySeed("B"));
        GetElements<EntitySeed, ?> op = new GetRelatedElements<>(defaultView, seeds);
        op.addOption(Constants.OPERATION_AUTHORISATIONS, AUTHS);
        // Set undirected edges only option, and query for edges in set {A, B} - should get edge2
        op.setIncludeEdges(GetOperation.IncludeEdgeType.UNDIRECTED);
        op.setIncludeEntities(false);
        AccumuloIDWithinSetRetriever retriever = new AccumuloIDWithinSetRetriever(store, op, loadIntoMemory);
        Set<Element> results = new HashSet<>();
        for (Element element : retriever) {
            results.add(element);
        }
        retriever.close();
        Set<Element> expectedResults = new HashSet<>();
        //TODO This returns the reverse of the same edge, skewing the results, this is because When A-B is in set B->A must be too
        Edge reverseEdge = new Edge(TestGroups.EDGE, "B", "A", false);
        reverseEdge.putProperty(AccumuloPropertyNames.COUNT, 2);
        expectedResults.add(edge2);
        expectedResults.add(reverseEdge);
        assertEquals(expectedResults, results);

        // Set directed edges only option, and query for edges in set {A, B} - should get edge1
        op = new GetRelatedElements<>(defaultView, seeds);
        op.addOption(Constants.OPERATION_AUTHORISATIONS, AUTHS);
        op.setIncludeEdges(IncludeEdgeType.DIRECTED);
        retriever = new AccumuloIDWithinSetRetriever(store, op, loadIntoMemory);
        results.clear();
        for (Element element : retriever) {
            results.add(element);
        }
        retriever.close();
        expectedResults.clear();
        expectedResults.add(edge1);
        assertEquals(expectedResults, results);

        op = new GetRelatedElements<>(defaultView, seeds);
        op.addOption(Constants.OPERATION_AUTHORISATIONS, AUTHS);
        // Turn off directed / undirected edges only option and check get both edge1 and edge2
        op.setIncludeEdges(IncludeEdgeType.ALL);
        retriever = new AccumuloIDWithinSetRetriever(store, op, loadIntoMemory);
        results.clear();
        for (Element element : retriever) {
            results.add(element);
        }
        retriever.close();
        expectedResults.add(edge2);
        expectedResults.add(reverseEdge);
        assertEquals(expectedResults, results);
    }

    /**
     * Tests that false positives are filtered out. It does this by explicitly finding a false positive (i.e. something
     * that matches the Bloom filter but that wasn't put into the filter) and adding that to the data, and then
     * checking that isn't returned.
     *
     * @throws gaffer.store.StoreException
     * @throws gaffer.accumulostore.key.exception.AccumuloElementConversionException
     */
    @Test
    public void testDealWithFalsePositives() throws StoreException, AccumuloElementConversionException {
        testDealWithFalsePositives(byteEntityStore);
        testDealWithFalsePositives(gaffer1KeyStore);
    }

    public void testDealWithFalsePositives(final AccumuloStore store) throws StoreException, AccumuloElementConversionException {
        testDealWithFalsePositives(true, store);
        testDealWithFalsePositives(false, store);
    }

    static void testDealWithFalsePositives(final boolean loadIntoMemory, final AccumuloStore store) throws StoreException, AccumuloElementConversionException {
        // Query for all edges in set {customer|A0, customer|A23}
        Set<EntitySeed> seeds = new HashSet<>();
        seeds.add(new EntitySeed("A0"));
        seeds.add(new EntitySeed("A23"));
        // Add a bunch of items that are not in the data to make the probability of being able to find a false
        // positive sensible.
        for (int i = 0; i < 10; i++) {
            seeds.add(new EntitySeed("abc" + i));
        }

        // Need to make sure that the Bloom filter we create has the same size and the same number of hashes as the
        // one that GraphElementsWithStatisticsWithinSetRetriever creates.
        int numItemsToBeAdded = loadIntoMemory ? seeds.size() : 20;
        if (!loadIntoMemory) {
            store.getProperties().setMaxEntriesForBatchScanner("20");
        }

        // Find something that will give a false positive
        // Need to repeat the logic used in the getGraphElementsWithStatisticsWithinSet() method.
        // Calculate sensible size of filter, aiming for false positive rate of 1 in 10000, with a maximum size of
        // maxBloomFilterToPassToAnIterator bytes.
        int size = (int) (-numItemsToBeAdded * Math.log(0.0001) / (Math.pow(Math.log(2.0), 2.0)));
        size = Math.min(size, store.getProperties().getMaxBloomFilterToPassToAnIterator());

        // Work out optimal number of hashes to use in Bloom filter based on size of set - optimal number of hashes is
        // (m/n)ln 2 where m is the size of the filter in bits and n is the number of items that will be added to the set.
        int numHashes = Math.max(1, (int) ((size / numItemsToBeAdded) * Math.log(2)));
        // Create Bloom filter and add seeds to it
        BloomFilter filter = new BloomFilter(size, numHashes, Hash.MURMUR_HASH);
        for (EntitySeed seed : seeds) {
            filter.add(new org.apache.hadoop.util.bloom.Key(store.getKeyPackage().getKeyConverter().serialiseVertexForBloomKey(seed.getVertex())));
        }

        // Test random items against it - should only have to test MAX_SIZE_BLOOM_FILTER / 2 on average before find a
        // false positive (but impose an arbitrary limit to avoid an infinite loop if there's a problem).
        int count = 0;
        int maxNumberOfTries = 50 * store.getProperties().getMaxBloomFilterToPassToAnIterator();
        while (count < maxNumberOfTries) {
            count++;
            if (filter.membershipTest(new org.apache.hadoop.util.bloom.Key(("" + count).getBytes()))) {
                break;
            }
        }
        if (count == maxNumberOfTries) {
            fail("Didn't find a false positive");
        }

        // False positive is "" + count so create an edge from seeds to that
        Edge edge = new Edge(TestGroups.EDGE, "A0", "" + count, true);
        edge.putProperty(AccumuloPropertyNames.COUNT, 1000000);
        Set<Element> elms = new HashSet<>();
        elms.add(edge);
        addElements(elms, store);
        GetElements<EntitySeed, ?> op = new GetRelatedElements<>(defaultView, seeds);
        op.addOption(Constants.OPERATION_AUTHORISATIONS, AUTHS);
        // Now query for all edges in set - shouldn't get the false positive
        AccumuloIDWithinSetRetriever retriever = new AccumuloIDWithinSetRetriever(store, op, loadIntoMemory);
        Set<Element> results = new HashSet<>();
        for (Element element : retriever) {
            results.add(element);
        }
        retriever.close();

        // Check results are as expected
        Set<Element> expectedResults = new HashSet<>();
        Element expectedElement1 = new Edge(TestGroups.EDGE, "A0", "A23", true);
        expectedElement1.putProperty(AccumuloPropertyNames.COUNT, 23);
        expectedResults.add(expectedElement1);
        Element expectedElement2 = new Entity(TestGroups.ENTITY, "A0");
        expectedElement2.putProperty(AccumuloPropertyNames.COUNT, 10000);
        expectedResults.add(expectedElement2);
        Element expectedElement3 = new Entity(TestGroups.ENTITY, "A23");
        expectedElement3.putProperty(AccumuloPropertyNames.COUNT, 23);
        expectedResults.add(expectedElement3);
        assertEquals(expectedResults, results);
    }

    /**
     * Tests that standard filtering (e.g. by summary type, or by time window, or to only receive entities) is still
     * applied.
     *
     * @throws gaffer.store.StoreException
     */
    @Test
    public void testOtherFilteringStillApplied() throws StoreException {
        testOtherFilteringStillApplied(byteEntityStore);
        testOtherFilteringStillApplied(gaffer1KeyStore);
    }

    public void testOtherFilteringStillApplied(final AccumuloStore store) throws StoreException {
        testOtherFilteringStillApplied(true, store);
        testOtherFilteringStillApplied(false, store);
    }

    static void testOtherFilteringStillApplied(final boolean loadIntoMemory, final AccumuloStore store) throws StoreException {
        // Query for all edges in set {customer|A0, customer|A23}
        Set<EntitySeed> seeds = new HashSet<>();
        seeds.add(new EntitySeed("A0"));
        seeds.add(new EntitySeed("A23"));
        GetElements<EntitySeed, ?> op = new GetRelatedElements<>(defaultView, seeds);
        op.addOption(Constants.OPERATION_AUTHORISATIONS, AUTHS);
        // Set graph to give us edges only
        op.setIncludeEntities(false);
        AccumuloIDWithinSetRetriever retriever = new AccumuloIDWithinSetRetriever(store, op, loadIntoMemory);
        Set<Element> results = new HashSet<>();
        for (Element elm : retriever) {
            results.add(elm);
        }
        retriever.close();
        Set<Element> expectedResults = new HashSet<>();
        Element expectedElement1 = new Edge(TestGroups.EDGE, "A0", "A23", true);
        expectedElement1.putProperty(AccumuloPropertyNames.COUNT, 23);
        expectedResults.add(expectedElement1);
        assertEquals(expectedResults, results);

        // Set graph to return entities only
        op = new GetRelatedElements<>(defaultView, seeds);
        op.addOption(Constants.OPERATION_AUTHORISATIONS, AUTHS);
        op.setIncludeEntities(true);
        op.setIncludeEdges(IncludeEdgeType.NONE);
        // Query for all edges in set {customer|A0, customer|A23}
        retriever = new AccumuloIDWithinSetRetriever(store, op, loadIntoMemory);
        results.clear();
        for (Element elm : retriever) {
            results.add(elm);
        }
        retriever.close();
        expectedResults.clear();
        Element expectedElement2 = new Entity(TestGroups.ENTITY, "A0");
        expectedElement2.putProperty(AccumuloPropertyNames.COUNT, 10000);
        expectedResults.add(expectedElement2);
        Element expectedElement3 = new Entity(TestGroups.ENTITY, "A23");
        expectedElement3.putProperty(AccumuloPropertyNames.COUNT, 23);
        expectedResults.add(expectedElement3);
        assertEquals(expectedResults, results);

        // Set graph to return both entities and edges again, and to only return summary type "X" (which will result
        // in no data)
        View view = new View.Builder()
                .edge("X", new ViewEdgeDefinition()).build();
        op = new GetRelatedElements<>(view, seeds);
        op.addOption(Constants.OPERATION_AUTHORISATIONS, AUTHS);
        op.setIncludeEdges(IncludeEdgeType.ALL);
        op.setIncludeEntities(true);
        retriever = new AccumuloIDWithinSetRetriever(store, op, loadIntoMemory);
        results.clear();
        int count = 0;
        for (@SuppressWarnings("unused") Element elm : retriever) {
            count++;
        }
        retriever.close();
        assertEquals(0, count);
    }

    @Test
    public void testWhenMoreElementsThanFitInBatchScanner() throws StoreException {
        testWhenMoreElementsThanFitInBatchScanner(byteEntityStore);
        testWhenMoreElementsThanFitInBatchScanner(gaffer1KeyStore);
    }

    public void testWhenMoreElementsThanFitInBatchScanner(final AccumuloStore store) throws StoreException {
        testWhenMoreElementsThanFitInBatchScanner(true, store);
        testWhenMoreElementsThanFitInBatchScanner(false, store);
    }

    static void testWhenMoreElementsThanFitInBatchScanner(final boolean loadIntoMemory, final AccumuloStore store) throws StoreException {
        store.getProperties().setMaxEntriesForBatchScanner("1");

        // Query for all edges in set {customer|A0, customer|A23}
        Set<EntitySeed> seeds = new HashSet<>();
        seeds.add(new EntitySeed("A0"));
        seeds.add(new EntitySeed("A23"));
        GetElements<EntitySeed, ?> op = new GetRelatedElements<>(defaultView, seeds);
        op.addOption(Constants.OPERATION_AUTHORISATIONS, AUTHS);
        AccumuloIDWithinSetRetriever retriever = new AccumuloIDWithinSetRetriever(store, op, loadIntoMemory);
        Set<Element> results = new HashSet<>();
        for (Element elm : retriever) {
            results.add(elm);
        }
        retriever.close();
        Set<Element> expectedResults = new HashSet<>();
        Element expectedElement1 = new Edge(TestGroups.EDGE, "A0", "A23", true);
        expectedElement1.putProperty(AccumuloPropertyNames.COUNT, 23);
        expectedResults.add(expectedElement1);
        Element expectedElement2 = new Entity(TestGroups.ENTITY, "A0");
        expectedElement2.putProperty(AccumuloPropertyNames.COUNT, 10000);
        expectedResults.add(expectedElement2);
        Element expectedElement3 = new Entity(TestGroups.ENTITY, "A23");
        expectedElement3.putProperty(AccumuloPropertyNames.COUNT, 23);
        expectedResults.add(expectedElement3);
        assertEquals(expectedResults, results);

        // Query for all edges in set {customer|A1} - there shouldn't be any, but we will get the entity for A1
        seeds.clear();
        seeds.add(new EntitySeed("A1"));
        op = new GetRelatedElements<>(defaultView, seeds);
        op.addOption(Constants.OPERATION_AUTHORISATIONS, AUTHS);
        retriever = new AccumuloIDWithinSetRetriever(store, op, loadIntoMemory);
        results.clear();
        int count = 0;
        for (Element elm : retriever) {
            count++;
            results.add(elm);
        }
        expectedResults.clear();
        expectedElement1 = new Entity(TestGroups.ENTITY, "A1");
        expectedElement1.putProperty(AccumuloPropertyNames.COUNT, 1);
        expectedResults.add(expectedElement1);
        assertEquals(1, count);
        assertEquals(expectedResults, results);

        // Query for all edges in set {customer|A1, customer|A2} - there shouldn't be any edges but will
        // get the two entities
        seeds.clear();
        seeds.add(new EntitySeed("A1"));
        seeds.add(new EntitySeed("A2"));
        op = new GetRelatedElements<>(defaultView, seeds);
        op.addOption(Constants.OPERATION_AUTHORISATIONS, AUTHS);
        retriever = new AccumuloIDWithinSetRetriever(store, op, loadIntoMemory);
        results.clear();
        count = 0;
        for (Element elm : retriever) {
            count++;
            results.add(elm);
        }
        expectedElement1 = new Entity(TestGroups.ENTITY, "A1");
        expectedElement1.putProperty(AccumuloPropertyNames.COUNT, 1);
        expectedResults.add(expectedElement1);
        expectedElement2 = new Entity(TestGroups.ENTITY, "A2");
        expectedElement2.putProperty(AccumuloPropertyNames.COUNT, 2);
        expectedResults.add(expectedElement2);
        assertEquals(2, count);
        assertEquals(expectedResults, results);
    }

    private static void setupGraph(final AccumuloStore store) {
        try {
            // Create table
            // (this method creates the table, removes the versioning iterator, and adds the SetOfStatisticsCombiner iterator,
            // and sets the age off iterator to age data off after it is more than ageOffTimeInMilliseconds milliseconds old).
            TableUtils.createTable(store);

            // Create set of GraphElementWithStatistics to gaffer.accumulostore data before adding it to the graph.
            Set<Element> data = new HashSet<>();
            // Create edges A0 -> A1, A0 -> A2, ..., A0 -> A99. Also create an Entity for each.
            Entity entity = new Entity(TestGroups.ENTITY);
            entity.setVertex("A0");
            entity.putProperty(AccumuloPropertyNames.COUNT, 10000);
            data.add(entity);
            for (int i = 1; i < 100; i++) {
                Edge edge = new Edge(TestGroups.EDGE);
                edge.setSource("A0");
                edge.setDestination("A" + i);
                edge.setDirected(true);
                edge.putProperty(AccumuloPropertyNames.COUNT, i);
                data.add(edge);
                entity = new Entity(TestGroups.ENTITY);
                entity.setVertex("A" + i);
                entity.putProperty(AccumuloPropertyNames.COUNT, i);
                data.add(entity);
            }
            addElements(data, store);
        } catch (AccumuloException | TableExistsException | IteratorSettingException e) {
            fail("Failed to set up graph in Accumulo with exception: " + e);
        }
    }

    private static void addElements(final Iterable<Element> data, final AccumuloStore store) {
        AddElements add = new AddElements(data);
        add.addOption(Constants.OPERATION_AUTHORISATIONS, AUTHS);
        try {
            store.execute(new OperationChain<>(add));
        } catch (OperationException e) {
            fail("Failed to set up graph in Accumulo with exception: " + e);
        }
    }
}
