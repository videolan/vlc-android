package org.videolan.vlc.tests;

import android.annotation.TargetApi;
import android.os.Build;
import android.test.ActivityInstrumentationTestCase2;

import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.util.Strings;

/**
 * Created by geoffrey on 08/05/15.
 */
public class VLCTests extends ActivityInstrumentationTestCase2<MainActivity>{

//    private MainActivity mFirstTestActivity;
//
//    @TargetApi(Build.VERSION_CODES.FROYO)
//    public VLCTests(Class<MainActivity> activityClass) {
//        super(activityClass);
//    }

    @TargetApi(Build.VERSION_CODES.FROYO)
    public VLCTests(){
        super(MainActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
//        mFirstTestActivity = getActivity();
    }

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
