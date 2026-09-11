# Link Piling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Opening N prompt-worthy links produces exactly one picker window where each link gets its own browser assignment and one action dispatches them.

**Architecture:** One localhost socket is both the single-instance lock and the forwarding channel: the first process binds it and becomes the host; every later process writes its payload (`url <u>` or `settings`) and exits in milliseconds. The host routes payloads on the EDT: rule-matched URLs launch silently, everything else is enqueued into a pile inside `SelectorDialog` — which renders exactly like today for one link and grows a links list the moment a second link arrives.

**Tech Stack:** Java 21 (records, sealed interfaces, virtual threads), Swing + FlatLaf 3.4, JUnit 5, plain `java.net` — no new dependencies.

**Spec:** `docs/superpowers/specs/2026-09-11-link-piling-design.md` — the plan argues from the spec; executors read both.

## Global Constraints

- Java 21; records, sealed interfaces, and virtual threads are already used in this codebase and allowed.
- No new Maven dependencies; no database schema changes; SQLite/JNA untouched.
- Rule-matched links NEVER enter the pile — they always auto-launch silently (`Main.handleUrl` keeps the rule check before any pile call).
- Single-link picker behavior is unchanged: same layout, digits/initials launch instantly, Shift = private, "Always use for" as today.
- No new user-facing settings; piling is unconditional.
- UI colors come from `UIManager` tokens only (FlatLaf, theme-safe). No hardcoded `Color`.
- Demo mode (non-Windows) must keep working end to end.
- Tests: JUnit 5, run locally with `mvn test` (CI's package step passes `-DskipTests`).
- Commits: Conventional Commits, imperative subject ≤50 chars.
- The jar used for manual runs is shaded: `target/browser-selector-1.8.0.jar` (version from `pom.xml`; re-check if it bumped).

## File Structure

| File | Responsibility |
|---|---|
| `src/main/java/com/browserselector/ui/PileModel.java` (new) | Pure pile state: entries, assignment, drain. No Swing. |
| `src/main/java/com/browserselector/service/SingleInstanceService.java` (new) | Socket lock + forwarding protocol + host accept loop. Pure I/O — no Swing. |
| `src/main/java/com/browserselector/Main.java` (modify) | Forward-or-host first; payload routing; linger watchdog. |
| `src/main/java/com/browserselector/ui/SelectorDialog.java` (modify) | Links list in pile mode, enqueue entry point, assignment interactions, drained commits. |
| `src/main/java/com/browserselector/ui/SettingsFrame.java` (modify) | Focus-existing hook for forwarded `settings`; DISPOSE instead of EXIT. |
| `src/test/java/com/browserselector/ui/PileModelTest.java` (new) | Pile behavior contract. |
| `src/test/java/com/browserselector/service/SingleInstanceForwardingTest.java` (new) | Forwarding protocol over real loopback sockets, in-process. |
| `src/test/java/com/browserselector/service/SingleInstanceTwoProcessTest.java` (new) | Real two-process handoff via test driver. |
| `src/test/java/com/browserselector/service/SingleInstanceTestDriver.java` (new, test-only main) | Runs as "host" or "forward" process for the integration test. |
| `src/test/java/com/browserselector/MainTest.java` (new) | `toPayload` arg mapping. |

Existing patterns to follow: records for data (`Browser`, `UrlRule`), singleton via `DatabaseService.getInstance()`, UIManager-token colors in renderers, list cell renderers extending `DefaultListCellRenderer` (`SelectorDialog.BrowserListRenderer` is the reference).

---

### Task 1: PileModel — pure pile state

**Files:**
- Create: `src/main/java/com/browserselector/ui/PileModel.java`
- Test: `src/test/java/com/browserselector/ui/PileModelTest.java`

**Interfaces:**
- Consumes: `UrlUtils.extractDomain(String url) → String` (exists); `Browser` record (exists).
- Produces (Tasks 4–5 rely on exactly these):
  - `record Entry(String url, String domain, Browser assigned, boolean privateMode)` — `assigned == null` means unassigned.
  - `PileModel()` — empty pile.
  - `void add(String url)` — appends, extracts domain.
  - `int size()`, `List<Entry> entries()` (immutable copy, arrival order), `boolean singleLinkMode()` (`size() == 1`).
  - `void assign(List<Integer> indices, Browser browser, boolean markPrivate)` — sets `assigned` on the given rows; `markPrivate == true` also sets `privateMode`; `false` leaves the existing flag alone.
  - `void remove(List<Integer> indices)`.
  - `List<Entry> drainAssigned()` — removes and returns assigned entries in order; unassigned entries stay.
  - `int assignedCount()`.

- [ ] **Step 1: Write the failing test**

```java
package com.browserselector.ui;

import com.browserselector.model.Browser;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Contract for the link pile: add, assign, drain — and the single-link invariant. */
class PileModelTest {

    private static final Browser FIREFOX =
        new Browser("firefox", "Mozilla Firefox", Path.of("/usr/bin/firefox"));

    @Test
    void addExtractsDomainAndStartsUnassigned() {
        var pile = new PileModel();
        pile.add("https://example.org/some/page");
        assertEquals(1, pile.size());
        var entry = pile.entries().get(0);
        assertEquals("https://example.org/some/page", entry.url());
        assertEquals("example.org", entry.domain());
        assertNull(entry.assigned());
        assertFalse(entry.privateMode());
    }

    @Test
    void singleLinkModeIsExactlyOneEntry() {
        var pile = new PileModel();
        pile.add("https://a.example/1");
        assertTrue(pile.singleLinkMode());
        pile.add("https://b.example/2");
        assertFalse(pile.singleLinkMode());
        pile.remove(List.of(0, 1));
        assertFalse(pile.singleLinkMode()); // an emptied pile is not "single-link"
        assertEquals(0, pile.size());
    }

    @Test
    void assignMarksSelectedRowsOnly() {
        var pile = new PileModel();
        pile.add("https://a.example/1");
        pile.add("https://b.example/2");
        pile.add("https://c.example/3");
        pile.assign(List.of(0, 2), FIREFOX, false);
        assertEquals(FIREFOX, pile.entries().get(0).assigned());
        assertNull(pile.entries().get(1).assigned());
        assertEquals(FIREFOX, pile.entries().get(2).assigned());
        assertEquals(2, pile.assignedCount());
    }

    @Test
    void shiftMarksPrivate_plainAssignKeepsExistingFlag() {
        var pile = new PileModel();
        pile.add("https://a.example/1");
        pile.assign(List.of(0), FIREFOX, true);
        assertTrue(pile.entries().get(0).privateMode());
        // re-assign without shift: browser changes, private flag stays
        pile.assign(List.of(0), FIREFOX, false);
        assertTrue(pile.entries().get(0).privateMode());
    }

    @Test
    void removeDropsOnlySelectedRows() {
        var pile = new PileModel();
        pile.add("https://a.example/1");
        pile.add("https://b.example/2");
        pile.add("https://c.example/3");
        pile.remove(List.of(1));
        assertEquals(2, pile.size());
        assertEquals("https://a.example/1", pile.entries().get(0).url());
        assertEquals("https://c.example/3", pile.entries().get(1).url());
    }

    @Test
    void drainAssignedReturnsAssignedInOrderAndKeepsUnassigned() {
        var pile = new PileModel();
        pile.add("https://a.example/1");
        pile.add("https://b.example/2");
        pile.add("https://c.example/3");
        pile.assign(List.of(0, 2), FIREFOX, false);
        var drained = pile.drainAssigned();
        assertEquals(2, drained.size());
        assertEquals("https://a.example/1", drained.get(0).url());
        assertEquals("https://c.example/3", drained.get(1).url());
        assertEquals(1, pile.size());
        assertEquals("https://b.example/2", pile.entries().get(0).url());
        assertEquals(0, pile.assignedCount());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=PileModelTest`
Expected: FAIL — compilation error, `PileModel` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.browserselector.ui;

import com.browserselector.model.Browser;
import com.browserselector.util.UrlUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Pure state for a pile of links awaiting dispatch. No Swing.
 * Arrival order is sacred — rows never re-sort (spec: "Data flow").
 */
public final class PileModel {

    /** One piled link. assigned == null means "not yet dispatched". */
    public record Entry(String url, String domain, Browser assigned, boolean privateMode) {}

    private final List<Entry> entries = new ArrayList<>();

    public void add(String url) {
        entries.add(new Entry(url, UrlUtils.extractDomain(url), null, false));
    }

    public int size() {
        return entries.size();
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public boolean singleLinkMode() {
        return entries.size() == 1;
    }

    /**
     * Assigns the browser to the given row indices. markPrivate == true marks
     * the rows private (Shift held); false keeps each row's existing flag.
     */
    public void assign(List<Integer> indices, Browser browser, boolean markPrivate) {
        for (int index : new LinkedHashSet<>(indices)) {
            var old = entries.get(index);
            entries.set(index, new Entry(old.url(), old.domain(), browser, markPrivate || old.privateMode()));
        }
    }

    public void remove(List<Integer> indices) {
        new LinkedHashSet<>(indices).stream()
            .sorted((a, b) -> b - a)
            .forEach(entries::remove);
    }

    /** Removes and returns assigned entries in order; unassigned entries stay (drain model). */
    public List<Entry> drainAssigned() {
        var drained = entries.stream().filter(e -> e.assigned() != null).toList();
        entries.removeIf(e -> e.assigned() != null);
        return drained;
    }

    public int assignedCount() {
        return (int) entries.stream().filter(e -> e.assigned() != null).count();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=PileModelTest`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/browserselector/ui/PileModel.java src/test/java/com/browserselector/ui/PileModelTest.java
git commit -m "feat(ui): add pile model for piled links"
```

---

### Task 2: SingleInstanceService — socket lock + forwarding

**Files:**
- Create: `src/main/java/com/browserselector/service/SingleInstanceService.java`
- Test: `src/test/java/com/browserselector/service/SingleInstanceForwardingTest.java`

**Interfaces:**
- Consumes: nothing from other tasks; plain `java.net`.
- Produces (Tasks 3 and 6 rely on exactly these):
  - `static final int DEFAULT_PORT = 47517`.
  - `sealed interface Outcome permits Host, Forwarded` with `record Host()` / `record Forwarded()`.
  - `interface PayloadListener { void onPayload(String type, String value); }` — called on a **background thread**; the caller marshals to the EDT.
  - `static Outcome acquire(String type, String value, PayloadListener hostListener)` — connect to `127.0.0.1:DEFAULT_PORT` and deliver (`type`, `value`), returning `Forwarded`; or bind the port, start the accept loop, and return `Host`.
  - Wire format: one UTF-8 line `"<type> <value>"` (type token first, space-delimited; URLs carry no raw newlines), ack `"ok"` or `"err <reason>"`. Known types: `url` (non-empty value), `settings` (empty value).

- [ ] **Step 1: Write the failing test**

```java
package com.browserselector.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Forwarding contract over real loopback sockets, all in one JVM. */
class SingleInstanceForwardingTest {

    /** Test-only port — never the production constant, so parallel runs never collide. */
    private static final int PORT = 47931;

    @AfterEach
    void closeHost() throws Exception {
        SingleInstanceService.closeHostForTesting();
    }

    @Test
    void secondAcquireForwardsToHostListener() throws Exception {
        var received = new LinkedBlockingQueue<String[]>();
        var first = SingleInstanceService.acquire(PORT, "url", "https://first.example/a",
            (type, value) -> received.add(new String[]{type, value}));
        assertInstanceOf(SingleInstanceService.Host.class, first);

        var second = SingleInstanceService.acquire(PORT, "url", "https://second.example/b",
            (type, value) -> fail("forwarder's own listener must never fire"));
        assertInstanceOf(SingleInstanceService.Forwarded.class, second);

        var payload = received.poll(5, TimeUnit.SECONDS);
        assertNotNull(payload, "host listener must receive the forwarded URL");
        assertArrayEquals(new String[]{"url", "https://second.example/b"}, payload);
    }

    @Test
    void settingsPayloadRoundTrips() throws Exception {
        var received = new LinkedBlockingQueue<String[]>();
        var first = SingleInstanceService.acquire(PORT, "settings", "",
            (type, value) -> received.add(new String[]{type, value}));
        assertInstanceOf(SingleInstanceService.Host.class, first);

        var second = SingleInstanceService.acquire(PORT, "settings", "", (t, v) -> {});
        assertInstanceOf(SingleInstanceService.Forwarded.class, second);

        var payload = received.poll(5, TimeUnit.SECONDS);
        assertNotNull(payload);
        assertEquals("settings", payload[0]);
    }

    @Test
    void malformedPayloadIsRejectedAndNotDispatched() throws Exception {
        var received = new LinkedBlockingQueue<String[]>();
        var first = SingleInstanceService.acquire(PORT, "url", "https://first.example/a",
            (type, value) -> received.add(new String[]{type, value}));
        assertInstanceOf(SingleInstanceService.Host.class, first);

        try (var socket = new Socket(InetAddress.getLoopbackAddress(), PORT)) {
            var out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            var in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out.write("garbage-nonsense");
            out.newLine();
            out.flush();
            var ack = in.readLine();
            assertTrue(ack.startsWith("err"), "garbage must be acked with err, got: " + ack);
        }
        assertNull(received.poll(300, TimeUnit.MILLISECONDS), "no payload may dispatch from garbage");
    }

    @Test
    void parseAcceptsOnlyKnownTypes() {
        assertNull(SingleInstanceService.parse(null));
        assertNull(SingleInstanceService.parse("garbage"));
        assertNull(SingleInstanceService.parse("url"));            // no space, no value
        assertNull(SingleInstanceService.parse("url   "));          // blank URL
        assertNull(SingleInstanceService.parse("evil https://x"));  // unknown type
        var settings = SingleInstanceService.parse("settings ");
        assertEquals("settings", settings[0]);
        assertEquals("", settings[1]);
        var url = SingleInstanceService.parse("url https://example.org/a?b=c");
        assertEquals("url", url[0]);
        assertEquals("https://example.org/a?b=c", url[1]);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=SingleInstanceForwardingTest`
Expected: FAIL — compilation error, `SingleInstanceService` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.browserselector.service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Single-instance lock and forwarding channel in one mechanism: the first
 * process to bind the fixed port becomes the host; every later process
 * forwards its payload and exits. Spec: "Single-instance: socket = lock + channel".
 */
public final class SingleInstanceService {

    public static final int DEFAULT_PORT = 47517;
    private static final int CONNECT_TIMEOUT_MS = 250;
    private static final Logger LOG = Logger.getLogger(SingleInstanceService.class.getName());

    public sealed interface Outcome permits Host, Forwarded {}
    public record Host() implements Outcome {}
    public record Forwarded() implements Outcome {}

    /** Called on a background thread for every payload the host accepts. */
    public interface PayloadListener {
        void onPayload(String type, String value);
    }

    private static volatile ServerSocket hostSocket;

    private SingleInstanceService() {}

    public static Outcome acquire(String type, String value, PayloadListener listener) {
        return acquire(DEFAULT_PORT, type, value, listener);
    }

    static Outcome acquire(int port, String type, String value, PayloadListener listener) {
        var safeValue = value == null ? "" : value;
        if (tryForward(port, type, safeValue)) return new Forwarded();
        if (tryBind(port, listener)) return new Host();
        // Lost the bind race (a host appeared between our connect and bind): one
        // forward retry, then degrade to an ephemeral port, logged loudly (spec).
        if (tryForward(port, type, safeValue)) return new Forwarded();
        if (tryBindEphemeral(listener)) {
            LOG.warning("Lost bind race on port " + port + "; bound an ephemeral fallback port");
            return new Host();
        }
        LOG.severe("Could not forward or bind any port; acting without forwarding");
        return new Host();
    }

    private static boolean tryForward(int port, String type, String value) {
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), CONNECT_TIMEOUT_MS);
            var out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            var in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out.write(type + " " + value);
            out.newLine();
            out.flush();
            return "ok".equals(in.readLine());
        } catch (IOException e) {
            return false; // no host — exactly the signal to try to become one
        }
    }

    private static boolean tryBind(int port, PayloadListener listener) {
        try {
            var server = new ServerSocket(port, 50, InetAddress.getLoopbackAddress());
            startHost(server, listener);
            return true;
        } catch (IOException e) {
            return false; // someone else bound it between our connect and bind
        }
    }

    private static boolean tryBindEphemeral(PayloadListener listener) {
        try {
            var server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            startHost(server, listener);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void startHost(ServerSocket server, PayloadListener listener) {
        hostSocket = server;
        Thread.ofVirtual().start(() -> acceptLoop(server, listener));
    }

    private static void acceptLoop(ServerSocket server, PayloadListener listener) {
        while (!server.isClosed()) {
            try {
                var socket = server.accept();
                Thread.ofVirtual().start(() -> handleConnection(socket, listener));
            } catch (IOException e) {
                if (server.isClosed()) return;
                LOG.log(Level.WARNING, "accept failed", e);
            }
        }
    }

    private static void handleConnection(Socket socket, PayloadListener listener) {
        try (socket;
             var in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             var out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            var parsed = parse(in.readLine());
            if (parsed == null) {
                out.write("err malformed payload");
                out.newLine();
                out.flush();
                return;
            }
            out.write("ok");
            out.newLine();
            out.flush();
            listener.onPayload(parsed[0], parsed[1]);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "forwarded payload dropped", e);
        }
    }

    /** Package-private for tests: type token first, space-delimited, known types only. */
    static String[] parse(String line) {
        if (line == null) return null;
        var trimmed = line.strip();
        int space = trimmed.indexOf(' ');
        if (space <= 0) return null;
        var type = trimmed.substring(0, space);
        var value = trimmed.substring(space + 1);
        return switch (type) {
            case "settings" -> new String[]{type, value};
            case "url" -> value.isBlank() ? null : new String[]{type, value};
            default -> null;
        };
    }

    /** Test isolation: closes the host socket so another test can bind the port. */
    static void closeHostForTesting() throws IOException {
        var server = hostSocket;
        hostSocket = null;
        if (server != null) server.close();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=SingleInstanceForwardingTest`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/browserselector/service/SingleInstanceService.java src/test/java/com/browserselector/service/SingleInstanceForwardingTest.java
git commit -m "feat(instance): socket lock + payload forwarding"
```

---

### Task 3: Main host routing + Settings focus hook + linger watchdog

**Files:**
- Modify: `src/main/java/com/browserselector/Main.java` (rework `main`, add `toPayload`, `dispatch`, `handleUrl`, `startLingerWatchdog`; keep `setupTheme`, `isSystemDarkMode`, `addDemoBrowsers`, `launchBrowser` verbatim)
- Modify: `src/main/java/com/browserselector/ui/SettingsFrame.java:66` (close operation) + new static focus hook
- Test: `src/test/java/com/browserselector/MainTest.java`

**Interfaces:**
- Consumes: `SingleInstanceService.acquire(String type, String value, PayloadListener)` → `Host | Forwarded` (Task 2).
- Produces (Tasks 4–5 rely on exactly these):
  - `Main.toPayload(String[] args) → Payload` where `record Payload(String type, String value)` — `("settings", "")` for no args or `--settings`; `("url", normalizedUrl)` otherwise.
  - `Main.handleUrl(String url)` — rule match launches silently via `BrowserUtils.launch(browser, url, null)`; otherwise `new SelectorDialog(url)` (Task 4 swaps this to `SelectorDialog.enqueue(url)`).
  - `SettingsFrame.focusOrCreate()` — static; focuses the existing window or creates the only one.

- [ ] **Step 1: Write the failing test**

```java
package com.browserselector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Arg → payload mapping for the forward-or-host decision. */
class MainTest {

    @Test
    void noArgsMeansSettings() {
        var p = Main.toPayload(new String[0]);
        assertEquals("settings", p.type());
        assertEquals("", p.value());
    }

    @Test
    void settingsFlagMeansSettings() {
        var p = Main.toPayload(new String[]{"--settings"});
        assertEquals("settings", p.type());
    }

    @Test
    void validUrlPassesThrough() {
        var p = Main.toPayload(new String[]{"https://example.org/page?q=1"});
        assertEquals("url", p.type());
        assertEquals("https://example.org/page?q=1", p.value());
    }

    @Test
    void bareDomainIsNormalized() {
        var p = Main.toPayload(new String[]{"example.org"});
        assertEquals("url", p.type());
        assertEquals("https://example.org", p.value());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=MainTest`
Expected: FAIL — compilation error, `toPayload`/`Payload` do not exist.

- [ ] **Step 3: Implement**

Replace `Main.java`'s `main` and add the new members (everything not shown stays verbatim):

```java
public record Payload(String type, String value) {}

public static void main(String[] args) {
    var payload = toPayload(args);

    // Forward-or-host first: a second process must exit in milliseconds,
    // before any L&F, database, or UI work (spec: startup flow).
    var outcome = SingleInstanceService.acquire(payload.type(), payload.value(),
        (type, value) -> SwingUtilities.invokeLater(() -> dispatch(type, value)));
    if (outcome instanceof SingleInstanceService.Forwarded) {
        return; // delivered to the host; this process is done
    }

    // We are the host process.
    setupTheme();
    var db = DatabaseService.getInstance();

    // First run: scan for browsers (verbatim from the previous main)
    if (db.getAllBrowsers().isEmpty()) {
        if (IS_WINDOWS) {
            var detector = new BrowserDetector();
            var browsers = detector.detectBrowsers();
            for (var browser : browsers) {
                db.saveBrowser(browser);
            }
            var profileDetector = new ProfileDetector();
            for (var browser : browsers) {
                for (var profile : profileDetector.detectProfiles(browser)) {
                    db.saveBrowser(profile);
                }
            }
        } else {
            addDemoBrowsers(db);
        }
    }

    SwingUtilities.invokeLater(() -> dispatch(payload.type(), payload.value()));
    startLingerWatchdog();
}

/** Args to wire payload: no args or --settings opens/focuses Settings; anything else is a URL. */
static Payload toPayload(String[] args) {
    if (args.length == 0 || args[0].equals("--settings")) {
        return new Payload("settings", "");
    }
    var url = args[0];
    if (!UrlUtils.isValidUrl(url)) {
        url = UrlUtils.normalizeUrl(url);
    }
    return new Payload("url", url);
}

/** EDT-only. Routes one payload — ours at startup, or forwarded from a second process. */
private static void dispatch(String type, String value) {
    if (type.equals("settings")) {
        SettingsFrame.focusOrCreate();
        return;
    }
    handleUrl(value);
}

private static void handleUrl(String url) {
    System.out.println("[BrowserSelector] Received URL: " + url);

    // Invalid on receipt: drop and log, host keeps serving (spec: error handling)
    if (!UrlUtils.isValidUrl(url)) {
        System.out.println("[BrowserSelector] Dropped invalid URL: " + url);
        return;
    }

    var db = DatabaseService.getInstance();

    // Rule-matched links never enter the pile — decide once, never ask again.
    var matchingRule = db.findMatchingRule(url);
    if (matchingRule.isPresent()) {
        var rule = matchingRule.get();
        System.out.println("[BrowserSelector] Found matching rule: " + rule.pattern() + " -> " + rule.browserId());
        var browser = db.getBrowser(rule.browserId());
        if (browser.isPresent()) {
            System.out.println("[BrowserSelector] Launching: " + browser.get().name());
            BrowserUtils.launch(browser.get(), url, null);
            return;
        }
    }

    System.out.println("[BrowserSelector] No matching rule, showing selector dialog...");
    new SelectorDialog(url).setVisible(true); // Task 4 swaps this to SelectorDialog.enqueue(url)
}

/**
 * The host lives while any window is visible, plus a 500 ms linger for
 * near-simultaneous stragglers; a forwarded link arriving during the linger
 * opens a window and resets the clock (spec: "Host lifecycle").
 */
private static void startLingerWatchdog() {
    var lastVisible = new long[]{System.currentTimeMillis()};
    var timer = new javax.swing.Timer(150, e -> {
        boolean anyVisible = java.util.Arrays.stream(Window.getWindows()).anyMatch(Window::isVisible);
        if (anyVisible) {
            lastVisible[0] = System.currentTimeMillis();
            return;
        }
        if (System.currentTimeMillis() - lastVisible[0] > 500) {
            System.exit(0);
        }
    });
    timer.start();
}
```

New imports in `Main.java`: `com.browserselector.service.SingleInstanceService`, `java.awt.Window` (keep existing imports; `SwingUtilities`, `UrlUtils`, `BrowserUtils` are already imported).

Then in `SettingsFrame.java`:

- Line 66: `setDefaultCloseOperation(EXIT_ON_CLOSE);` → `setDefaultCloseOperation(DISPOSE_ON_CLOSE);`
  Why: under whole-app single instance, closing Settings must not kill a live picker in the same process; the linger watchdog now owns process exit (spec: whole-app scope).
- Add to the class (after the field declarations, before the constructor):

```java
private static SettingsFrame current;

/** Whole-app single instance: focus the existing Settings window, or create the only one. */
public static void focusOrCreate() {
    if (current != null && current.isDisplayable()) {
        current.setState(Frame.NORMAL);
        current.toFront();
        current.requestFocus();
        return;
    }
    current = new SettingsFrame();
    current.setVisible(true);
}
```

(`Frame` is already imported via `java.awt.*`.)

- [ ] **Step 4: Run tests and the app**

Run: `mvn test`
Expected: PASS (all suites, including the new `MainTest`).

Manual smoke (any OS — demo mode):
```bash
mvn -q package -DskipTests
java -jar target/browser-selector-1.8.0.jar --settings
```
Expected: Settings opens. Run it again in a second terminal — the first window comes to front, the second process exits immediately (its console returns at once). Close Settings: the host process exits (watchdog, ≤ ~700 ms).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/browserselector/Main.java src/main/java/com/browserselector/ui/SettingsFrame.java src/test/java/com/browserselector/MainTest.java
git commit -m "feat(instance): host process routing + linger"
```

---

### Task 4: SelectorDialog pile scaffold — enqueue, links list, arrivals

**Files:**
- Modify: `src/main/java/com/browserselector/ui/SelectorDialog.java` (extract `initUI` into builders; add pile state, `enqueue`, links list, title counter, highlight)
- Modify: `src/main/java/com/browserselector/Main.java` (one line: call `SelectorDialog.enqueue(url)`)
- Test: existing suites must keep passing; UI verified manually

**Interfaces:**
- Consumes: `PileModel` + `PileModel.Entry` (Task 1, exact names above); `Main.handleUrl` (Task 3).
- Produces (Task 5 relies on exactly these):
  - `static void enqueue(String url)` — host-side entry point for every prompt-worthy link.
  - Instance fields: `private final PileModel pile`, `private JList<PileModel.Entry> pileList`, `private static SelectorDialog current`, `private String highlightUrl`, `private boolean pileMode` (one-way: set true in `transitionToPileMode()`, never reset — a pile shrunk back to one row by Delete keeps the pile contract), and promoted fields `private JButton openBtn` (Task 5 needs the reference).
  - `private void refreshPileList()` — resets list data + title; Task 5 adds the Open-button label update here.
  - Layout builders: `buildUrlHeader()`, `buildPileHeader()`, `buildBrowserCenter()`, `buildSouth()` — NORTH swaps between the first two; CENTER and SOUTH are the existing blocks verbatim.

- [ ] **Step 1: Restructure initUI into builders (no behavior change)**

In `SelectorDialog.java`, split the body of `initUI()` into four private builders with these exact signatures, called from a new `buildContentPane()`; `initUI()` becomes:

```java
private void initUI() {
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setResizable(false);
    buildContentPane();
    setupKeyBindings();
    addGlobalKeyListener();
    centerOnScreen();
    refreshPileList();
    validatePattern();
}

/** NORTH swaps with pile mode; CENTER and SOUTH are today's blocks, verbatim. */
private void buildContentPane() {
    var panel = new JPanel(new BorderLayout(10, 10));
    panel.setBorder(new EmptyBorder(15, 15, 12, 15));
    panel.add(pile.singleLinkMode() ? buildUrlHeader() : buildPileHeader(), BorderLayout.NORTH);
    panel.add(buildBrowserCenter(), BorderLayout.CENTER);
    panel.add(buildSouth(), BorderLayout.SOUTH);
    setContentPane(panel);
    pack();
    setMinimumSize(new Dimension(400, 300));
}
```

- `buildUrlHeader()` returns the existing URL `JLabel` block (`middleTruncate(url, 64)`, tooltip, muted color) — move verbatim.
- `buildBrowserCenter()` returns the existing browser list + scroll pane block — move verbatim; promote `browserList` usage as-is.
- `buildSouth()` returns the existing south stack (hint, separator, commitment + actions) — move verbatim; promote `openBtn` to a field (Task 5 Step 3 re-wires its action by mode; until then keep `e -> launchSelected()`).

- [ ] **Step 2: Add pile state and the enqueue entry point**

New fields and static entry point:

```java
private final PileModel pile = new PileModel();
private JList<PileModel.Entry> pileList;
private static SelectorDialog current;
private String highlightUrl;
```

At the end of the constructor add `current = this;`. In the existing `windowClosed` listener add `if (current == this) current = null;` as its first statement.

```java
/** Host-side entry point for every prompt-worthy link (spec: "Arrivals"). */
public static void enqueue(String url) {
    if (current != null && current.isDisplayable()) {
        current.addPileEntry(url);
        return;
    }
    current = new SelectorDialog(url);
    current.setVisible(true);
}

private void addPileEntry(String url) {
    pile.add(url);
    if (pile.size() == 2) {
        transitionToPileMode();
    }
    highlightUrl = url;
    refreshPileList();
    var settle = new javax.swing.Timer(1200, e -> {
        highlightUrl = null;
        pileList.repaint();
    });
    settle.setRepeats(false);
    settle.start();
    // No toFront(), no requestFocus(): arrivals never steal focus (spec).
}
```

- [ ] **Step 3: Build the links list (pile NORTH)**

```java
private JScrollPane buildPileHeader() {
    pileList = new JList<>();
    pileList.setSelectionMode(ListSelectionModel.MULTI_INTERVAL_SELECTION);
    pileList.setVisibleRowCount(3);
    pileList.setCellRenderer(new PileRowRenderer());
    var scrollPane = new JScrollPane(pileList);
    scrollPane.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")));
    return scrollPane;
}

private void transitionToPileMode() {
    pileMode = true; // one-way: Delete may shrink the pile to one row, but the
                     // assign/commit contract must survive (see Task 5 routing)
    buildContentPane(); // NORTH now renders the links list instead of the URL label
}

private void refreshPileList() {
    setTitle(!pileMode ? "Select Browser"
        : "Select Browsers — " + pile.size() + (pile.size() == 1 ? " link" : " links"));
    if (pileList != null) {
        pileList.setListData(pile.entries().toArray(new PileModel.Entry[0]));
    }
}

/** Pile row: number, truncated URL, assigned-browser chip, private marker. */
private class PileRowRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value,
            int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        setBorder(new EmptyBorder(6, 10, 6, 10));
        if (value instanceof PileModel.Entry entry) {
            var text = (index + 1) + ".  " + middleTruncate(entry.url(), 58);
            if (entry.assigned() != null) {
                text += "  →  " + entry.assigned().name().trim();
                setIcon(Icons.forBrowser(entry.assigned()));
            }
            if (entry.privateMode()) {
                text += "  (Private)";
            }
            setText(text);
            if (entry.url().equals(highlightUrl) && !isSelected) {
                var highlight = UIManager.getColor("Component.infoBackground");
                if (highlight != null) {
                    setBackground(highlight);
                }
            }
        }
        return this;
    }
}
```

Then in `Main.handleUrl`, replace the last line:

```java
new SelectorDialog(url).setVisible(true); // Task 4 swaps this to SelectorDialog.enqueue(url)
```

with:

```java
SelectorDialog.enqueue(url);
```

- [ ] **Step 4: Run tests and manual verification**

Run: `mvn test`
Expected: PASS (all suites).

Manual verification (any OS, demo mode — two terminals):
```bash
mvn -q package -DskipTests
# Terminal 1 — becomes host, shows TODAY'S single-link picker (URL label, no links list)
java -jar target/browser-selector-1.8.0.jar https://example.com/one
# Terminal 2 — returns immediately (forwarded), no new window
java -jar target/browser-selector-1.8.0.jar https://example.org/two
```
Expected:
- Terminal 2 exits instantly.
- The one window's title becomes "Select Browsers — 2 links"; a links list replaces the URL label with rows `1. https://example.com/one` and `2. https://example.org/two` (second row briefly highlighted).
- The browser list, buttons, and "Always use for" row are unchanged below.
- Focus never jumps: the window does not come to front when link 2 lands.
- A third link piles as row 3 with title "— 3 links".
- ESC closes the window; the host exits (watchdog).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/browserselector/ui/SelectorDialog.java src/main/java/com/browserselector/Main.java
git commit -m "feat(ui): pile links into one selector window"
```

---

### Task 5: Pile interactions — assignment, commit/drain, delete, remember gating

**Files:**
- Modify: `src/main/java/com/browserselector/ui/SelectorDialog.java` (bindings, assignment, commit, failures, selection listener, openSettings)

**Interfaces:**
- Consumes: `PileModel.assign/remove/drainAssigned/assignedCount/entries` (Task 1); `SettingsFrame.focusOrCreate()` (Task 3); `BrowserUtils.launch(Browser, String url, boolean incognito, Component parent) → boolean` (exists — `parent == null` suppresses its own error dialog, so batch failures can be collected).
- Produces: complete pile UX. No further consumers.

- [ ] **Step 1: Route browser-list actions by mode**

Replace the double-click listener and `launchAction` so browser-list input assigns in pile mode (spec: "The keyboard contract shifts only in pile mode"):

```java
// In buildBrowserCenter(), the double-click listener becomes:
browserList.addMouseListener(new MouseAdapter() {
    @Override
    public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
            if (!pileMode) {
                launchSelected();
            } else {
                assignAndOpenSelected(); // today's muscle memory: double-click = open now
            }
        }
    }
});

