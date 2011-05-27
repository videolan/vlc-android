/**
 * LibVlcException: exceptions thrown by the native LibVLC interface
 */
package org.videolan.vlc.android;
/**
 * @author jpeg
 *
 */
public class LibVlcException extends Exception {
    private static final long serialVersionUID = -1909522348226924189L;
    
    /**
     * Create an empty error
     */
    public LibVlcException() {
        super();
    }

    /**
     * @param detailMessage
     */
    public LibVlcException(String detailMessage) {
        super(detailMessage);
    }

    /**
     * @param throwable
     */
    public LibVlcException(Throwable throwable) {
        super(throwable);
    }

    /**
     * @param detailMessage
     * @param throwable
     */
    public LibVlcException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

}
