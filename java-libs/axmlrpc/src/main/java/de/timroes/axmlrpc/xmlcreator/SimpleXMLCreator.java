package de.timroes.axmlrpc.xmlcreator;

/**
 * This is a very simple xml creator. It allows creating an xml document
 * containing multiple xml tags. No attributes are supported.
 *
 * @author Tim Roes
 */
public class SimpleXMLCreator {

	private XmlElement root;

	/**
	 * Set the root element of the xml tree.
	 *
	 * @param element The element to use as root element in this tree.
	 */
	public void setRootElement(XmlElement element) {
		this.root = element;
	}

	/**
	 * Return the string representation of the xml tree.
	 * @return String representation of the xml tree.
	 */
	@Override
	public String toString() {
		return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + root.toString();
	}

}
