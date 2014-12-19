/*****************************************************************************
 * TvMedia.java
 *****************************************************************************
 * Copyright © 2012-2014 VLC authors, VideoLAN and VideoLabs
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/
package org.videolan.vlc.gui.tv;

import android.os.Parcel;
import android.os.Parcelable;

public class TvMedia implements Parcelable {
	private long id;
    private String mediaUrl;
    private String title;
    private String description;
    private int bgImageId;
    private int cardImageId;
    private String bgImageUrl;
    private String cardImageUrl;

    public TvMedia(long id, String title, String description, String bgImageUrl, String cardImageUrl, String mediaUrl) {
    	this.id = id;
    	this.title = title;
    	this.description = description; 
    	this.bgImageUrl = bgImageUrl;
    	this.cardImageUrl = cardImageUrl;
    	this.mediaUrl = mediaUrl;
    }

    public TvMedia(long id, String title, String description, int bgImageId, int cardImageId, String mediaUrl) {
    	this.id = id;
    	this.title = title;
    	this.description = description;
    	this.bgImageId = bgImageId;
    	this.cardImageId = cardImageId;
    	this.mediaUrl = mediaUrl;
    }

    public long getId(){
    	return id;
    }
    
    public String getDescription(){
    	return description;
    }
    
    public String getBgImageUrl(){
    	return bgImageUrl;
    }
    
    public String getCardImageUrl(){
    	return cardImageUrl;
    }
    
    public String getVideoUrl(){
    	return mediaUrl;
    }
    
    public String getTitle(){
    	return title;
    }

    public int getBackgroundImageId() {
        return bgImageId;
    }

//    public URI getBackgroundImageURI() {
//        try {
//            Log.d("BACK MEDIA: ", bgImageUrl);
//            return new URI(getBgImageUrl());
//        } catch (URISyntaxException e) {
//            Log.d("URI exception: ", bgImageUrl);
//            return null;
//        }
//    }

    public int getCardImageId() {
    	return cardImageId;
    }

//    public URI getCardImageURI() {
//        try {
//            return new URI(getCardImageUrl());
//        } catch (URISyntaxException e) {
//            return null;
//        }
//    }

    @Override
    public String toString() {
        return "Movie{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", mediaUrl='" + mediaUrl + '\'' +
                ", backgroundImageId='" + bgImageId + '\'' +
//                ", backgroundImageURI='" + getBackgroundImageURI().toString() + '\'' +
                ", cardImageUrl='" + cardImageUrl + '\'' +
                '}';
    }

	@Override
	public int describeContents() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeLong(id);
		dest.writeString(mediaUrl);
		dest.writeString(title);
		dest.writeString(description);
		dest.writeInt(bgImageId);
		dest.writeInt(cardImageId);
		dest.writeString(bgImageUrl);
		dest.writeString(cardImageUrl);
	}
	
	public static final Parcelable.Creator<TvMedia> CREATOR
	= new Parcelable.Creator<TvMedia>() {
		public TvMedia createFromParcel(Parcel in) {
			return new TvMedia(in);
		}

		public TvMedia[] newArray(int size) {
			return new TvMedia[size];
		}
	};

	private TvMedia(Parcel in) {
		id = in.readLong();
		mediaUrl = in.readString();
		title = in.readString();
		description = in.readString();
		bgImageId = in.readInt();
		cardImageId = in.readInt();
		bgImageUrl = in.readString();
		cardImageUrl = in.readString();
	}

}
