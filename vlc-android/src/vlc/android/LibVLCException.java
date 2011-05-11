/**
 * LibVlcException: exceptions thrown by the native LibVLC interface
 */
package vlc.android;

/**
 * @author jpeg
 *
 */
public class LibVLCException extends Exception {
    private static final long serialVersionUID = -1909522348226924189L;

    /**
     * Create an empty error
     */
    public LibVLCException() {
        super();
    }

    /**
     * @param detailMessage
     */
    public LibVLCException(String detailMessage) {
        super(detailMessage);
    }

    /**
     * @param throwable
     */
    public LibVLCException(Throwable throwable) {
        super(throwable);
    }

    /**
     * @param detailMessage
     * @param throwable
     */
    public LibVLCException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

}
