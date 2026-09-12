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
