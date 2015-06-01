package org.videolan.vlc.util;

import static org.junit.Assert.*;

public class StringsTest {


    public void testGetName(){
        String path = "/home/folder/file.txt";
        assertEquals("", Strings.getName(null));
        assertEquals("", Strings.getName("/"));
        assertEquals("file.txt", Strings.getName(path));
    }

    public void testGetParent() {
        String result = Strings.getParent("");
        assertEquals("", result);

        result = Strings.getParent("/");
        assertEquals("/", result);

        result = Strings.getParent("/folder");
        assertEquals("/", result);

        result = Strings.getParent("/folder/");
        assertEquals("/", result);

        result = Strings.getParent("/folder/sub");
        assertEquals("/folder", result);

        result = Strings.getParent("/folder/sub/");
        assertEquals("/folder", result);
    }
}