package de.timroes.axmlrpc;

import java.net.HttpURLConnection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The CookieManager handles cookies for the http requests.
 * If the FLAGS_ENABLE_COOKIES has been set, it will save cookies
 * and send it with every request.
 *
 * @author Tim Roes
 */
class CookieManager {

	private static final String SET_COOKIE = "Set-Cookie";
	private static final String COOKIE = "Cookie";

	private int flags;
	private Map<String,String> cookies = new ConcurrentHashMap<String, String>();

	/**
	 * Create a new CookieManager with the given flags.
	 *
	 * @param flags A combination of flags to be set.
	 */
	public CookieManager(int flags) {
		this.flags = flags;
	}

	/**
	 * Delete all cookies.
	 */
	public void clearCookies() {
		cookies.clear();
	}

	/**
	 * Returns a {@link Map} of all cookies.
	 * 
	 * @return All cookies
	 */
	public Map<String,String> getCookies() {
		return cookies;
	}
	
	/**
	 * Read the cookies from an http response. It will look at every Set-Cookie
	 * header and put the cookie to the map of cookies.
	 *
	 * @param http A http connection.
	 */
	public void readCookies(HttpURLConnection http) {

		// Only save cookies if FLAGS_ENABLE_COOKIES has been set.
		if((flags & XMLRPCClient.FLAGS_ENABLE_COOKIES) == 0)
			return;

		String cookie, key;
		String[] split;

		// Extract every Set-Cookie field and put the cookie to the cookies map.
		for(int i = 0; i < http.getHeaderFields().size(); i++) {
			key = http.getHeaderFieldKey(i);
			if(key != null && SET_COOKIE.toLowerCase().equals(key.toLowerCase())) {
				cookie = http.getHeaderField(i).split(";")[0];
				split = cookie.split("=");
				if(split.length >= 2)
					cookies.put(split[0], split[1]);
			}
		}

	}

	/**
	 * Write the cookies to a http connection. It will set the Cookie field
	 * to all currently set cookies in the map.
	 *
	 * @param http A http connection.
	 */
	public void setCookies(HttpURLConnection http) {

		// Only save cookies if FLAGS_ENABLE_COOKIES has been set.
		if((flags & XMLRPCClient.FLAGS_ENABLE_COOKIES) == 0)
			return;

		String concat = "";
		for(Map.Entry<String,String> cookie : cookies.entrySet()) {
			concat += cookie.getKey() + "=" + cookie.getValue() + "; ";
		}
		http.setRequestProperty(COOKIE, concat);

	}

}