package de.timroes.axmlrpc.serializer;

import de.timroes.axmlrpc.XMLRPCClient;
import de.timroes.axmlrpc.XMLRPCException;
import de.timroes.axmlrpc.XMLRPCRuntimeException;
import de.timroes.axmlrpc.XMLUtil;
import de.timroes.axmlrpc.xmlcreator.XmlElement;
import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import org.w3c.dom.Element;

/**
 * The serializer handler serializes and deserialized objects.
 * It takes an object, determine its type and let the responsible handler serialize it.
 * For deserialization it looks at the xml tag around the element.
 * The class is designed as a kind of singleton, so it can be accessed from anywhere in
 * the library.
 *
 * @author Tim Roes
 */
public class SerializerHandler {

	public static final String TYPE_STRING = "string";
	public static final String TYPE_BOOLEAN = "boolean";
	public static final String TYPE_INT = "int";
	public static final String TYPE_INT2 = "i4";
	public static final String TYPE_LONG = "i8";
	public static final String TYPE_DOUBLE = "double";
	public static final String TYPE_DATETIME = "dateTime.iso8601";
	public static final String TYPE_STRUCT = "struct";
	public static final String TYPE_ARRAY = "array";
	public static final String TYPE_BASE64 = "base64";
	public static final String TYPE_NULL = "nil";

	private static SerializerHandler instance;

	/**
	 * Initialize the serialization handler. This method must be called before
	 * the get method returns any object.
	 *
	 * @param flags The flags that has been set in the XMLRPCClient.
	 * @see XMLRPCClient
	 */
	public static void initialize(int flags) {
		instance = new SerializerHandler(flags);
	}

	/**
	 * Return the instance of the SerializerHandler.
	 * It must have been initialized with initialize() before.
	 *
	 * @return The instance of the SerializerHandler.
	 */
	public static SerializerHandler getDefault() {
		if(instance == null) {
			throw new XMLRPCRuntimeException("The SerializerHandler has not been initialized.");
		}
		return instance;
	}

	private StringSerializer string;
	private BooleanSerializer bool = new BooleanSerializer();
	private IntSerializer integer = new IntSerializer();
	private LongSerializer long8 = new LongSerializer();
	private StructSerializer struct = new StructSerializer();
	private DoubleSerializer floating = new DoubleSerializer();
	private DateTimeSerializer datetime = new DateTimeSerializer();
	private ArraySerializer array = new ArraySerializer();
	private Base64Serializer base64 = new Base64Serializer();
	private NullSerializer nil = new NullSerializer();
	
	private int flags;

	/**
	 * Generates the SerializerHandler.
	 * This method can only called from within the class (the initialize method).
	 *
	 * @param flags The flags to use.
	 */
	private SerializerHandler(int flags) {
		this.flags = flags;
		string = new StringSerializer(
			(flags & XMLRPCClient.FLAGS_NO_STRING_ENCODE) == 0,
			(flags & XMLRPCClient.FLAGS_NO_STRING_DECODE) == 0
		);
	}

