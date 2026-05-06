/*
 * (C) Copyright 2026 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Thibaud Arguillere
 *     (Code initially generated with the help of OpenCode / Claude Opus)
 */
package nuxeo.labs.bafnotification;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.CoreBulkFeature;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.ecm.core.bulk.message.BulkStatus;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

/**
 * Tests that a {@code bulkActionDone} event is fired when a bulk action completes.
 *
 * @since 2025.1
 */
@RunWith(FeaturesRunner.class)
@Features({ CoreFeature.class, CoreBulkFeature.class })
@Deploy("nuxeo.labs.baf.notification.nuxeo-labs-baf-notification-core")
@Deploy("nuxeo.labs.baf.notification.nuxeo-labs-baf-notification-core.tests:OSGI-INF/test-listener-contrib.xml")
public class TestBulkActionDoneEvent {

    @Inject
    protected CoreSession session;

    @Inject
    protected BulkService bulkService;

    @Inject
    protected TransactionalFeature txFeature;

    @Test
    public void testEventFiredOnCompletion() throws InterruptedException {
        // Clear any previously captured events
        TestBulkActionDoneListener.reset();

        // Create a document so the bulk command has something to process
        var doc = session.createDocumentModel("/", "testDoc", "File");
        doc.setPropertyValue("dc:title", "Test");
        session.createDocument(doc);
        txFeature.nextTransaction();

        // Submit a bulk setProperties command
        var command = new BulkCommand.Builder("setProperties",
                "SELECT * FROM Document WHERE ecm:isVersion = 0 AND ecm:isTrashed = 0",
                session.getPrincipal().getName())
                .repository(session.getRepositoryName())
                .param("dc:description", (Serializable) "Updated by bulk")
                .build();
        var commandId = bulkService.submit(command);

        // Wait for completion
        boolean completed = bulkService.await(commandId, Duration.ofSeconds(30));
        assertNotNull(completed);

        // The computation is async; give it a moment to fire the event
        Thread.sleep(2000);

        // Verify the event was received
        var events = TestBulkActionDoneListener.getReceivedEvents();
        assertFalse("Expected at least one bulkActionDone event", events.isEmpty());

        // Find our command's event
        var matchingEvent = events.stream()
                .filter(e -> commandId.equals(e.getContext().getProperty("commandId")))
                .findFirst()
                .orElse(null);
        assertNotNull("Expected event for command %s".formatted(commandId), matchingEvent);

        var ctx = matchingEvent.getContext();
        assertEquals("setProperties", ctx.getProperty("action"));
        assertEquals("COMPLETED", ctx.getProperty("state"));
    }

    /**
     * Test event listener that captures {@code bulkActionDone} events.
     *
     * @since 2025.1
     */
    public static class TestBulkActionDoneListener implements EventListener {

        private static final List<Event> receivedEvents = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void handleEvent(Event event) {
            if (BulkActionDoneComputation.EVENT_NAME.equals(event.getName())) {
                receivedEvents.add(event);
            }
        }

        public static List<Event> getReceivedEvents() {
            return List.copyOf(receivedEvents);
        }

        public static void reset() {
            receivedEvents.clear();
        }
    }
}
