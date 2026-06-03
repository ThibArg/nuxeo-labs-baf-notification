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
4. By default the event is fired for **all** bulk actions (setProperties, csvExport, trash, reindex, or any custom action). You can restrict it to a specific subset by contributing to the `nuxeo.labs.baf.notification.service` extension point — see [Filtering Which Actions Trigger the Event](#filtering-which-actions-trigger-the-event)

## The `bulkActionDone` Event

### Event Name

`bulkActionDone`

### Event Properties

The `EventContext` carries the following properties (basically, the `BulkStatus`):

| Property | Type | Description |
|----------|------|-------------|
| `commandId` | `String` | The unique identifier of the bulk command |
| `action` | `String` | The bulk action name (e.g. `"setProperties"`, `"csvExport"`) |
| `username` | `String` | The user who submitted the command |
| `state` | `String` | The final state: `"COMPLETED"` or `"ABORTED"` |
| `processed` | `long` | Number of documents processed |
| `total` | `long` | Total number of documents in the command |
| `errorCount` | `long` | Number of errors encountered during processing |
| `errorCode` | `int` | Number of errors encountered during processing |
| `errorMessage` | `String` | Number of errors encountered during processing |
| `processingDurationMillis` | `long` | Number of errors encountered during processing |

## Filtering Which Actions Trigger the Event

By default the plugin fires the `bulkActionDone` event for every BAF command that completes or aborts. You can narrow it down to a specific subset of actions by contributing to the `configuration` extension point of `nuxeo.labs.baf.notification.service`.

### Semantics

- **No contribution at all** &rarr; event is fired for every action (default).
- **At least one contribution exists** &rarr; event is fired only when the action name is in the union of all contributions.
- **Multiple contributions are merged (union)**. This is intentional: a Studio project and a custom plugin can each contribute their own list and both sets are taken into account.
- Action names are matched **case-sensitively**, exactly as they appear in `BulkStatus.getAction()`.
- Filtered-out actions are silently ignored (a single `DEBUG` log is emitted in `BulkActionDoneComputation`).

### Example: restrict to a single action

```xml
<extension target="nuxeo.labs.baf.notification.service" point="configuration">
  <actions>
    <action>setProperties</action>
  </actions>
</extension>
```

### Example: union of two contributions

Plugin A contributes:

```xml
<extension target="nuxeo.labs.baf.notification.service" point="configuration">
  <actions>
    <action>setProperties</action>
  </actions>
</extension>
```

Plugin B contributes:

```xml
<extension target="nuxeo.labs.baf.notification.service" point="configuration">
  <actions>
    <action>csvExport</action>
  </actions>
</extension>
```

Effective filter: `{setProperties, csvExport}`. The event is fired for both.

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
- The event is fired **exactly once** per bulk command completion. If a listener throws an exception, the error is caught and logged, but the event is **not** retried. This guarantees that listeners will not receive duplicate events, and a misbehaving listener cannot block the stream processing
- The event is fired for **every** bulk action by default. To restrict to specific action names, contribute to the `nuxeo.labs.baf.notification.service` extension point — see [Filtering Which Actions Trigger the Event](#filtering-which-actions-trigger-the-event). You can still filter further in your listener by reading the `action` property if needed

## Use with nuxeo-labs-push-to-webui

This plugin can be combined with [nuxeo-labs-push-to-webui](https://github.com/nuxeo-sandbox/nuxeo-labs-push-to-webui) to push real-time notifications to users in Nuxeo Web UI when their asynchronous bulk action completes. See the "Use with nuxeo-labs-baf-notification" section in the [nuxeo-labs-push-to-webui README](https://github.com/nuxeo-sandbox/nuxeo-labs-push-to-webui#use-with-nuxeo-labs-baf-notification) for details.

## How to Build and Deploy

### Build and Deploy Locally

```bash
git clone https://github.com/nuxeo-sandbox/nuxeo-labs-baf-notification
cd nuxeo-labs-baf-notification
mvn clean install
```

To skip unit testing, add `-DskipTests`.

The Marketplace package is generated at:

```
nuxeo-labs-baf-notification-package/target/nuxeo-labs-baf-notification-package-{VERSION}.zip
```

Install it via `nuxeoctl`:

```bash
nuxeoctl mp-install nuxeo-labs-baf-notification-package-{VERSION}.zip
```

### Deploy from Nuxeo Marketplace

This plugin will be available as a package on the [Nuxeo Marketplace](https://connect.nuxeo.com/nuxeo/site/marketplace), you can just:

```bash
nuxeoctl mp-install nuxeo-labs-baf-notification

```

## Support

**These features are not part of the Nuxeo Production platform.**

These solutions are provided for inspiration and we encourage customers to use them as code samples and learning resources.

This is a moving project (no API maintenance, no deprecation process, etc.) If any of these solutions are found to be useful for the Nuxeo Platform in general, they will be integrated directly into the platform, not maintained here.

## License

[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html)

## About Nuxeo

Nuxeo Platform is an open source highly scalable, cloud-native, enterprise content management product with rich multimedia support, written in Java. Data can be stored in both SQL & NoSQL databases.

The development of the Nuxeo Platform is mostly done by Nuxeo employees with an open development model.

The source code, documentation, roadmap, issue tracker, testing, benchmarks are all public.

More information is available at [Hyland/Nuxeo](https://www.hyland.com/en/solutions/products/nuxeo-platform).

