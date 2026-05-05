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

import static org.nuxeo.ecm.core.bulk.BulkServiceImpl.DONE_STREAM;
import static org.nuxeo.lib.stream.computation.AbstractComputation.INPUT_1;

import java.util.List;
import java.util.Map;

import org.nuxeo.lib.stream.computation.Topology;
import org.nuxeo.runtime.stream.StreamProcessorTopology;

/**
 * Registers a stream computation that consumes the {@code bulk/done} stream and fires a
 * {@code bulkActionDone} Nuxeo event for each completed or aborted bulk command.
 *
 * @since 2025.1
 */
public class BulkActionDoneProcessor implements StreamProcessorTopology {

    @Override
    public Topology getTopology(Map<String, String> options) {
        return Topology.builder()
                       .addComputation(BulkActionDoneComputation::new,
                               List.of(INPUT_1 + ":" + DONE_STREAM))
                       .build();
    }
}