private Action launchAction(int index) {
    return new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (index >= 0) {
                browserList.setSelectedIndex(index);
            }
            if (!pileMode) {
                launchSelected();
            } else {
                assignBrowserToSelection(browserList.getSelectedValue());
            }
        }
    };
}
```

- [ ] **Step 2: Add pile key bindings, assignment, commit, remove**

Call `setupPileKeyBindings()` from `transitionToPileMode()` (after `buildContentPane()`):

```java
private void setupPileKeyBindings() {
    var inputMap = pileList.getInputMap(JComponent.WHEN_FOCUSED);
    var actionMap = pileList.getActionMap();

    // Digits 1..n assign that browser row — never fire during pattern typing:
    // these live on the list (WHEN_FOCUSED), same scoping as browserList.
    for (int i = 1; i <= 9 && i <= browsers.size(); i++) {
        final int index = i - 1;
        inputMap.put(KeyStroke.getKeyStroke(Character.forDigit(i, 10)), "pile-assign-" + index);
        actionMap.put("pile-assign-" + index, assignAction(index));
    }
    // Initial letters, first match wins, deduped — same contract as browserList.
    var used = new HashSet<Character>();
    for (int i = 0; i < browsers.size(); i++) {
        char c = Character.toLowerCase(browsers.get(i).name().charAt(0));
        if (!used.add(c)) {
            continue;
        }
        final int index = i;
        inputMap.put(KeyStroke.getKeyStroke(c), "pile-assign-" + index);
        inputMap.put(KeyStroke.getKeyStroke(Character.toUpperCase(c)), "pile-assign-" + index);
        actionMap.put("pile-assign-" + index, assignAction(index));
    }
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "pile-commit");
    actionMap.put("pile-commit", new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
            commitPile();
        }
    });
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "pile-remove");
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "pile-remove");
    actionMap.put("pile-remove", new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
            removeSelected();
        }
    });
}

