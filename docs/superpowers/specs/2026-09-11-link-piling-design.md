# Link Piling — Design Spec

Date: 2026-09-11
Status: Approved brief, pending implementation plan
Input: shape interview (dispatch = per-link assignment, scope = whole app, arrivals = live append)

## Problem

Browser Selector is launched by Windows once per URL. When a user opens several
links at once (multi-select "open in browser", or an app firing a burst), each
link spawns its own JVM process, its own taskbar entry, and its own modal
`SelectorDialog` — stacked windows fighting for focus, each demanding a separate
decision. Rule-matched links are unaffected (they never prompt), so the pain is
concentrated exactly where the human decision is.

## Goals

1. Opening N prompt-worthy links produces exactly **one** window listing all N links.
2. Second and later processes hand their URL to the first process and exit within
   milliseconds; they never show UI.
3. Each piled link gets its **own** browser assignment; one action commits the batch.
4. The single-link case is behaviorally identical to today: instant appearance,
   digit/initial = instant launch, gone in one keystroke.
5. The pile never steals focus once open; new arrivals append live.

## Non-goals

- Piling rule-matched links: a matching rule still auto-launches silently, always
  ("decide once, then never ask again"). Only prompt-worthy links enter the pile.
- Any new user-facing setting or toggle: piling is unconditional behavior.
- Batching/queueing UI in Settings; history of piled links.
- Naming unification ("Browser Switch" vs "Browser Selector") — tracked separately in PRODUCT.md.
- Fixing critique findings beyond what pile mode requires (key-binding scoping is
  pulled in as a precondition because pile mode multiplies the risk; everything
  else stays out).

## Decisions (from the design interview)

| Decision | Choice | Consequence |
|---|---|---|
| Dispatch model | Per-link assignment | Links list + browser list; digits *assign* in pile mode instead of launching |
| Instance scope | Whole app | One process total; second `--settings` focuses the existing window |
| New arrivals | Live append, no focus steal | Rows pop in with a subtle cue; window never grabs focus mid-decision |

## Architecture

### Single-instance: socket = lock + channel

One mechanism serves as both mutex and forwarding channel: a localhost TCP socket
on a fixed, documented port (`SingleInstanceService.DEFAULT_PORT`).

**Startup flow (every process):**

```
main(args)
  └─ SingleInstanceService.tryForwardOrBind(payload)
       ├─ connect to 127.0.0.1:PORT succeeds
       │    → write payload line ("url <url>" or "settings" — type token first,
       │      space-delimited; URLs carry no raw newlines), read ack, return FORWARDED
       │      (main exits 0 immediately; no L&F, no DB init, no UI)
       ├─ connect refused
       │    → bind ServerSocket on PORT, return HOST
       │      (start accept loop on a virtual thread; every accepted line is
       │       enqueued to the Swing host via SwingUtilities.invokeLater)
       └─ bind fails (race: another host bound between connect and bind)
            → retry forward once; if that also fails, act as host with a
              randomly chosen fallback port and log loudly
```

- Payload is a single line; URLs are validated with `UrlUtils.isValidUrl` before
  sending and on receipt. Malformed lines are dropped and logged, never crash the host.
- The order of connect-then-bind already handles the common race (two links
  launched in the same tick): one wins the socket, the other forwards to it.

### Host lifecycle

- The host process lives while any window is visible, plus a **500 ms linger**
  after the last window closes, to catch near-simultaneous stragglers; then it
  exits (socket closes with it). A forwarded link received during the linger
  cancels the pending exit and opens a picker.
- If the host is gone, the next link simply becomes the new host — graceful
  degradation to today's behavior for that link.

### Arg routing in `Main`

- Forwarded payload types: a URL (pile/launch path) and `settings` (focus or open
  SettingsFrame). `--settings` maps to `settings`; a bare launch (no args) maps to
  `settings` as today.
- A link arriving while Settings is open opens the picker from the same process
  (pile rules apply normally). Settings does not swallow links.

## UI design — `SelectorDialog` evolves

The dialog gains a **pile model**: an ordered list of
`PileEntry(url, domain, assignedBrowser, private)`. Everything below is phrased
against that model.

### Single-link mode (pile size 1) — unchanged

URL line (middle-truncated, full URL on tooltip), numbered browser list,
"Always use for" + pattern field, Settings/Cancel/Open, digits and initials
launch instantly, Shift = private. No visual or keystroke changes; this remains
99% of usage and the "prompt is the product" principle holds.

### Pile mode (pile size ≥ 2)

The URL line is replaced by a **links list**; the browser list below stays
exactly as it is — it remains "the picker of record".

- **Links list rows:** number, domain-forward middle-truncated URL (existing
  helper), full URL on tooltip. Each row renders its assignment as a trailing
  chip — favicon + browser name, "└ Work profile" rendered like profile rows
  today; "(Private)" appended when the row is marked private.
