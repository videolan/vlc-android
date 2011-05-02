package vlc.android;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class MediaLibraryAdapter extends ArrayAdapter<MediaItem> {
	
    public MediaLibraryAdapter(Context context, int textViewResourceId) {
		super(context, textViewResourceId);
	}

    /**
     * Display the view of a file browser item.
     */
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		View view = convertView;
		if (view == null){
			LayoutInflater inflater = (LayoutInflater) this.getContext()
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			view = inflater.inflate(R.layout.media_library_item, 
					parent, false);
		}
		
		MediaItem item = getItem(position);
		if ( item != null ) {
			TextView titleTextView = 
				(TextView)view.findViewById(R.id.ml_item_title);
			titleTextView.setText(item.getName());
		}

		return view;
	}

}