private Action assignAction(int index) {
    return new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
            assignBrowserToSelection(browsers.get(index));
        }
    };
}

/** Assigns the browser to the selected rows; an empty selection means the whole pile. */
private void assignBrowserToSelection(Browser browser) {
    if (browser == null || !pileMode) {
        return;
    }
    var selected = pileList.getSelectedIndices();
    if (selected.length == 0) {
        selected = new int[pile.size()];
        for (int i = 0; i < selected.length; i++) {
            selected[i] = i;
        }
    }
    var indices = new ArrayList<Integer>(selected.length);
    for (int i : selected) {
        indices.add(i);
    }
    pile.assign(indices, browser, showIncognito && shiftPressed);
    refreshPileList();
}

/** Assign + immediate dispatch for the selection (double-click on a browser row). */
private void assignAndOpenSelected() {
    assignBrowserToSelection(browserList.getSelectedValue());
    commitPile();
}

/** Open all assigned links, keep unassigned rows; collect failures, report once. */
private void commitPile() {
    var drained = pile.drainAssigned();
    var failures = new ArrayList<String>();
    for (var entry : drained) {
        // parent == null: collect here instead of one dialog per failed link
        if (!BrowserUtils.launch(entry.assigned(), entry.url(), entry.privateMode(), null)) {
            failures.add(entry.url());
        }
    }
    refreshPileList();
    if (pile.size() == 0) {
        dispose();
        return;
    }
    if (!failures.isEmpty()) {
        showLaunchFailures(failures);
    }
}

