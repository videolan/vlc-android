package de.timroes.axmlrpc;

import de.timroes.axmlrpc.serializer.SerializerHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * The ResponseParser parses the response of an XMLRPC server to an object.
 *
 * @author Tim Roes
 */
class ResponseParser {

	private static final String FAULT_CODE = "faultCode";
	private static final String FAULT_STRING = "faultString";

	/**
	 * The given InputStream must contain the xml response from an xmlrpc server.
	 * This method extract the content of it as an object.
	 *
	 * @param response The InputStream of the server response.
	 * @return The returned object.
	 * @throws XMLRPCException Will be thrown whenever something fails.
	 * @throws XMLRPCServerException Will be thrown, if the server returns an error.
	 */
	public Object parse(InputStream response, boolean debugMode) throws XMLRPCException {

		try {

			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document dom = builder.parse(response);
			if (debugMode ){
				printDocument(dom, System.out);
			}
			Element e = dom.getDocumentElement();


			// Check for root tag
			if(!e.getNodeName().equals(XMLRPCClient.METHOD_RESPONSE)) {
				throw new XMLRPCException("MethodResponse root tag is missing.");
			}

			e = XMLUtil.getOnlyChildElement(e.getChildNodes());

			if(e.getNodeName().equals(XMLRPCClient.PARAMS)) {

				e = XMLUtil.getOnlyChildElement(e.getChildNodes());

				if(!e.getNodeName().equals(XMLRPCClient.PARAM)) {
					throw new XMLRPCException("The params tag must contain a param tag.");
				}

				return getReturnValueFromElement(e);

			} else if(e.getNodeName().equals(XMLRPCClient.FAULT)) {

				@SuppressWarnings("unchecked")
				Map<String,Object> o = (Map<String,Object>)getReturnValueFromElement(e);

				throw new XMLRPCServerException((String)o.get(FAULT_STRING), (Integer)o.get(FAULT_CODE));

			}

			throw new XMLRPCException("The methodResponse tag must contain a fault or params tag.");

		} catch (Exception ex) {

			if(ex instanceof XMLRPCServerException)
				throw (XMLRPCServerException)ex;
			else
				throw new XMLRPCException("Error getting result from server.", ex);

		}

	}

	public static void printDocument(Document doc, OutputStream out) throws IOException, TransformerException {
		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer transformer = tf.newTransformer();
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
		transformer.setOutputProperty(OutputKeys.METHOD, "xml");
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

		transformer.transform(new DOMSource(doc),
				new StreamResult(new OutputStreamWriter(out, "UTF-8")));
	}

	/**
	 * This method takes an element (must be a param or fault element) and
	 * returns the deserialized object of this param tag.
	 *
	 * @param element An param element.
	 * @return The deserialized object within the given param element.
	 * @throws XMLRPCException Will be thrown when the structure of the document
	 *		doesn't match the XML-RPC specification.
	 */
	private Object getReturnValueFromElement(Element element) throws XMLRPCException {

		element = XMLUtil.getOnlyChildElement(element.getChildNodes());

		return SerializerHandler.getDefault().deserialize(element);

	}

}