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
