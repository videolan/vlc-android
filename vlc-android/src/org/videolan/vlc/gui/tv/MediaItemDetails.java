package org.videolan.vlc.gui.tv;

public class MediaItemDetails {

	private String title, subTitle, body;

	public MediaItemDetails(String title, String subTitle, String body) {
		this.title = title;
		this.subTitle = subTitle;
		this.body = body;
	}

	public String getTitle() {
		return title;
	}

	public String getSubTitle() {
		return subTitle;
	}

	public String getBody() {
		return body;
	}
}
