package de.timroes.axmlrpc.xmlcreator;

import java.util.ArrayList;
import java.util.List;

/**
 * An xml element within an xml tree.
 * In this case an xml element can have a text content OR a multiple amount
 * of children. The xml element itself has a name.
 *
 * @author Tim Roes
 */
public class XmlElement {

	private List<XmlElement> children = new ArrayList<XmlElement>();
	private String name;
	private String content;

	/**
	 * Create a new xml element with the given name.
	 *
	 * @param name The name of the xml element.
	 */
	public XmlElement(String name) {
		this.name = name;
	}

	/**
	 * Add a child to this xml element.
	 *
	 * @param element The child to add.
	 */
	public void addChildren(XmlElement element) {
		children.add(element);
	}

	/**
	 * Set the content of this xml tag. If the content is set the children
	 * won't be used in a string representation.
	 *
	 * @param content Content of the xml element.
	 */
	public void setContent(String content) {
		this.content = content;
	}

	/**
	 * Return a string representation of this xml element.
	 *
	 * @return String representation of xml element.
	 */
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		if(content != null && content.length() > 0) {
			builder.append("\n<").append(name).append(">")
					.append(content)
					.append("</").append(name).append(">\n");
			return builder.toString();
		} else if(children.size() > 0) {
			builder.append("\n<").append(name).append(">");
			for(XmlElement x : children) {
				builder.append(x.toString());
			}
			builder.append("</").append(name).append(">\n");
			return builder.toString();
		} else {
			builder.append("\n<").append(name).append("/>\n");
			return builder.toString();
		}
	}

}
