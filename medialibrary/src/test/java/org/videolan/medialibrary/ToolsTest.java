package org.videolan.medialibrary;

import junit.framework.TestCase;

public class ToolsTest extends TestCase {
    public void testIsArrayEmpty() throws Exception {

    }

    public void testMillisToString() throws Exception {
        assertEquals("3min30s", Tools.millisToString(210000, true, true));
        assertEquals("3min", Tools.millisToString(180000, true, true));
        assertEquals("1h30min30s", Tools.millisToString(5430000, true, true));
        assertEquals("1h30min", Tools.millisToString(5430000, true, false));
        assertEquals("1h30min", Tools.millisToString(5400000, true, true));
        assertEquals("17s", Tools.millisToString(17000, true, true));
        assertEquals("17s", Tools.millisToString(17000, true, false));
        assertEquals("1h30s", Tools.millisToString(3630000, true, true));
        assertEquals("-32:40", Tools.millisToString(-1960000, false, true));
    }

}