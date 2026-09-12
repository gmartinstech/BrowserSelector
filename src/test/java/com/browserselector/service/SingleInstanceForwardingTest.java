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
