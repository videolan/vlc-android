package org.videolan.medialibrary;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

    @Test
    public void testCleanupArray() {
        // Test with null
        assertNull(Tools.cleanupArray(null));

        // Test with empty array
        String[] empty = new String[0];
        assertSame(empty, Tools.cleanupArray(empty));

        // Test with no nulls - should return same instance
        String[] noNulls = {"a", "b", "c"};
        assertSame(noNulls, Tools.cleanupArray(noNulls));

        // Test with mixed nulls
        String[] mixed = {"a", null, "b", null, "c"};
        String[] expectedMixed = {"a", "b", "c"};
        assertArrayEquals(expectedMixed, Tools.cleanupArray(mixed));

        // Test with only nulls
        String[] onlyNulls = {null, null};
        assertEquals(0, Tools.cleanupArray(onlyNulls).length);

        // Test with different types to ensure reflection works
        Integer[] ints = {1, null, 2};
        Integer[] expectedInts = {1, 2};
        assertArrayEquals(expectedInts, Tools.cleanupArray(ints));
    }
}
