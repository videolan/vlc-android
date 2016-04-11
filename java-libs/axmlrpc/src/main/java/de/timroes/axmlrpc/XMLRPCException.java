package de.timroes.axmlrpc;

/**
 * The exception is thrown whenever the remote procedure call fails in some point.
 *
 * @author Tim Roes
 */
public class XMLRPCException extends Exception {

	public XMLRPCException() {
		super();
	}
	
	public XMLRPCException(Exception ex) {
		super(ex);
	}

	public XMLRPCException(String ex) {
		super(ex);
	}

	public XMLRPCException(String msg, Exception ex) {
		super(msg, ex);
	}

}