private void showLaunchFailures(List<String> failures) {
    var shown = failures.stream()
        .limit(5)
        .map(u -> middleTruncate(u, 64))
        .collect(java.util.stream.Collectors.joining("\n"));
    if (failures.size() > 5) {
        shown += "\n… and " + (failures.size() - 5) + " more";
    }
    JOptionPane.showMessageDialog(this,
        "Could not open " + failures.size() + (failures.size() == 1 ? " link" : " links") + ":\n" + shown,
        "Launch Failed",
        JOptionPane.WARNING_MESSAGE);
}

private void removeSelected() {
    var selected = pileList.getSelectedIndices();
    var indices = new ArrayList<Integer>(selected.length);
    for (int i : selected) {
        indices.add(i);
    }
    pile.remove(indices);
    highlightUrl = null;
    refreshPileList();
    if (pile.size() == 0) {
        dispose();
    }
}
```

- [ ] **Step 3: Open-button label and action, remember gating, openSettings**

In `buildSouth()`, the Open button becomes:

```java
openBtn.addActionListener(e -> {
    if (!pileMode) {
        launchSelected();
    } else {
        commitPile();
    }
});
```

(`openBtn` was promoted to a field in Task 4; it stays the default button with the accent applied.)

In `refreshPileList()`, after the list-data update add:

```java
openBtn.setText(!pileMode ? "Open" : "Open (" + pile.assignedCount() + " of " + pile.size() + ")");
```

In `buildPileHeader()`, after the renderer, add the selection listener that gates "Always use for" (spec: enabled only when exactly one link is selected, pattern follows that link):

```java
pileList.addListSelectionListener(e -> {
    if (e.getValueIsAdjusting()) {
        return;
    }
    var selected = pileList.getSelectedIndices();
    boolean one = selected.length == 1;
    rememberCheckbox.setEnabled(one);
    patternField.setEnabled(one && rememberCheckbox.isSelected());
    if (one) {
        patternField.setText(PatternMatcher.domainToPattern(pile.entries().get(selected[0]).domain()));
        validatePattern();
    }
});
```

Replace `openSettings()` — in pile mode the Settings button must not destroy the pile:

```java
private void openSettings() {
    if (pileMode) {
        SettingsFrame.focusOrCreate(); // keep the pile open behind Settings
        return;
    }
    dispose();
    SwingUtilities.invokeLater(SettingsFrame::focusOrCreate);
}
```

- [ ] **Step 4: Run tests and manual verification**

Run: `mvn test`
Expected: PASS (all suites).

Manual verification (any OS, demo mode — three quick terminals):
```bash
mvn -q package -DskipTests
java -jar target/browser-selector-1.8.0.jar https://a.example/1
java -jar target/browser-selector-1.8.0.jar https://b.example/2
java -jar target/browser-selector-1.8.0.jar https://c.example/3
```
Scenario checks, in order:
1. Press `2` with no links selected → the empty selection means the whole pile: all three rows show `→ Mozilla Firefox`; button reads `Open (3 of 3)`.
2. Click row 3, hold Shift, press `1` → row 3 alone becomes `→ Google Chrome (Private)`; button still `Open (3 of 3)` (every row has an assignment).
3. Press Enter → commit drains all three: they launch (demo browsers may fail to spawn — that exercises the failure dialog listing them, which is fine); rows disappear; dialog closes; host exits.
4. Re-pile two links. Click row 1, press `2` → only row 1 assigned; button `Open (1 of 2)`. Press Enter → row 1 launches, row 2 remains with button `Open (0 of 1)` — the drained-commit model.
5. With row 2 still there (pile shrunken to one), press `2` → row 2 gets `→ Mozilla Firefox` — an ASSIGNMENT, not an instant launch: the pile contract survives shrinking to one row.
6. Click row 2, press Delete → row removed, dialog closes (pile empty), host exits.
7. Re-pile two links. Click row 2 alone → "Always use for" enables with `*.b.example` in the pattern field. Ctrl+click row 1 too (two selected) → "Always use for" disables. Press ESC → whole pile dismissed, host exits.
8. Single-link regression: run one URL alone → identical to today: digit launches instantly, Open works, "Always use for" enabled, title "Select Browser".

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/browserselector/ui/SelectorDialog.java
git commit -m "feat(ui): per-link assignment + drained commits"
```

