package de.timroes.axmlrpc;

import de.timroes.axmlrpc.xmlcreator.XmlElement;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This class provides some utility methods for the use with the Java DOM parser.
 *
 * @author Tim Roes
 */
public class XMLUtil {

	/**
	 * Returns the only child element in a given NodeList.
	 * Will throw an error if there is more then one child element or any other
	 * child that is not an element or an empty text string (whitespace are normal).
	 *
	 * @param list A NodeList of children nodes.
	 * @return The only child element in the given node list.
	 * @throws XMLRPCException Will be thrown if there is more then one child element
	 * 		except empty text nodes.
	 */
	public static Element getOnlyChildElement(NodeList list) throws XMLRPCException {

		Element e = null;
		Node n;
		for(int i = 0; i < list.getLength(); i++) {
			n = list.item(i);
			// Strip only whitespace text elements and comments
			if((n.getNodeType() == Node.TEXT_NODE
						&& n.getNodeValue().trim().length() <= 0)
					|| n.getNodeType() == Node.COMMENT_NODE)
				continue;

			// Check if there is anything else than an element node.
			if(n.getNodeType() != Node.ELEMENT_NODE) {
				throw new XMLRPCException("Only element nodes allowed.");
			}

			// If there was already an element, throw exception.
			if(e != null) {
				throw new XMLRPCException("Element has more than one children.");
			}

			e = (Element)n;

		}

		return e;

	}

	/**
	 * Returns the text node from a given NodeList. If the list contains
	 * more then just text nodes, an exception will be thrown.
	 *
	 * @param list The given list of nodes.
	 * @return The text of the given node list.
	 * @throws XMLRPCException Will be thrown if there is more than just one
	 *		text node within the list.
	 */
	public static String getOnlyTextContent(NodeList list) throws XMLRPCException {

		StringBuilder builder = new StringBuilder();
		Node n;

		for(int i = 0; i < list.getLength(); i++) {
			n = list.item(i);

			// Skip comments inside text tag.
			if(n.getNodeType() == Node.COMMENT_NODE) {
				continue;
			}

			if(n.getNodeType() != Node.TEXT_NODE) {
				throw new XMLRPCException("Element must contain only text elements.");
			}

			builder.append(n.getNodeValue());

		}

		return builder.toString();

	}

	/**
	 * Checks if the given {@link NodeList} contains a child element.
	 *
	 * @param list The {@link NodeList} to check.
	 * @return Whether the {@link NodeList} contains children.
	 */
	public static boolean hasChildElement(NodeList list) {
		
		Node n;

		for(int i = 0; i < list.getLength(); i++) {
			n = list.item(i);

			if(n.getNodeType() == Node.ELEMENT_NODE) {
				return true;
			}
		}

		return false;
		
	}

	/**
	 * Creates an xml tag with a given type and content.
	 *
	 * @param type The type of the xml tag. What will be filled in the <..>.
	 * @param content The content of the tag.
	 * @return The xml tag with its content as a string.
	 */
	public static XmlElement makeXmlTag(String type, String content) {
		XmlElement xml = new XmlElement(type);
		xml.setContent(content);
		return xml;
	}

}