	/**
	 * Deserializes an incoming xml element to an java object.
	 * The xml element must be the value element around the type element.
	 * The type of the returning object depends on the type tag.
	 *
	 * @param element An type element from within a value tag.
	 * @return The deserialized object.
	 * @throws XMLRPCException Will be thrown whenever an error occurs.
	 */
	public Object deserialize(Element element) throws XMLRPCException {

		if(!XMLRPCClient.VALUE.equals(element.getNodeName())) {
			throw new XMLRPCException("Value tag is missing around value.");
		}
		
		if(!XMLUtil.hasChildElement(element.getChildNodes())) {
			// Value element doesn't contain a child element
			if((flags & XMLRPCClient.FLAGS_DEFAULT_TYPE_STRING) != 0) {
				return string.deserialize(element);
			} else {
				throw new XMLRPCException("Missing type element inside of value element.");
			}
		}
			
		// Grep type element from inside value element
		element = XMLUtil.getOnlyChildElement(element.getChildNodes());

		Serializer s = null;

		String type;

		// If FLAGS_IGNORE_NAMESPACE has been set, only use local name.
		if((flags & XMLRPCClient.FLAGS_IGNORE_NAMESPACES) != 0) {
			type = element.getLocalName() == null ? element.getNodeName() : element.getLocalName();
		} else {
			type = element.getNodeName();
		}

		if((flags & XMLRPCClient.FLAGS_NIL) != 0 && TYPE_NULL.equals(type)) {
			s = nil;
		} else if(TYPE_STRING.equals(type)) {
			s = string;
		} else if(TYPE_BOOLEAN.equals(type)) {
			s = bool;
		} else if(TYPE_DOUBLE.equals(type)) {
			s = floating;
		} else if (TYPE_INT.equals(type) || TYPE_INT2.equals(type)) {
			s = integer;
		} else if(TYPE_DATETIME.equals(type)) {
			s = datetime;
		} else if (TYPE_LONG.equals(type)) {
			if((flags & XMLRPCClient.FLAGS_8BYTE_INT) != 0) {
				s = long8;
			} else {
				throw new XMLRPCException("8 byte integer is not in the specification. "
						+ "You must use FLAGS_8BYTE_INT to enable the i8 tag.");
			}
		} else if(TYPE_STRUCT.equals(type)) {
			s = struct;
		} else if(TYPE_ARRAY.equals(type)) {
			s = array;
		} else if(TYPE_BASE64.equals(type)) {
			s = base64;
		} else {
			throw new XMLRPCException("No deserializer found for type '" + type + "'.");
		}

		return s.deserialize(element);

	}

	/**
	 * Serialize an object to its representation as an xml element.
	 * The xml element will be the type element for the use within a value tag.
	 *
	 * @param object The object that should be serialized.
	 * @return The xml representation of this object.
	 * @throws XMLRPCException Will be thrown, if an error occurs (e.g. the object
	 * 		cannot be serialized to an xml element.
	 */
	public XmlElement serialize(Object object) throws XMLRPCException {

		Serializer s = null;

		if((flags & XMLRPCClient.FLAGS_NIL) != 0 && object == null) {
			s = nil;
		} else if(object instanceof String) {
			s = string;
		} else if(object instanceof Boolean) {
			s = bool;
		} else if(object instanceof Double || object instanceof Float
				|| object instanceof BigDecimal) {
			s = floating;
		} else if (object instanceof Integer || object instanceof Short
				|| object instanceof Byte) {
			s = integer;
		} else if(object instanceof Long) {
			// Check whether the 8 byte integer flag was set.
			if((flags & XMLRPCClient.FLAGS_8BYTE_INT) != 0) {
				s = long8;
			} else {
				// Allow long values as long as their fit within the 4 byte integer range.
				long l = (Long)object;
				if(l > Integer.MAX_VALUE || l < Integer.MIN_VALUE) {
					throw new XMLRPCException("FLAGS_8BYTE_INT must be set, if values "
							+ "outside the 4 byte integer range should be transfered.");
				} else {
					s = integer;
				}
			}
		} else if(object instanceof Date) {
			s = datetime;
		} else if(object instanceof Calendar) {
			object = ((Calendar)object).getTime();
			s = datetime;
		} else if (object instanceof Map) {
			s = struct;
		} else if(object instanceof byte[]) {
			byte[] old = (byte[])object;
			Byte[] boxed = new Byte[old.length];
			for(int i = 0; i < boxed.length; i++) {
				boxed[i] = new Byte(old[i]);
			}
			object = boxed;
			s = base64;
		} else if(object instanceof Byte[]) {
			s = base64;
		} else if(object instanceof Iterable<?> || object instanceof Object[]) {
			s = array;
		} else {
			throw new XMLRPCException("No serializer found for type '"
					+ object.getClass().getName() + "'.");
		}

		return s.serialize(object);

	}

}