- **Assignment:** selecting link row(s), then pressing a digit/initial (or
  clicking a browser row) assigns that browser to every selected link.
  **Double-clicking a browser row** is today's muscle memory preserved: it
  assigns and opens the selected links immediately (a per-link drain).
  Multi-select in the links list means one keystroke assigns many — this is
  what keeps per-link assignment fast for a 5-link burst.
- **Keyboard contract shift (pile mode only):**
  - digits/initials **assign** to the selection (they no longer launch),
  - **Enter** / **"Open all (3 of 5)"** commits the assigned links,
  - **Delete** removes the selected link(s) from the pile ("not now"),
  - **Shift held while assigning** marks the affected rows private,
  - **ESC / Cancel** dismisses the entire pile.
  With exactly one link in the pile, digits/initials still launch instantly —
  the shift happens the moment a second link arrives, with no user action.
- **Commit drains:** "Open 3 of 5" launches the 3 assigned links and keeps the
  2 unassigned rows in the dialog; the count label updates. The dialog closes
  when the user assigns + opens the rest, or cancels.
- **"Always use for":** enabled only when exactly one link is selected; the
  pattern field follows that selected link's domain. With multi-select it is
  disabled (no silent "applies to first" ambiguity).
- **Arrivals:** a new link inserts a row in arrival order (no re-sorting) with a
  brief highlight and the title counter updates ("Select Browsers — 3 links").
  Focus is never taken; `setAlwaysOnTop` behavior is unchanged.
- **Mode transition is implicit:** the single picker grows the links list when
  the second link lands. No second window, no toggle.

### Edge cases

- **Zero enabled browsers:** Open/assign disabled (existing gap, unchanged).
- **Link launch failure:** per-link; failures are collected and surfaced once
  after the drain (single dialog listing what failed), so one bad browser never
  blocks the batch.
- **Rule match while pile is open:** auto-launches silently; unrelated to the pile.
- **Very large pile:** links list scrolls comfortably to ~15 rows; browser list
  unchanged (8 visible rows max, as today). No hard cap; no truncation of the pile.
- **URL invalid on receipt:** dropped and logged; the host keeps running.

## Data flow

```
App A opens 3 links
  Windows spawns BS.exe ×3
    P1 binds socket → HOST, shows picker with link1
    P2 connects, forwards "url|link2", exits
    P3 connects, forwards "url|link3", exits
  Host enqueues link2, link3 → pile grows to 3 rows (no focus steal)
User multi-selects link2+link3, presses "2" → both assigned to Firefox (private)
User presses Enter → "Open all (2 of 3)" → both launch, rows drain
User selects link1, presses "f" → assigned to Firefox; Enter → launches; dialog closes
```

## Error handling

| Failure | Behavior |
|---|---|
| Second instance can't connect or bind | Retry forward once, then become host on fallback port; log loudly |
| Malformed forwarded payload | Drop, log, ack with error; host keeps serving |
| Host dies between connect and write | Second instance's connect fails → becomes host itself (retry path above) |
| Assigned browser vanished between assign and commit | Launch fails for that link; collected into the post-drain failure report |
| Pattern field invalid on commit | Existing validation dialog, unchanged; commit blocked for that action only |

## Testing

- **Unit (new `PileModel`):** assign, multi-assign, re-assign, drain counts,
  remove, private marking, single-link equivalence invariants (digits launch at
  size 1, assign at ≥ 2).
- **Unit (forwarding):** round-trip a payload through a real loopback socket
  against a running host stub; malformed-payload handling; connect-then-bind race
  (two threads, one socket).
- **Integration (Windows runner, CI):** spawn two real app processes back-to-back
  with URL args; assert exactly one process remains and the URL arrives in the
  first. Assert process count drops to 1 within 2 s.
- **Demo mode (any OS):** full pile flow exercised without Windows.

## Implementation footprint

| File | Change |
|---|---|
| `src/main/java/com/browserselector/Main.java` | Forward-or-host decision first; arg routing (url / settings); linger timer |
| `src/main/java/com/browserselector/service/SingleInstanceService.java` | **New.** Socket lock, forwarding protocol, host accept loop |
| `src/main/java/com/browserselector/ui/PileModel.java` | **New.** Pure pile logic (entries, assignment, drain) — no Swing |
| `src/main/java/com/browserselector/ui/SelectorDialog.java` | Links list in pile mode, assignment interactions, drain commit, arrivals |
| `src/main/java/com/browserselector/ui/SettingsFrame.java` | Expose focus-existing hook for forwarded `settings` |
| `src/test/java/...` | `PileModelTest`, `SingleInstanceForwardingTest` |

No database schema changes. No new dependencies (JNA already present, unused by
this design; plain `java.net` suffices).

## Open decision (carry into implementation)

**MSIX activation:** packaged MSIX apps are single-instanced by Windows by
default, which can swallow the second launch's command-line args before the
forwarding socket is involved. Verify during implementation with the packaged
build: launch two links and check whether both URLs arrive. If args are dropped,
the fix lives in packaging/activation (app execution alias or activation
redirection), not in this design. The jpackage app-image path — the primary
distribution in CI — is unaffected and must work regardless.
