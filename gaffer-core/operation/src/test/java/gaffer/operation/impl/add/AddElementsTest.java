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

package gaffer.operation.impl.add;

import gaffer.data.element.Edge;
import gaffer.data.element.Element;
import gaffer.data.element.Entity;
import gaffer.exception.SerialisationException;
import gaffer.jsonserialisation.JSONSerialiser;
import gaffer.operation.OperationTest;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


public class AddElementsTest implements OperationTest {
    private static final JSONSerialiser serialiser = new JSONSerialiser();
    public static final String ADD_ELEMENTS_JSON = "{\n" +
            "  \"elements\" : [ {\n" +
            "    \"class\" : \"gaffer.data.element.Entity\",\n" +
            "    \"properties\" : {\n" +
            "      \"property 1\" : \"property 1 value\"\n" +
            "    },\n" +
            "    \"group\" : \"entity type 1\",\n" +
            "    \"vertex\" : \"vertex 1\"\n" +
            "  }, {\n" +
            "    \"class\" : \"gaffer.data.element.Edge\",\n" +
            "    \"properties\" : {\n" +
            "      \"property 2\" : \"property 2 value\"\n" +
            "    },\n" +
            "    \"group\" : \"edge type 2\",\n" +
            "    \"source\" : \"source vertex 1\",\n" +
            "    \"destination\" : \"dest vertex 1\",\n" +
            "    \"directed\" : true\n" +
            "  } ]\n" +
            "}";

    @Test
    @Override
    public void shouldSerialiseAndDeserialiseOperation() throws SerialisationException {
        // Given
        final AddElements addElements = new AddElements();

        // When
        String json = new String(serialiser.serialise(addElements, true));

        // Then
        assertEquals("{ }", json);
    }

    @Test
    public void shouldSerialisePopulatedAddElementsOperation() throws IOException {
        // Given
        final List<Element> elements = new ArrayList<>();
        {
            final Entity elm1 = new Entity("entity type 1", "vertex 1");
            elm1.putProperty("property 1", "property 1 value");
            elements.add(elm1);

        }
        {
            final Edge elm2 = new Edge("edge type 2", "source vertex 1", "dest vertex 1", true);
            elm2.putProperty("property 2", "property 2 value");
            elements.add(elm2);
        }

        final AddElements addElements = new AddElements(elements);

        // When
        String json = new String(serialiser.serialise(addElements, true));

        // Then
        assertEquals(ADD_ELEMENTS_JSON, json);
    }

    @Test
    public void shouldDeserialiseAddElementsOperation() throws IOException {
        // Given

        // When
        AddElements addElements = serialiser.deserialise(ADD_ELEMENTS_JSON.getBytes(), AddElements.class);

        // Then
        final Iterator<Element> itr = addElements.getElements().iterator();

        final Entity elm1 = (Entity) itr.next();
        assertEquals("vertex 1", elm1.getVertex());
        assertEquals(1, elm1.getProperties().size());
        assertEquals("property 1 value", elm1.getProperty("property 1"));

        final Edge elm2 = (Edge) itr.next();
        assertEquals("source vertex 1", elm2.getSource());
        assertEquals("dest vertex 1", elm2.getDestination());
        assertTrue(elm2.isDirected());
        assertEquals(1, elm2.getProperties().size());
        assertEquals("property 2 value", elm2.getProperty("property 2"));

        assertFalse(itr.hasNext());
    }
}
