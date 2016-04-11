package de.timroes.axmlrpc.serializer;

import java.text.SimpleDateFormat;

import org.w3c.dom.Element;

import de.timroes.axmlrpc.XMLRPCException;
import de.timroes.axmlrpc.XMLUtil;
import de.timroes.axmlrpc.xmlcreator.XmlElement;
import fr.turri.jiso8601.Iso8601Deserializer;

/**
 *
 * @author timroes
 */
public class DateTimeSerializer implements Serializer {

	private static final String DATETIME_FORMAT = "yyyyMMdd'T'HHmmss";
	private static final SimpleDateFormat DATE_FORMATER = new SimpleDateFormat(DATETIME_FORMAT);

	@Override
	public Object deserialize(Element content) throws XMLRPCException {
		return deserialize(XMLUtil.getOnlyTextContent(content.getChildNodes()));
	}

	public Object deserialize(String dateStr) throws XMLRPCException {
		try {
			return Iso8601Deserializer.toDate(dateStr);
		} catch (Exception ex) {
			throw new XMLRPCException("Unable to parse given date.", ex);
		}
	}

	@Override
	public XmlElement serialize(Object object) {
		return XMLUtil.makeXmlTag(SerializerHandler.TYPE_DATETIME,
				DATE_FORMATER.format(object));
	}

}
