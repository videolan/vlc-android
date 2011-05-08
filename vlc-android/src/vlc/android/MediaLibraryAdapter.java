package vlc.android;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class MediaLibraryAdapter extends ArrayAdapter<MediaItem> {
	public final static String TAG = "VLC/MediaLibraryAdapter";
	private List<MediaItem> mItems = new ArrayList<MediaItem>();
	
    public MediaLibraryAdapter(Context context, int textViewResourceId) {
		super(context, textViewResourceId);
	}

    @Override
	public synchronized void add(MediaItem object) {
		// TODO Auto-generated method stub
		super.add(object);
	}

    @Override
	public synchronized MediaItem getItem(int position) {
		// TODO Auto-generated method stub
		return super.getItem(position);
	}

    @Override
	public synchronized void insert(MediaItem object, int index) {
		// TODO Auto-generated method stub
		super.insert(object, index);
	}

	@Override
	public synchronized void remove(MediaItem object) {
		// TODO Auto-generated method stub
		super.remove(object);
	}
	
	@Override
	public void clear() {
		mItems.clear();
		super.clear();
	}
	
	
	public synchronized void insert(MediaItem item) {
		int position = getPosition(item);
		if (position == -1) {
			mItems.add(item);
			Collections.sort(mItems);
			position = mItems.indexOf(item);
			insert(item, position);
		} else {
			remove(item);
			insert(item, position);
		}
	}
	
	
	
	
	/**
     * Display the view of a file browser item.
     */
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		View v = convertView;
		if (v == null){
			LayoutInflater inflater = (LayoutInflater) this.getContext()
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			v = inflater.inflate(R.layout.media_library_item, 
					parent, false);
		}

		MediaItem item = getItem(position);
		ImageView tumbnail = (ImageView)v.findViewById(R.id.ml_item_thumbnail);
		TextView title = (TextView)v.findViewById(R.id.ml_item_title);
		TextView length = (TextView)v.findViewById(R.id.ml_item_length);
//		TextView format = (TextView)v.findViewById(R.id.ml_item_format);
//		TextView path = (TextView)v.findViewById(R.id.ml_item_path);
//		TextView extention = (TextView)v.findViewById(R.id.ml_item_extention);
		title.setText(item.getName());
		length.setText(item.getLenght());
//		format.setText(item.getFormat());
//		path.setText(item.getParentPath());
//		extention.setText(item.getExtention());
		tumbnail.setImageBitmap(item.getThumbnail());

		return v;
	}

	
}

