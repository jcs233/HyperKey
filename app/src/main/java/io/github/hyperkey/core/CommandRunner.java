package io.github.hyperkey.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Runs in the companion app only. Never send a script until the same su session proves UID 0. */
public final class CommandRunner {
    public enum Status { OK, ROOT_DENIED, TIMEOUT, FAILED, CANCELED }
    public record Result(Status status, int exit) {}
    interface Start { Process open(boolean root) throws IOException; }
    private static final byte[] READY = "HYPERKEY_UID_0\n".getBytes(StandardCharsets.US_ASCII);
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "HyperKeyCommandTimeout"); t.setDaemon(true); return t;
    });
    private CommandRunner() {}
    public static Result run(boolean root, String text, BooleanSupplier allowed) {
        return run(root, text, allowed, privileged -> {
            File sink = new File("/dev/null");
            ProcessBuilder pb = privileged ? new ProcessBuilder("su", "-c",
                    "[ \"$(/system/bin/id -u)\" = 0 ] || exit 1; printf 'HYPERKEY_UID_0\\n'; exec /system/bin/sh >/dev/null 2>&1") : new ProcessBuilder("/system/bin/sh").redirectOutput(sink);
            return pb.redirectError(sink).start();
        }, 30000);
    }
    static Result run(boolean root, String text, BooleanSupplier allowed, Start start, long timeoutMs) {
        if (text == null || text.trim().isEmpty() || text.length() > 2048 || text.indexOf(0) >= 0 || !allowed.getAsBoolean()) return new Result(Status.CANCELED,-1);
        Process p;
        try { p = start.open(root); } catch (IOException | SecurityException e) { return new Result(root ? Status.ROOT_DENIED : Status.FAILED,-1); }
        AtomicBoolean expired = new AtomicBoolean();
        ScheduledFuture<?> timer = TIMER.schedule(() -> { expired.set(true); p.destroyForcibly(); }, timeoutMs, TimeUnit.MILLISECONDS);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        boolean granted = !root;
        try (InputStream reply = p.getInputStream(); OutputStream script = p.getOutputStream()) {
            if (root) {
                for (byte expected : READY) {
                    while (reply.available() == 0) {
                        if (expired.get() || System.nanoTime() >= deadline) return new Result(Status.TIMEOUT,-1);
                        if (!p.isAlive()) return new Result(Status.ROOT_DENIED,-1);
                        Thread.sleep(15);
                    }
                    if (reply.read() != expected) return new Result(Status.ROOT_DENIED,-1);
                }
                granted = true;
            }
            if (expired.get()) return new Result(Status.TIMEOUT,-1);
            if (!allowed.getAsBoolean()) return new Result(Status.CANCELED,-1);
            script.write((text + "\n").getBytes(StandardCharsets.UTF_8)); script.close();
            if (!p.waitFor(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS) || expired.get()) return new Result(Status.TIMEOUT,-1);
            int code = p.exitValue(); return new Result(code == 0 ? Status.OK : Status.FAILED,code);
        } catch (IOException e) { return new Result(expired.get() ? Status.TIMEOUT : granted ? Status.FAILED : Status.ROOT_DENIED,-1); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return new Result(Status.CANCELED,-1); }
        finally { timer.cancel(false); if (p.isAlive()) p.destroyForcibly(); }
    }
}