---

### Task 6: Two-process integration test

**Files:**
- Create: `src/test/java/com/browserselector/service/SingleInstanceTestDriver.java` (test-only main)
- Create: `src/test/java/com/browserselector/service/SingleInstanceTwoProcessTest.java`
- Test: itself

**Interfaces:**
- Consumes: `SingleInstanceService.acquire` / `Host` / `Forwarded` with `DEFAULT_PORT` (Task 2) — the exact production path, not the test-port overload.
- Produces: automated proof of the spec's "Data flow" sequence on a real OS process boundary. Runs on any OS; CI runs on `windows-latest`.

- [ ] **Step 1: Write the test driver and failing test**

```java
package com.browserselector.service;

/**
 * Test-only process for SingleInstanceTwoProcessTest. NOT production code.
 * Usage: SingleInstanceTestDriver host <url>   → binds, prints HOST_READY,
 *                                               prints "PAYLOAD <type> <value>" per receipt
 *        SingleInstanceTestDriver forward <url> → prints FORWARDED or HOST, exits
 */
public final class SingleInstanceTestDriver {

    private SingleInstanceTestDriver() {}

    public static void main(String[] args) throws Exception {
        var mode = args[0];
        var url = args[1];
        if (mode.equals("host")) {
            var outcome = SingleInstanceService.acquire("url", url,
                (type, value) -> System.out.println("PAYLOAD " + type + " " + value));
            System.out.println(outcome instanceof SingleInstanceService.Host ? "HOST_READY" : "FORWARDED");
            System.out.flush();
            Thread.sleep(8000); // keep the host alive long enough to receive forwards
        } else {
            var outcome = SingleInstanceService.acquire("url", url, (t, v) -> {});
            System.out.println(outcome instanceof SingleInstanceService.Forwarded ? "FORWARDED" : "HOST");
        }
    }
}
```

