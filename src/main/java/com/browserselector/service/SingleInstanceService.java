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

    /** Fixed localhost lock/forwarding port. Chosen below ~44000 to stay clear of
     *  Hyper-V/WSL dynamic bind-exclusion bands (observed ~44900-48700 on Windows 11
     *  hosts; the band moves across reboots), which would silently break binding. */
    public static final int DEFAULT_PORT = 39999;
    private static final int CONNECT_TIMEOUT_MS = 250;
    /** Bounds the ack read: without it, a silent acceptor (foreign lock on the
     *  port, or our host paused in a debugger) would block startup forever. */
    private static final int READ_TIMEOUT_MS = 1000;
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
            socket.setSoTimeout(READ_TIMEOUT_MS);
            var out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            var in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out.write(type + " " + value);
            out.newLine();
            out.flush();
            return "ok".equals(in.readLine());
        } catch (IOException e) {
            // No host answered — absent, silent, or too slow. Either way,
            // try to become one (the degrade ladder handles the rest).
            return false;
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
        int space = line.indexOf(' ');
        if (space <= 0) return null;
        var type = line.substring(0, space);
        var value = line.substring(space + 1);
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
