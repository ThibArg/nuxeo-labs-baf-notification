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
import static org.junit.Assert.assertTrue;

import java.io.Serializable;
import java.time.Duration;

import jakarta.inject.Inject;

import nuxeo.labs.bafnotification.TestBulkActionDoneEvent.TestBulkActionDoneListener;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.CoreBulkFeature;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

/**
 * Verifies the action-name filtering performed by {@link BAFNotificationService}.
 * <p>
 * Deploys a contribution that lists an action name that is NOT used by the test
 * command, then a second contribution that DOES match, and asserts the event is
 * only fired when an action name from the union is matched.
 *
 * @since 2025.1
 */
@RunWith(FeaturesRunner.class)
@Features({ CoreFeature.class, CoreBulkFeature.class })
@Deploy("nuxeo.labs.baf.notification.nuxeo-labs-baf-notification-core")
@Deploy("nuxeo.labs.baf.notification.nuxeo-labs-baf-notification-core.tests:OSGI-INF/test-listener-contrib.xml")
@Deploy("nuxeo.labs.baf.notification.nuxeo-labs-baf-notification-core.tests:OSGI-INF/test-filter-nomatch-contrib.xml")
public class TestBAFNotificationFiltering {

    @Inject
    protected CoreSession session;

    @Inject
    protected BulkService bulkService;

    @Inject
    protected TransactionalFeature txFeature;

    @Test
    public void testServiceHasContributions() {
        var service = Framework.getService(BAFNotificationService.class);
        assertNotNull(service);
        assertTrue(service.hasContributions());
        assertFalse(service.shouldNotify("setProperties"));
        assertTrue(service.shouldNotify("noSuchActionEverFired"));
    }

    @Test
    public void testEventNotFiredWhenActionFilteredOut() throws InterruptedException {
        TestBulkActionDoneListener.reset();

        var doc = session.createDocumentModel("/", "filterDoc1", "File");
        session.createDocument(doc);
        txFeature.nextTransaction();

        // setProperties is NOT in the deployed contribution -> no event must fire.
        var command = new BulkCommand.Builder("setProperties",
                "SELECT * FROM Document WHERE ecm:isVersion = 0 AND ecm:isTrashed = 0",
                session.getPrincipal().getName())
                .repository(session.getRepositoryName())
                .param("dc:description", (Serializable) "filtered out")
                .build();
        var commandId = bulkService.submit(command);

        bulkService.await(commandId, Duration.ofSeconds(30));
        Thread.sleep(2000);

        var noMatch = TestBulkActionDoneListener.getReceivedEvents().stream()
                .noneMatch(e -> commandId.equals(e.getContext().getProperty("commandId")));
        assertTrue("No bulkActionDone event must be fired for a filtered-out action", noMatch);
    }

    @Test
    @Deploy("nuxeo.labs.baf.notification.nuxeo-labs-baf-notification-core.tests:OSGI-INF/test-filter-match-contrib.xml")
    public void testEventFiredWhenActionMatchesUnion() throws InterruptedException {
        TestBulkActionDoneListener.reset();

        var doc = session.createDocumentModel("/", "filterDoc2", "File");
        session.createDocument(doc);
        txFeature.nextTransaction();

        var command = new BulkCommand.Builder("setProperties",
                "SELECT * FROM Document WHERE ecm:isVersion = 0 AND ecm:isTrashed = 0",
                session.getPrincipal().getName())
                .repository(session.getRepositoryName())
                .param("dc:description", (Serializable) "matched")
                .build();
        var commandId = bulkService.submit(command);

        bulkService.await(commandId, Duration.ofSeconds(30));
        Thread.sleep(2000);

        var match = TestBulkActionDoneListener.getReceivedEvents().stream()
                .filter(e -> commandId.equals(e.getContext().getProperty("commandId")))
                .findFirst()
                .orElse(null);
        assertNotNull("Expected event for command %s after union match".formatted(commandId), match);
        assertEquals("setProperties", match.getContext().getProperty("action"));
    }
}