```java
package com.browserselector.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real process boundary: a host JVM and two forwarder JVMs, exactly the
 * production path (DEFAULT_PORT, acquire). Spec: "Data flow".
 */
class SingleInstanceTwoProcessTest {

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void forwarderProcessesHandPayloadsToHostProcess() throws Exception {
        var javaBin = Path.of(System.getProperty("java.home"), "bin",
            System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java").toString();
        var classpath = System.getProperty("java.class.path");

        var host = new ProcessBuilder(javaBin, "-cp", classpath,
            "com.browserselector.service.SingleInstanceTestDriver", "host", "https://host.example/one")
            .redirectErrorStream(true)
            .start();
        try {
            // Read host stdout on a background thread: a blocking .toList() would
            // wait for host exit, and then the forwarders would find no host.
            var hostLines = Collections.synchronizedList(new ArrayList<String>());
            var hostReader = new BufferedReader(
                new InputStreamReader(host.getInputStream(), StandardCharsets.UTF_8));
            var readerThread = Thread.ofVirtual().start(() -> {
                try {
                    String line;
                    while ((line = hostReader.readLine()) != null) {
                        hostLines.add(line);
                    }
                } catch (IOException ignored) {
                    // host destroyed; stream ends
                }
            });

            assertTrue(awaitLine(hostLines, "HOST_READY"), "host never became ready: " + hostLines);

            var fwdOne = runForwarder(javaBin, classpath, "https://fwd.example/two");
            var fwdTwo = runForwarder(javaBin, classpath, "https://fwd.example/three");
            assertEquals("FORWARDED", fwdOne, "first forwarder must hand off, not become host");
            assertEquals("FORWARDED", fwdTwo, "second forwarder must hand off, not become host");

            host.destroy();
            readerThread.join(TimeUnit.SECONDS.toMillis(5));

            assertTrue(hostLines.contains("PAYLOAD url https://fwd.example/two"),
                "host must receive link two: " + hostLines);
            assertTrue(hostLines.contains("PAYLOAD url https://fwd.example/three"),
                "host must receive link three: " + hostLines);
        } finally {
            host.destroy();
            host.waitFor();
        }
    }

    /** Polls until the line appears (reader thread appends concurrently) or the deadline passes. */
    private static boolean awaitLine(List<String> lines, String expected) throws InterruptedException {
        var deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (lines.contains(expected)) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }

    /** Starts a forwarder, waits for exit, returns its stdout payload (one line). */
    private static String runForwarder(String javaBin, String classpath, String url) throws Exception {
        var process = new ProcessBuilder(javaBin, "-cp", classpath,
            "com.browserselector.service.SingleInstanceTestDriver", "forward", url)
            .redirectErrorStream(true)
            .start();
        List<String> lines = new ArrayList<>();
        try (var reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        assertTrue(process.waitFor(15, TimeUnit.SECONDS), "forwarder must exit quickly");
        assertEquals(0, process.exitValue());
        return lines.isEmpty() ? "" : lines.get(lines.size() - 1).trim();
    }
}
```

