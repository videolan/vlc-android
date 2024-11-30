package org.videolan.mobile.app.delegates;

import android.content.*;
import org.videolan.moviepedia.MediaScraper;
import org.videolan.resources.ACTION_CONTENT_INDEXING;
import org.videolan.resources.util.RegisterReceiverCompat;
import org.videolan.tools.AppScope;

public interface IIndexersDelegate 
{
    void setupIndexers(Context context);
}

public class IndexersDelegate extends BroadcastReceiver implements IIndexersDelegate 
{
    @Override
    public void setupIndexers(Context context) 
    {
        RegisterReceiverCompat.registerReceiverCompat(this, new IntentFilter(ACTION_CONTENT_INDEXING), false);
    }

    @Override
    public void onReceive(Context context, Intent intent) 
    {
        AppScope.launch(() -> 
        {
            MediaScraper.indexListener.onIndexingDone();
            return null; 
        });
    }
