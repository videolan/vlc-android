package vlc.android;

import java.io.File;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.CompoundButton.OnCheckedChangeListener;



public class BrowserAdapter extends ArrayAdapter<File> {

	public BrowserAdapter(Context context, int textViewResourceId) {
		super(context, textViewResourceId);
	}
	
	
	
    @Override
	public synchronized void add(File object) {
		super.add(object);
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
			view = inflater.inflate(R.layout.browser_item, 
					parent, false);
		}
		
		final File file = getItem(position);
		final DatabaseManager dbManager = DatabaseManager.getInstance();

		if ( file != null ) {
			TextView dirTextView = 
				(TextView)view.findViewById(R.id.browser_item_dir);
			dirTextView.setText(file.getName());
			CheckBox dirCheckBox = 
				(CheckBox)view.findViewById(R.id.browser_item_selected);

			dirCheckBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
				
				public void onCheckedChanged(CompoundButton buttonView,
						boolean isChecked) {
					if (isChecked) {
						dbManager.addMediaDir(file.getPath());
					} else {
						dbManager.removeMediaDir(file.getPath());
					}
					
				}
			});
			
			dirCheckBox.setChecked(dbManager.mediaDirExists(file.getPath()));


		}

		return view;
	}

}