- [ ] **Step 2: Run the test — it may already pass, which is also valid**

Run: `mvn test -Dtest=SingleInstanceTwoProcessTest`
Expected: PASS — Task 2 implemented the real mechanism, so this proves it across process boundaries. If it FAILS, debug the forwarding path before proceeding (a forwarder printing HOST means it lost the race to bind; investigate with `tryForward` logs).

- [ ] **Step 3: Run the full suite**

Run: `mvn test`
Expected: PASS (all suites).

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/browserselector/service/SingleInstanceTestDriver.java src/test/java/com/browserselector/service/SingleInstanceTwoProcessTest.java
git commit -m "test(instance): two-process handoff integration"
```

---

### Task 7: Docs + packaged MSIX verification probe

**Files:**
- Modify: `README.md` (Features list)
- Verify only: packaged behavior (no code expected)

**Interfaces:**
- Consumes: the completed build.
- Produces: the spec's open decision resolved with evidence, recorded in the PR description.

- [ ] **Step 1: Add the feature to README**

In `README.md`, in the `## Features` list, add after the **Incognito Mode** line:

```markdown
- **Link Piling** - Links opened together pile into one picker window, each with its own browser choice; one action opens them all
```

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: link piling usage"
```

- [ ] **Step 3: Run the MSIX args probe (spec open decision)**

The spec flags that packaged MSIX apps are single-instanced by Windows by default and may swallow the second launch's command-line args. Verify with evidence:

1. Build the app-image per README's jpackage instructions (with the `win.norestart=true` cfg patch), then build the MSIX from `packaging/msix/` per its `AppxManifest.xml` setup.
2. Install and register: `Add-AppxPackage <msix path>`, then run the app once with `--settings`, click "Register as Default Browser", and complete Windows' prompts.
3. From a plain terminal, fire two links back-to-back:
   ```powershell
   Start-Process "https://probe.example/one"; Start-Process "https://probe.example/two"
   ```
4. Expected: one picker window, two rows. Record the result.
5. If instead only one URL arrives (or the second launch silently vanishes): do NOT fix it in this feature — record the finding (packaging/activation work, out of scope per the spec) and note that the jpackage app-image path, the primary distribution, is unaffected. The feature still ships for app-image users.

- [ ] **Step 4: Final full-suite check before PR**

Run: `mvn clean test`
Expected: PASS — then request code review per the normal workflow.
