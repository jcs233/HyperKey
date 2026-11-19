package io.github.hyperkey.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

/** A controlled subprocess lets us assert precisely when private bytes cross the su boundary. */
public class CommandRunnerTest {
    private static final String READY = "HYPERKEY_UID_0\n";
    static final class Session extends Process {
        final InputStream reply; final ByteArrayOutputStream sent = new ByteArrayOutputStream();
        final int exit; volatile boolean alive;
        Session(String response, int exit, boolean waiting) { reply = new ByteArrayInputStream(response.getBytes(StandardCharsets.US_ASCII)); this.exit=exit; alive=waiting; }
        public InputStream getInputStream() { return reply; } public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
        public OutputStream getOutputStream() { return sent; } public int waitFor() { alive=false; return exit; }
        public boolean waitFor(long n, TimeUnit u) { alive=false; return true; } public int exitValue() { return exit; }
        public void destroy() { alive=false; } public boolean isAlive() { return alive; }
    }
    @Test public void missingSuAndIncorrectUidNeverSendPayload() {
        assertEquals(CommandRunner.Status.ROOT_DENIED, CommandRunner.run(true,"pwd",()->true,r->{ throw new IOException(); },1000).status());
        for (String reply : new String[]{"", "2000\n", "HYPERKEY_UID_1\n"}) {
            Session s = new Session(reply,1,false);
            assertEquals(CommandRunner.Status.ROOT_DENIED,CommandRunner.run(true,"pwd",()->true,r->s,1000).status());
            assertEquals(0,s.sent.size());
        }
    }
    @Test public void waitingForRootTimesOutBeforeWritingAnything() {
        Session s = new Session("",0,true);
        assertEquals(CommandRunner.Status.TIMEOUT,CommandRunner.run(true,"pwd",()->true,r->s,40).status());
        assertFalse(s.isAlive()); assertEquals(0,s.sent.size());
    }
    @Test public void lockedOrDeletedDuringConsentCancels() {
        Session s = new Session(READY,0,false); AtomicInteger checks=new AtomicInteger();
        assertEquals(CommandRunner.Status.CANCELED,CommandRunner.run(true,"pwd",()->checks.incrementAndGet()==1,r->s,1000).status());
        assertEquals(0,s.sent.size());
    }
    @Test public void sameAuthorizedSessionReceivesOriginalUtf8Once() {
        Session s = new Session(READY,0,false); String script="echo '你好; : $x'\npwd";
        assertEquals(CommandRunner.Status.OK,CommandRunner.run(true,script,()->true,r->{assertTrue(r);return s;},1000).status());
        assertEquals(script+"\n",s.sent.toString(StandardCharsets.UTF_8));
    }
    @Test public void ordinaryCommandNeverRequestsSuAndReturnsExitCode() {
        Session s = new Session("",7,false);
        CommandRunner.Result result=CommandRunner.run(false,"exit 7",()->true,r->{assertFalse(r);return s;},1000);
        assertEquals(CommandRunner.Status.FAILED,result.status()); assertEquals(7,result.exit());
    }
}
