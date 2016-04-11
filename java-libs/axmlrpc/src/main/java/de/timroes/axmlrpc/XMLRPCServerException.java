package de.timroes.axmlrpc;

/**
 * This exception will be thrown if the server returns an error. It contains the
 * message and the error number returned from the server.
 *
 * @author Tim Roes
 */
public class XMLRPCServerException extends XMLRPCException {

	private int errornr;

	public XMLRPCServerException(String ex, int errnr) {
		super(ex);
		this.errornr = errnr;
	}

	/**
	 * Returns the detail message string of this throwable.
	 * It will have the server error number at the end.
	 *
	 * @return The detail message string of this error.
	 */
	@Override
	public String getMessage() {
		return super.getMessage() + " [" + errornr + "]";
	}

	/**
	 * Return the error number.
	 *
	 * @return The error number.
	 */
	public int getErrorNr() {
		return errornr;
	}

}
