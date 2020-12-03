package org.videolan.medialibrary;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.assertEquals;

public class ToolsTest {

    @Test
    public void testMillisToString() {
        assertEquals("3min30s", Tools.millisToString(210000, true, true, false));
        assertEquals("3min 30s ", Tools.millisToString(210000, true, true, true));
        assertEquals("3min", Tools.millisToString(180000, true, true, false));
        assertEquals("3min ", Tools.millisToString(180000, true, true, true));
        assertEquals("1h30min30s", Tools.millisToString(5430000, true, true, false));
        assertEquals("1h 30min 30s ", Tools.millisToString(5430000, true, true, true));
        assertEquals("1h30min", Tools.millisToString(5430000, true, false, false));
        assertEquals("1h 30min ", Tools.millisToString(5430000, true, false, true));
        assertEquals("1h30min", Tools.millisToString(5400000, true, true, false));
        assertEquals("17s", Tools.millisToString(17000, true, true, false));
        assertEquals("17s ", Tools.millisToString(17000, true, true, true));
        assertEquals("17s", Tools.millisToString(17000, true, false, false));
        assertEquals("17s ", Tools.millisToString(17000, true, false, true));
        assertEquals("1h30s", Tools.millisToString(3630000, true, true, false));
        assertEquals("1h 30s ", Tools.millisToString(3630000, true, true, true));
        assertEquals("-32:40", Tools.millisToString(-1960000, false, true, false));
    }

    /**
     * Perform multi-threaded testing of the duration formatting routines.
     */
    @Test
    public void testConcurrentMillisToString() throws ExecutionException, InterruptedException {
        List<Future> futureList = new ArrayList<>();
        ExecutorService exec = Executors.newFixedThreadPool(2);
        futureList.add(exec.submit(() -> {
            for (int i = 0; i < 100; i++) {
                assertEquals("3min30s", Tools.millisToString(210000, true, true, false));
                assertEquals("-32:40", Tools.millisToString(-1960000, false, true, false));
            }
        }));
        futureList.add(exec.submit(() -> {
            for (int i = 0; i < 100; i++) {
                assertEquals("3min", Tools.millisToString(180000, true, true, false));
                assertEquals("54:32:10", Tools.millisToString(196330000L, false, true, false));
            }
        }));
        for (Future f : futureList) f.get();
        exec.shutdown();
        exec.awaitTermination(10, TimeUnit.SECONDS);
    }

}