package org.videolan.vlc.gui.tv;

import android.content.res.Resources;
import android.os.Bundle;
import android.support.v17.leanback.app.DetailsFragment;
import android.support.v17.leanback.widget.Action;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.ClassPresenterSelector;
import android.support.v17.leanback.widget.DetailsOverviewRow;
import android.support.v17.leanback.widget.DetailsOverviewRowPresenter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.util.Log;

public class MediaItemDetailsFragment extends DetailsFragment {
	private static final String TAG = "MediaItemDetailsFragment";
	private ArrayObjectAdapter mRowsAdapter;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.i(TAG, "onCreate");
		super.onCreate(savedInstanceState);

		buildDetails();
	}

	private void buildDetails() {
		Bundle extras = getActivity().getIntent().getExtras();
		Log.d(TAG, "id "+extras.getLong("id"));
		TvMedia media = extras.getParcelable("item");
		ClassPresenterSelector selector = new ClassPresenterSelector();
		// Attach your media item details presenter to the row presenter:
		DetailsOverviewRowPresenter rowPresenter =
				new DetailsOverviewRowPresenter(new DetailsDescriptionPresenter());

		selector.addClassPresenter(DetailsOverviewRow.class, rowPresenter);
		selector.addClassPresenter(ListRow.class,
				new ListRowPresenter());
		mRowsAdapter = new ArrayObjectAdapter(selector);

		Resources res = getActivity().getResources();
		DetailsOverviewRow detailsOverview = new DetailsOverviewRow(new MediaItemDetails(media.getTitle(), media.getDescription(), "Big body"));

		// Add images and action buttons to the details view
		detailsOverview.setImageDrawable(res.getDrawable(media.getCardImageId()));
		detailsOverview.addAction(new Action(1, "Play"));
		detailsOverview.addAction(new Action(2, "Delete"));
		mRowsAdapter.add(detailsOverview);

		// Add a Related items row
		ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(
				new StringPresenter());
		listRowAdapter.add("Media Item 1");
		listRowAdapter.add("Media Item 2");
		listRowAdapter.add("Media Item 3");
		HeaderItem header = new HeaderItem(0, "Related Items", null);
		mRowsAdapter.add(new ListRow(header, listRowAdapter));

		setAdapter(mRowsAdapter);
	}

}
