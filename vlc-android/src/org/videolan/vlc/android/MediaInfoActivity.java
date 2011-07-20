package org.videolan.vlc.android;

import java.nio.ByteBuffer;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

public class MediaInfoActivity extends Activity {
	public final static String TAG = "VLC/MediaInfoActivity";
	private Media mItem;
	private Bitmap mImage;
	private final static int NEW_IMAGE = 0;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.media_info);
		String path = getIntent().getExtras().getString("filePath");
		if (path == null)
			return;
		mItem = MediaLibrary.getInstance().getMediaItem(path);
				
		// set title
		TextView titleView = (TextView)findViewById(R.id.title);
		titleView.setText(mItem.getTitle());
		
		// set length
		TextView lengthView = (TextView)findViewById(R.id.length);
		lengthView.setText(Util.millisToString(mItem.getLength()));
		
		new Thread(mLoadImage).start();
		
	}
	
	@Override
	public void onBackPressed() {
		Log.e(TAG, TAG + " onBackPressed()");
	}
	
	Runnable mLoadImage = new Runnable() {	
		@Override
		public void run() {
			LibVLC mLibVlc = null;
			try {
				mLibVlc = LibVLC.getInstance();
			} catch (LibVlcException e) {
				return;
			}
			
            int width = getWindowManager().getDefaultDisplay().getHeight();
            int height = width;
            
            // Get the thumbnail.
            mImage = Bitmap.createBitmap(width, height, Config.ARGB_8888);

            byte[] b = mLibVlc.getThumbnail(mItem.getPath(), width, height);

            if (b == null) // We were not able to create a thumbnail for this item.
            	return;

            
            mImage.copyPixelsFromBuffer(ByteBuffer.wrap(b));
            int top = 0;
            for (int i = 0; i < height; i++) {
            	int pixel = mImage.getPixel(width/2, i);
            	if (pixel == 0) {
            		top = i;
            	} else {
            		break;
            	}
            }
            
            int left = 0;
            for (int i = 0; i < width; i++) {
            	int pixel = mImage.getPixel(i, height/2);
            	if (pixel == 0) {
            		left = i;
            	} else {
            		break;
            	}
            }
            
            // Cut off the transparency on the borders
            mImage = Bitmap.createBitmap(mImage, top, left, 
					(width - (2 * top)), (height - (2 * left)));
            
            mHandler.sendEmptyMessage(NEW_IMAGE);
		}
	};
	
	
	
	Handler mHandler = new Handler() {
		
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case NEW_IMAGE:
				ImageView imageView = 
					(ImageView)MediaInfoActivity.this.findViewById(R.id.image);
				imageView.setImageBitmap(mImage);
				imageView.setVisibility(ImageView.VISIBLE);
				break;
			}
		};
		
	};

}
