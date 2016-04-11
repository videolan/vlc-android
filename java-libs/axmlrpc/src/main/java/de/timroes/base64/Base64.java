package de.timroes.base64;

import java.util.HashMap;

/**
 * A Base64 en/decoder. You can use it to encode and decode strings and byte arrays.
 *
 * @author Tim Roes
 */
public class Base64 {

	private static final char[] code = ("=ABCDEFGHIJKLMNOPQRSTUVWXYZ"
			+ "abcdefghijklmnopqrstuvwxyz0123456789+/").toCharArray();

	private static final HashMap<Character,Byte> map = new HashMap<Character, Byte>();

	static {
		for(int i = 0; i < code.length; i++) {
			map.put(code[i], (byte)i);
		}
	}

	/**
	 * Decode a base64 encoded string to a byte array.
	 *
	 * @param in A string representing a base64 encoding.
	 * @return The decoded byte array.
	 */
	public static byte[] decode(String in) {

		in = in.replaceAll("\\r|\\n","");
		if(in.length() % 4 != 0) {
			throw new IllegalArgumentException("The length of the input string must be a multiple of four.");
		}

		if(!in.matches("^[A-Za-z0-9+/]*[=]{0,3}$")) {
			throw new IllegalArgumentException("The argument contains illegal characters.");
		}

		byte[] out = new byte[(in.length()*3)/4];

		char[] input = in.toCharArray();

		int outi = 0;
		int b1, b2, b3, b4;
		for(int i = 0; i < input.length; i+=4) {
			b1 = (map.get(input[i]) - 1);
			b2 = (map.get(input[i+1]) - 1);
			b3 = (map.get(input[i+2]) - 1);
			b4 = (map.get(input[i+3]) - 1);
			out[outi++] = (byte)(b1 << 2 | b2 >>> 4);
			out[outi++] = (byte)((b2 & 0x0F) << 4 | b3 >>> 2);
			out[outi++] = (byte)((b3 & 0x03) << 6 | (b4 & 0x3F));
		}

		if(in.endsWith("=")) {
			byte[] trimmed = new byte[out.length - (in.length() - in.indexOf("="))];
			System.arraycopy(out, 0, trimmed, 0, trimmed.length);
			return trimmed;
		}

		return out;

	}

	/**
	 * Decode a base64 encoded string to a string.
	 *
	 * @param in The string representation of the base64 encoding.
	 * @return The decoded string in the default charset of the JVM.
	 */
	public static String decodeAsString(String in) {
		return new String(decode(in));
	}

	/**
	 * Encode a String and return the encoded string.
	 *
	 * @param in A string to encode.
	 * @return The encoded byte array.
	 */
	public static String encode(String in) {
		return encode(in.getBytes());
	}

	/**
	 * Encode a Byte array and return the encoded string.
	 *
	 * @param in A string to encode.
	 * @return The encoded byte array.
	 */
	public static String encode(Byte[] in) {
		byte[] tmp = new byte[in.length];
		for(int i = 0; i < tmp.length; i++) {
			tmp[i] = in[i];
		}
		return encode(tmp);
	}

	/**
	 * Encode a byte array and return the encoded string.
	 *
	 * @param in A string to encode.
	 * @return The encoded byte array.
	 */
	public static String encode(byte[] in) {
		StringBuilder builder = new StringBuilder(4 * ((in.length+2)/3));
		byte[] encoded = encodeAsBytes(in);
		for(int i = 0; i < encoded.length; i++) {
			builder.append(code[encoded[i]+1]);
			if(i % 72 == 71)
				builder.append("\n");
		}
		return builder.toString();
	}

	/**
	 * Encode a String and return the encoded byte array. Bytes that has been
	 * appended to pad the string to a multiple of four are set to -1 in the array.
	 *
	 * @param in A string to encode.
	 * @return The encoded byte array.
	 */
	public static byte[] encodeAsBytes(String in) {
		return encodeAsBytes(in.getBytes());
	}

	/**
	 * Encode a byte array and return the encoded byte array. Bytes that has been
	 * appended to pad the string to a multiple of four are set to -1 in the array.
	 *
	 * @param inArray A string to encode.
	 * @return The encoded byte array.
	 */
	public static byte[] encodeAsBytes(byte[] inArray) {

		// Output string must be 4 * floor((n+2)/3) large
		byte[] out = new byte[4 * ((inArray.length+2)/3)];
		// Create padded input array with the next largest length that is a multiple of 3
		byte[] in = new byte[(inArray.length+2)/3*3];
		// Copy content form unpadded to padded array
		System.arraycopy(inArray, 0, in, 0, inArray.length);

		int outi = 0;
		for(int i = 0; i < in.length; i+=3) {

			out[outi++] = (byte)((in[i] & 0xFF) >>> 2);
			out[outi++] = (byte)(((in[i] & 0x03) << 4) | ((in[i+1] & 0xFF) >>> 4));
			out[outi++] = (byte)(((in[i+1] & 0x0F) << 2) | ((in[i+2] & 0xFF) >>> 6));
			out[outi++] = (byte)(in[i+2] & 0x3F);

		}

		for(int i = in.length - inArray.length; i > 0; i--) {
			out[out.length - i] = -1;
		}

		return out;
	}

}