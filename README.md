# Nuxeo Labs BAF Notification

A Nuxeo plugin that fires a Nuxeo event when a Bulk Action Framework (BAF) command completes or aborts.

## Why This Plugin Exists

The Nuxeo Bulk Action Framework (BAF) processes documents at scale using stream-based computations. When a bulk command finishes (whether successfully or by being aborted), the framework persists the final status in a key-value store and writes it to an internal `bulk/done` stream — but it does **not** fire any Nuxeo event.

This means there is no built-in way to reactively listen for bulk action completion using standard Nuxeo event listeners. Callers must poll via `BulkService.await()` or `BulkService.getStatus()`.

This plugin bridges that gap by consuming the `bulk/done` stream and firing a standard Nuxeo event, allowing any code to react to bulk action completion using the familiar `EventListener` pattern.

## How It Works

1. The plugin registers a **stream computation** (`BulkActionDoneComputation`) that consumes the `bulk/done` stream
2. The `bulk/done` stream already receives the final `BulkStatus` for every bulk command that completes or aborts — this is built into Nuxeo's `BulkStatusComputation`
3. For each record on that stream, the computation decodes the `BulkStatus` and fires a synchronous **`bulkActionDone`** Nuxeo event via `EventService`
4. The event is fired for **all** bulk actions (setProperties, csvExport, trash, reindex, or any custom action)

## The `bulkActionDone` Event

### Event Name

`bulkActionDone`

### Event Properties

The `EventContext` carries the following properties:

| Property | Type | Description |
|----------|------|-------------|
| `commandId` | `String` | The unique identifier of the bulk command |
| `action` | `String` | The bulk action name (e.g. `"setProperties"`, `"csvExport"`) |
| `username` | `String` | The user who submitted the command |
| `state` | `String` | The final state: `"COMPLETED"` or `"ABORTED"` |
| `processed` | `long` | Number of documents processed |
| `total` | `long` | Total number of documents in the command |
| `errorCount` | `long` | Number of errors encountered during processing |

## How to Listen for the Event

Register an `EventListener` in your own plugin:

### 1. Create the listener class

```java
package com.example;

import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventListener;

public class MyBulkActionDoneListener implements EventListener {

    @Override
    public void handleEvent(Event event) {
        var ctx = event.getContext();
        var commandId = (String) ctx.getProperty("commandId");
        var action = (String) ctx.getProperty("action");
        var state = (String) ctx.getProperty("state");
        var processed = (long) ctx.getProperty("processed");
        var total = (long) ctx.getProperty("total");
        var errorCount = (long) ctx.getProperty("errorCount");

        // React to the bulk action completion
        if ("COMPLETED".equals(state) && "setProperties".equals(action)) {
            // do something
        }
    }
}
```

### 2. Register the listener via XML contribution

```xml
<?xml version="1.0"?>
<component name="com.example.my-bulk-listener">

  <extension target="org.nuxeo.ecm.core.event.EventServiceComponent" point="listener">
    <listener name="myBulkActionDoneListener"
        class="com.example.MyBulkActionDoneListener">
      <event>bulkActionDone</event>
    </listener>
  </extension>

</component>
```

### 3. Reference the XML in your MANIFEST.MF

```
Nuxeo-Component: OSGI-INF/my-bulk-listener-contrib.xml
```

## Important Notes

- The event is fired **asynchronously relative to the bulk command** — it is triggered when the stream computation processes the `bulk/done` record, which may be slightly after the command status transitions to `COMPLETED`/`ABORTED` in the key-value store
- The event itself is fired **synchronously** within the computation — your listener runs inline
- The event is fired for **every** bulk action, not just specific ones. Filter by the `action` property in your listener if needed
- This plugin does **not** modify the Nuxeo source code — it hooks into the existing `bulk/done` stream infrastructure

## Build

```bash
# Full build with tests
mvn clean install

# Build without tests
mvn clean install -DskipTests

# Build only the core module
mvn clean install -pl nuxeo-labs-baf-notification-core
```

## Install

Install the marketplace package `nuxeo-labs-baf-notification-package/target/nuxeo-labs-baf-notification-package-*.zip` via `nuxeoctl mp-install` or the Admin Center.

## License

Apache License, Version 2.0
