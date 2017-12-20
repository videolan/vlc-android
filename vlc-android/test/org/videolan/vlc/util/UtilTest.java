package org.videolan.vlc.util;


import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class UtilTest {

    @Test
    public void testInsertOrUdpate() {
        String a = "a";
        String b = "b";
        String c = "c";
        String b2 = new String(b);
        List<String> list = new ArrayList<>();
        list.add(a);
        list.add(b);
        String[] items = {c, b2 };
        Util.insertOrUdpate(list, items);
        assertEquals(list.get(1), b2);
        assertEquals(b, b2);
        assertEquals(true, list.get(1) == b2);
        assertEquals(list.get(2), c);
    }
}
