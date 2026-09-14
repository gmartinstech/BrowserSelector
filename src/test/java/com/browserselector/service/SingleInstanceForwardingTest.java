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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Forwarding contract over real loopback sockets, all in one JVM. */
class SingleInstanceForwardingTest {

    /** Test-only port — must never equal the production constant (a test binding the
     *  prod port would forward to a real running app), and must not collide with
     *  parallel runs. 38931 binds on the dev host, clear of the Hyper-V/WSL
     *  bind-exclusion band (~44900-48700). */
    private static final int PORT = 38931;

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
    void bindRaceFunnelIsExactlyOneHost() throws Exception {
        final int racers = 4;
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(racers);
        var outcomes = new SingleInstanceService.Outcome[racers];
        var received = new LinkedBlockingQueue<String[]>();
        var errors = new ConcurrentLinkedQueue<String>();
        var threads = new Thread[racers];
        for (int i = 0; i < racers; i++) {
            final int n = i;
            threads[i] = Thread.ofPlatform().start(() -> {
                try {
                    start.await();
                    outcomes[n] = SingleInstanceService.acquire(PORT, "url", "https://race.example/" + n,
                        (type, value) -> received.add(new String[]{type, value}));
                } catch (Exception e) {
                    errors.add("racer " + n + " threw: " + e);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "all racers must finish");
        assertTrue(errors.isEmpty(), "racers must not throw: " + errors);

        // The ladder's funnel: each loser's first connect finds no host, its
        // bind loses the race, and its one forward retry must hit the winner's
        // socket. Exactly one winner; every loser forwarded.
        int hostCount = 0, forwardCount = 0, hostIndex = -1;
        for (int i = 0; i < racers; i++) {
            if (outcomes[i] instanceof SingleInstanceService.Host) {
                hostCount++;
                hostIndex = i;
            } else if (outcomes[i] instanceof SingleInstanceService.Forwarded) {
                forwardCount++;
            }
        }
        assertEquals(1, hostCount, "exactly one racer may become host");
        assertEquals(racers - 1, forwardCount, "every loser must forward");

        // A listener is only ever wired up by a winning host's accept loop, so
        // a forwarder's listener firing is structurally impossible — the
        // one-host count above is its guard (a fail() here would throw on an
        // accept-loop thread and go unheard). The host's own payload is the
        // caller's business (Main dispatches it locally), so exactly the
        // racers - 1 forwarded URLs arrive on the host's listener — never the
        // host's own.
        var urls = new ArrayList<String>();
        for (int i = 0; i < racers - 1; i++) {
            var payload = received.poll(5, TimeUnit.SECONDS);
            assertNotNull(payload, "forwarded payload " + i + " must arrive on the host listener");
            assertEquals("url", payload[0]);
            urls.add(payload[1]);
        }
        assertNull(received.poll(300, TimeUnit.MILLISECONDS), "no payload may arrive beyond the forwarders'");
        var expected = new HashSet<String>();
        for (int i = 0; i < racers; i++) {
            expected.add("https://race.example/" + i);
        }
        assertTrue(expected.remove("https://race.example/" + hostIndex));
        assertEquals(expected, new HashSet<>(urls),
            "host listener must receive exactly the forwarders' URLs, each once");
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
