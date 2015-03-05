/*****************************************************************************
 * videobrowserinterface.java
 *****************************************************************************
 * copyright © 2014-2015 vlc authors and videolan
 *
 * this program is free software; you can redistribute it and/or modify
 * it under the terms of the gnu general public license as published by
 * the free software foundation; either version 2 of the license, or
 * (at your option) any later version.
 *
 * this program is distributed in the hope that it will be useful,
 * but without any warranty; without even the implied warranty of
 * merchantability or fitness for a particular purpose.  see the
 * gnu general public license for more details.
 *
 * you should have received a copy of the gnu general public license
 * along with this program; if not, write to the free software
 * foundation, inc., 51 franklin street, fifth floor, boston ma 02110-1301, usa.
 *****************************************************************************/

package org.videolan.vlc.interfaces;

import java.util.concurrent.brokenbarrierexception;

import org.videolan.vlc.mediawrapper;

public interface ivideobrowser {

    public void resetbarrier();
    public void setitemtoupdate(mediawrapper item);
    public void await() throws interruptedexception, brokenbarrierexception;
    public void updateitem();
    public void updatelist();
    public void showprogressbar();
    public void hideprogressbar();
    public void cleartextinfo();
    public void sendtextinfo(string info, int progress, int max);
}
