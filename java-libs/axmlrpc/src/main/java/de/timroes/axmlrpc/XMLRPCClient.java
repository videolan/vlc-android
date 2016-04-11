package de.timroes.axmlrpc;

import de.timroes.axmlrpc.serializer.SerializerHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.*;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.*;

/**
 * An XMLRPCClient is a client used to make XML-RPC (Extensible Markup Language
 * Remote Procedure Calls).
 * The specification of XMLRPC can be found at http://www.xmlrpc.com/spec.
 * You can use flags to extend the functionality of the client to some extras.
 * Further information on the flags can be found in the documentation of these.
 * For a documentation on how to use this class see also the README file delivered
 * with the source of this library.
 *
 * @author Tim Roes
 */
public class XMLRPCClient {

	private static final String DEFAULT_USER_AGENT = "aXMLRPC";

	/**
	 * Constants from the http protocol.
	 */
	static final String USER_AGENT = "User-Agent";
	static final String CONTENT_TYPE = "Content-Type";
	static final String TYPE_XML = "text/xml; charset=utf-8";
	static final String HOST = "Host";
	static final String CONTENT_LENGTH = "Content-Length";
	static final String HTTP_POST = "POST";

	/**
	 * XML elements to be used.
	 */
	static final String METHOD_RESPONSE = "methodResponse";
	static final String PARAMS = "params";
	static final String PARAM = "param";
	public static final String VALUE = "value";
	static final String FAULT = "fault";
	static final String METHOD_CALL = "methodCall";
	static final String METHOD_NAME = "methodName";
	static final String STRUCT_MEMBER = "member";

	/**
	 * No flags should be set.
	 */
	public static final int FLAGS_NONE = 0x0;

	/**
	 * The client should parse responses strict to specification.
	 * It will check if the given content-type is right.
	 * The method name in a call must only contain of A-Z, a-z, 0-9, _, ., :, /
	 * Normally this is not needed.
	 */
	public static final int FLAGS_STRICT = 0x01;

	/**
	 * The client will be able to handle 8 byte integer values (longs).
	 * The xml type tag &lt;i8&gt; will be used. This is not in the specification
	 * but some libraries and servers support this behaviour.
	 * If this isn't enabled you cannot recieve 8 byte integers and if you try to
	 * send a long the value must be within the 4byte integer range.
	 */
	public static final int FLAGS_8BYTE_INT = 0x02;

	/**
	 * With this flag, the client will be able to handle cookies, meaning saving cookies
	 * from the server and sending it with every other request again. This is needed
	 * for some XML-RPC interfaces that support login.
	 */
	public static final int FLAGS_ENABLE_COOKIES = 0x04;

	/**
	 * The client will be able to send null values. A null value will be send
	 * as <nil/>. This extension is described under: http://ontosys.com/xml-rpc/extensions.php
	 */
	public static final int FLAGS_NIL = 0x08;

	/**
	 * With this flag enabled, the XML-RPC client will ignore the HTTP status
	 * code of the response from the server. According to specification the
	 * status code must be 200. This flag is only needed for the use with
	 * not standard compliant servers.
	 */
	public static final int FLAGS_IGNORE_STATUSCODE = 0x10;

	/**
	 * With this flag enabled, the client will forward the request, if
	 * the 301 or 302 HTTP status code has been received. If this flag has not
	 * been set, the client will throw an exception on these HTTP status codes.
	 */
	public static final int FLAGS_FORWARD = 0x20;

	/**
	 * With this flag enabled, the client will ignore, if the URL doesn't match
	 * the SSL Certificate. This should be used with caution. Normally the URL
	 * should always match the URL in the SSL certificate, even with self signed
	 * certificates.
	 */
	public static final int FLAGS_SSL_IGNORE_INVALID_HOST = 0x40;

	/**
	 * With this flag enabled, the client will ignore all unverified SSL/TLS
	 * certificates. This must be used, if you use self-signed certificates
	 * or certificated from unknown (or untrusted) authorities. If this flag is
	 * used, calls to {@link #installCustomTrustManager(javax.net.ssl.TrustManager)}
	 * won't have any effect.
	 */
	public static final int FLAGS_SSL_IGNORE_INVALID_CERT = 0x80;

	/**
	 * With this flag enabled, a value with a missing type tag, will be parsed
	 * as a string element. This is just for incoming messages. Outgoing messages
	 * will still be generated according to specification.
	 */
	public static final int FLAGS_DEFAULT_TYPE_STRING = 0x100;

	/**
	 * With this flag enabled, the {@link XMLRPCClient} ignores all namespaces
	 * used within the response from the server.
	 */
	public static final int FLAGS_IGNORE_NAMESPACES = 0x200;

	/**
	 * With this flag enabled, the {@link XMLRPCClient} will use the system http
	 * proxy to connect to the XML-RPC server.
	 */
	public static final int FLAGS_USE_SYSTEM_PROXY = 0x400;

	/**
	 * This prevents the decoding of incoming strings, meaning &amp; and &lt;
	 * won't be decoded to the & sign and the "less then" sign. See
	 * {@link #FLAGS_NO_STRING_ENCODE} for the counterpart.
	 */
	public static final int FLAGS_NO_STRING_DECODE = 0x800;

	/**
	 * By default outgoing string values will be encoded according to specification.
	 * Meaning the & sign will be encoded to &amp; and the "less then" sign to &lt;.
	 * If you set this flag, the encoding won't be done for outgoing string values.
	 * See {@link #FLAGS_NO_STRING_ENCODE} for the counterpart.
	 */
	public static final int FLAGS_NO_STRING_ENCODE = 0x1000;

	/**
	 * Activate debug mode.
	 * Do NOT use if you don't need it.
	 */
	public static final int FLAGS_DEBUG = 0x2000;

	/**
	 * This flag disables all SSL warnings. It is an alternative to use
	 * FLAGS_SSL_IGNORE_INVALID_CERT | FLAGS_SSL_IGNORE_INVALID_HOST. There
	 * is no functional difference.
	 */
	public static final int FLAGS_SSL_IGNORE_ERRORS =
			FLAGS_SSL_IGNORE_INVALID_CERT | FLAGS_SSL_IGNORE_INVALID_HOST;

	/**
	 * This flag should be used if the server is an apache ws xmlrpc server.
	 * This will set some flags, so that the not standard conform behavior
	 * of the server will be ignored.
	 * This will enable the following flags: FLAGS_IGNORE_NAMESPACES, FLAGS_NIL,
	 * FLAGS_DEFAULT_TYPE_STRING
	 */
	public static final int FLAGS_APACHE_WS = FLAGS_IGNORE_NAMESPACES | FLAGS_NIL
			| FLAGS_DEFAULT_TYPE_STRING;

	private final int flags;

	private URL url;
	private Map<String,String> httpParameters = new ConcurrentHashMap<String, String>();

	private Map<Long,Caller> backgroundCalls = new ConcurrentHashMap<Long, Caller>();

	private ResponseParser responseParser;
	private CookieManager cookieManager;
	private AuthenticationManager authManager;

	private TrustManager[] trustManagers;
    private KeyManager[] keyManagers;

	private Proxy proxy;

	private int timeout;

	/**
	 * Create a new XMLRPC client for the given URL.
	 *
	 * @param url The URL to send the requests to.
	 * @param userAgent A user agent string to use in the HTTP requests.
	 * @param flags A combination of flags to be set.
	 */
	public XMLRPCClient(URL url, String userAgent, int flags) {

		SerializerHandler.initialize(flags);

		this.url = url;

		this.flags = flags;
		// Create a parser for the http responses.
		responseParser = new ResponseParser();

		cookieManager = new CookieManager(flags);
		authManager = new AuthenticationManager();

		httpParameters.put(CONTENT_TYPE, TYPE_XML);
		httpParameters.put(USER_AGENT, userAgent);

		// If invalid ssl certs are ignored, instantiate an all trusting TrustManager
		if(isFlagSet(FLAGS_SSL_IGNORE_INVALID_CERT)) {
			trustManagers = new TrustManager[] {
				new X509TrustManager() {
					public void checkClientTrusted(X509Certificate[] xcs, String string)
							throws CertificateException { }

					public void checkServerTrusted(X509Certificate[] xcs, String string)
							throws CertificateException { }

					public X509Certificate[] getAcceptedIssuers() {
						return null;
					}
				}
			};
		}

		if(isFlagSet(FLAGS_USE_SYSTEM_PROXY)) {
			// Read system proxy settings and generate a proxy from that
			Properties prop = System.getProperties();
			String proxyHost = prop.getProperty("http.proxyHost");
			int proxyPort = Integer.parseInt(prop.getProperty("http.proxyPort", "0"));
			if(proxyPort > 0 && proxyHost.length() > 0 && !proxyHost.equals("null")) {
				proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
			}
		}

	}

	/**
	 * Create a new XMLRPC client for the given URL.
	 * The default user agent string will be used.
	 *
	 * @param url The URL to send the requests to.
	 * @param flags A combination of flags to be set.
	 */
	public XMLRPCClient(URL url, int flags) {
		this(url, DEFAULT_USER_AGENT, flags);
	}

	/**
	 * Create a new XMLRPC client for the given url.
	 * No flags will be set.
	 *
	 * @param url The url to send the requests to.
	 * @param userAgent A user agent string to use in the http request.
	 */
	public XMLRPCClient(URL url, String userAgent) {
		this(url, userAgent, FLAGS_NONE);
	}

	/**
	 * Create a new XMLRPC client for the given url.
	 * No flags will be used.
	 * The default user agent string will be used.
	 *
	 * @param url The url to send the requests to.
	 */
	public XMLRPCClient(URL url) {
		this(url, DEFAULT_USER_AGENT, FLAGS_NONE);
	}

	/**
	 * Returns the URL this XMLRPCClient is connected to. If that URL permanently forwards
	 * to another URL, this method will return the forwarded URL, as soon as
	 * the first call has been made.
	 *
	 * @return Returns the URL for this XMLRPCClient.
	 */
	public URL getURL() {
		return url;
	}

	/**
	 * Sets the time in seconds after which a call should timeout.
	 * If {@code timeout} will be zero or less the connection will never timeout.
	 * In case the connection times out an {@link XMLRPCTimeoutException} will
	 * be thrown for calls made by {@link #call(java.lang.String, java.lang.Object[])}.
	 * For calls made by {@link #callAsync(de.timroes.axmlrpc.XMLRPCCallback, java.lang.String, java.lang.Object[])}
	 * the {@link XMLRPCCallback#onError(long, de.timroes.axmlrpc.XMLRPCException)} method
	 * of the callback will be called. By default connections won't timeout.
	 *
	 * @param timeout The timeout for connections in seconds.
	 */
	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	/**
	 * Sets the user agent string.
	 * If this method is never called the default
	 * user agent 'aXMLRPC' will be used.
	 *
	 * @param userAgent The new user agent string.
	 */
	public void setUserAgentString(String userAgent) {
		httpParameters.put(USER_AGENT, userAgent);
	}

	/**
	 * Sets a proxy to use for this client. If you want to use the system proxy,
	 * use {@link #FLAGS_USE_SYSTEM_PROXY} instead. If combined with
	 * {@code FLAGS_USE_SYSTEM_PROXY}, this proxy will be used instead of the
	 * system proxy.
	 *
	 * @param proxy A proxy to use for the connection.
	 */
	public void setProxy(Proxy proxy) {
		this.proxy = proxy;
	}

	/**
	 * Set a HTTP header field to a custom value.
	 * You cannot modify the Host or Content-Type field that way.
	 * If the field already exists, the old value is overwritten.
	 *
	 * @param headerName The name of the header field.
	 * @param headerValue The new value of the header field.
	 */
	public void setCustomHttpHeader(String headerName, String headerValue) {
		if(CONTENT_TYPE.equals(headerName) || HOST.equals(headerName)
				|| CONTENT_LENGTH.equals(headerName)) {
			throw new XMLRPCRuntimeException("You cannot modify the Host, Content-Type or Content-Length header.");
		}
		httpParameters.put(headerName, headerValue);
	}

	/**
	 * Set the username and password that should be used to perform basic
	 * http authentication.
	 *
	 * @param user Username
	 * @param pass Password
	 */
	public void setLoginData(String user, String pass) {
		authManager.setAuthData(user, pass);
	}

	/**
	 * Clear the username and password. No basic HTTP authentication will be used
	 * in the next calls.
	 */
	public void clearLoginData() {
		authManager.clearAuthData();
	}

	/**
	 * Returns a {@link Map} of all cookies. It contains each cookie key as a map
	 * key and its value as a map value. Cookies will only be used if {@link #FLAGS_ENABLE_COOKIES}
	 * has been set for the client. This map will also be available (and empty)
	 * when this flag hasn't been said, but has no effect on the HTTP connection.
	 *
	 * @return A {@code Map} of all cookies.
	 */
	public Map<String,String> getCookies() {
		return cookieManager.getCookies();
	}

	/**
	 * Delete all cookies currently used by the client.
	 * This method has only an effect, as long as the FLAGS_ENABLE_COOKIES has
	 * been set on this client.
	 */
	public void clearCookies() {
		cookieManager.clearCookies();
	}

	/**
	 * Installs a custom {@link TrustManager} to handle SSL/TLS certificate verification.
	 * This will replace any previously installed {@code TrustManager}s.
	 * If {@link #FLAGS_SSL_IGNORE_INVALID_CERT} is set, this won't do anything.
	 *
	 * @param trustManager {@link TrustManager} to install.
	 *
	 * @see #installCustomTrustManagers(javax.net.ssl.TrustManager[])
	 */
	public void installCustomTrustManager(TrustManager trustManager) {
		if(!isFlagSet(FLAGS_SSL_IGNORE_INVALID_CERT)) {
			trustManagers = new TrustManager[] { trustManager };
		}
	}

	/**
	 * Installs custom {@link TrustManager TrustManagers} to handle SSL/TLS certificate
	 * verification. This will replace any previously installed {@code TrustManagers}s.
	 * If {@link #FLAGS_SSL_IGNORE_INVALID_CERT} is set, this won't do anything.
	 *
	 * @param trustManagers {@link TrustManager TrustManagers} to install.
	 *
	 * @see #installCustomTrustManager(javax.net.ssl.TrustManager)
	 */
	public void installCustomTrustManagers(TrustManager[] trustManagers) {
		if(!isFlagSet(FLAGS_SSL_IGNORE_INVALID_CERT)) {
			this.trustManagers = trustManagers.clone();
		}
	}

    /**
     * Installs a custom {@link KeyManager} to handle SSL/TLS certificate verification.
     * This will replace any previously installed {@code KeyManager}s.
     * If {@link #FLAGS_SSL_IGNORE_INVALID_CERT} is set, this won't do anything.
     *
     * @param keyManager {@link KeyManager} to install.
     *
     * @see #installCustomKeyManagers(javax.net.ssl.KeyManager[])
     */
    public void installCustomKeyManager(KeyManager keyManager) {
        if(!isFlagSet(FLAGS_SSL_IGNORE_INVALID_CERT)) {
            keyManagers = new KeyManager[] { keyManager };
        }
    }

    /**
     * Installs custom {@link KeyManager KeyManagers} to handle SSL/TLS certificate
     * verification. This will replace any previously installed {@code KeyManagers}s.
     * If {@link #FLAGS_SSL_IGNORE_INVALID_CERT} is set, this won't do anything.
     *
     * @param keyManagers {@link KeyManager KeyManagers} to install.
     *
     * @see #installCustomKeyManager(javax.net.ssl.KeyManager)
     */
    public void installCustomKeyManagers(KeyManager[] keyManagers) {
      if(!isFlagSet(FLAGS_SSL_IGNORE_INVALID_CERT)) {
        this.keyManagers = keyManagers.clone();
      }
    }

	/**
	 * Call a remote procedure on the server. The method must be described by
	 * a method name. If the method requires parameters, this must be set.
	 * The type of the return object depends on the server. You should consult
	 * the server documentation and then cast the return value according to that.
	 * This method will block until the server returned a result (or an error occurred).
	 * Read the README file delivered with the source code of this library for more
	 * information.
	 *
	 * @param method A method name to call.
	 * @param params An array of parameters for the method.
	 * @return The result of the server.
	 * @throws XMLRPCException Will be thrown if an error occurred during the call.
	 */
	public Object call(String method, Object... params) throws XMLRPCException {
		return new Caller().call(method, params);
	}

	/**
	 * Asynchronously call a remote procedure on the server. The method must be
	 * described by a method  name. If the method requires parameters, this must
	 * be set. When the server returns a response the onResponse method is called
	 * on the listener. If the server returns an error the onServerError method
	 * is called on the listener. The onError method is called whenever something
	 * fails. This method returns immediately and returns an identifier for the
	 * request. All listener methods get this id as a parameter to distinguish between
	 * multiple requests.
	 *
	 * @param listener A listener, which will be notified about the server response or errors.
	 * @param methodName A method name to call on the server.
	 * @param params An array of parameters for the method.
	 * @return The id of the current request.
	 */
	public long callAsync(XMLRPCCallback listener, String methodName, Object... params) {
		long id = System.currentTimeMillis();
		new Caller(listener, id, methodName, params).start();
		return id;
	}

	/**
	 * Cancel a specific asynchronous call.
	 *
	 * @param id The id of the call as returned by the callAsync method.
	 */
	public void cancel(long id) {

		// Lookup the background call for the given id.
		Caller cancel = backgroundCalls.get(id);
		if(cancel == null) {
			return;
		}

		// Cancel the thread
		cancel.cancel();

		try {
			// Wait for the thread
			cancel.join();
		} catch (InterruptedException ex) {
			// Ignore this
		}

	}

	/**
	 * Create a call object from a given method string and parameters.
	 *
	 * @param method The method that should be called.
	 * @param params An array of parameters or null if no parameters needed.
	 * @return A call object.
	 */
	private Call createCall(String method, Object[] params) {

		if(isFlagSet(FLAGS_STRICT) && !method.matches("^[A-Za-z0-9\\._:/]*$")) {
			throw new XMLRPCRuntimeException("Method name must only contain A-Z a-z . : _ / ");
		}

		return new Call(method, params);

	}

	/**
	 * Checks whether a specific flag has been set.
	 *
	 * @param flag The flag to check for.
	 * @return Whether the flag has been set.
	 */
	private boolean isFlagSet(int flag) {
		return (this.flags & flag) != 0;
	}

	/**
	 * The Caller class is used to make asynchronous calls to the server.
	 * For synchronous calls the Thread function of this class isn't used.
	 */
	private class Caller extends Thread {

		private XMLRPCCallback listener;
		private long threadId;
		private String methodName;
		private Object[] params;

		private volatile boolean canceled;
		private HttpURLConnection http;

		/**
		 * Create a new Caller for asynchronous use.
		 *
		 * @param listener The listener to notice about the response or an error.
		 * @param threadId An id that will be send to the listener.
		 * @param methodName The method name to call.
		 * @param params The parameters of the call or null.
		 */
		public Caller(XMLRPCCallback listener, long threadId, String methodName, Object[] params) {
			this.listener = listener;
			this.threadId = threadId;
			this.methodName = methodName;
			this.params = params;
		}

		/**
		 * Create a new Caller for synchronous use.
		 * If the caller has been created with this constructor you cannot use the
		 * start method to start it as a thread. But you can call the call method
		 * on it for synchronous use.
		 */
		public Caller() { }

		/**
		 * The run method is invoked when the thread gets started.
		 * This will only work, if the Caller has been created with parameters.
		 * It execute the call method and notify the listener about the result.
		 */
		@Override
		public void run() {

			if(listener == null)
				return;

			try {
				backgroundCalls.put(threadId, this);
				Object o = this.call(methodName, params);
				listener.onResponse(threadId, o);
			} catch(CancelException ex) {
				// Don't notify the listener, if the call has been canceled.
			} catch(XMLRPCServerException ex) {
				listener.onServerError(threadId, ex);
			} catch (XMLRPCException ex) {
				listener.onError(threadId, ex);
			} finally {
				backgroundCalls.remove(threadId);
			}

		}

		/**
		 * Cancel this call. This will abort the network communication.
		 */
		public void cancel() {
			// Set the flag, that this thread has been canceled
			canceled = true;
			// Disconnect the connection to the server
			http.disconnect();
		}

		/**
		 * Call a remote procedure on the server. The method must be described by
		 * a method name. If the method requires parameters, this must be set.
		 * The type of the return object depends on the server. You should consult
		 * the server documentation and then cast the return value according to that.
		 * This method will block until the server returned a result (or an error occurred).
		 * Read the README file delivered with the source code of this library for more
		 * information.
		 *
		 * @param method A method name to call.
		 * @param params An array of parameters for the method.
		 * @return The result of the server.
		 * @throws XMLRPCException Will be thrown if an error occurred during the call.
		 */
		public Object call(String methodName, Object[] params) throws XMLRPCException {

			try {

				Call c = createCall(methodName, params);

				// If proxy is available, use it
				URLConnection conn;
				if(proxy != null)
					conn = url.openConnection(proxy);
				else
					conn = url.openConnection();

				http = verifyConnection(conn);
				http.setInstanceFollowRedirects(false);
				http.setRequestMethod(HTTP_POST);
				http.setDoOutput(true);
				http.setDoInput(true);

				// Set timeout
				if(timeout > 0) {
					http.setConnectTimeout(timeout * 1000);
					http.setReadTimeout(timeout * 1000);
				}

				// Set the request parameters
				for(Map.Entry<String,String> param : httpParameters.entrySet()) {
					http.setRequestProperty(param.getKey(), param.getValue());
				}

				authManager.setAuthentication(http);
				cookieManager.setCookies(http);

				OutputStreamWriter stream = new OutputStreamWriter(http.getOutputStream());
				stream.write(c.getXML(isFlagSet(FLAGS_DEBUG)));
				stream.flush();
				stream.close();

				// Try to get the status code from the connection
				int statusCode;
				try {
					statusCode = http.getResponseCode();
				} catch(IOException ex) {
					// Due to a bug on android, the getResponseCode()-method will
					// fail the first time, with a IOException, when 401 or 403 has been returned.
					// The second time it should success. If it fail the second time again
					// the normal exceptipon handling can take care of this, since
					// it is a real error.
					statusCode = http.getResponseCode();
				}

				InputStream istream;

				// If status code was 401 or 403 throw exception or if appropriate
				// flag is set, ignore error code.
				if(statusCode == HttpURLConnection.HTTP_FORBIDDEN
						|| statusCode == HttpURLConnection.HTTP_UNAUTHORIZED) {

					if(isFlagSet(FLAGS_IGNORE_STATUSCODE)) {
						// getInputStream will fail if server returned above
						// error code, use getErrorStream instead
						istream = http.getErrorStream();
					} else {
						throw new XMLRPCException("Invalid status code '"
								+ statusCode + "' returned from server.");
					}

				} else {
					istream = http.getInputStream();
				}

				// If status code is 301 Moved Permanently or 302 Found ...
				if(statusCode == HttpURLConnection.HTTP_MOVED_PERM
						|| statusCode == HttpURLConnection.HTTP_MOVED_TEMP) {
					// ... do either a foward
					if(isFlagSet(FLAGS_FORWARD)) {
						boolean temporaryForward = (statusCode == HttpURLConnection.HTTP_MOVED_TEMP);

						// Get new location from header field.
						String newLocation = http.getHeaderField("Location");
						// Try getting header in lower case, if no header has been found
						if(newLocation == null || newLocation.length() <= 0)
							newLocation = http.getHeaderField("location");

						// Set new location, disconnect current connection and request to new location.
						URL oldURL = url;
						url = new URL(newLocation);
						http.disconnect();
						Object forwardedResult = call(methodName, params);

						// In case of temporary forward, restore original URL again for next call.
						if(temporaryForward) {
							url = oldURL;
						}

						return forwardedResult;

					} else {
						// ... or throw an exception
						throw new XMLRPCException("The server responded with a http 301 or 302 status "
								+ "code, but forwarding has not been enabled (FLAGS_FORWARD).");

					}
				}

				if(!isFlagSet(FLAGS_IGNORE_STATUSCODE)
					&& statusCode != HttpURLConnection.HTTP_OK) {
					throw new XMLRPCException("The status code of the http response must be 200.");
				}

				// Check for strict parameters
				if(isFlagSet(FLAGS_STRICT)) {
					if(!http.getContentType().startsWith(TYPE_XML)) {
						throw new XMLRPCException("The Content-Type of the response must be text/xml.");
					}
				}

				cookieManager.readCookies(http);

				return responseParser.parse(istream, isFlagSet(FLAGS_DEBUG));

			} catch(SocketTimeoutException ex) {
				throw new XMLRPCTimeoutException("The XMLRPC call timed out.");
			} catch (IOException ex) {
				// If the thread has been canceled this exception will be thrown.
				// So only throw an exception if the thread hasnt been canceled
				// or if the thred has not been started in background.
				if(!canceled || threadId <= 0) {
					throw new XMLRPCException(ex);
				} else {
					throw new CancelException();
				}
			}

		}

		/**
		 * Verifies the given URLConnection to be a valid HTTP or HTTPS connection.
		 * If the SSL ignoring flags are set, the method will ignore SSL warnings.
		 *
		 * @param conn The URLConnection to validate.
		 * @return The verified HttpURLConnection.
		 * @throws XMLRPCException Will be thrown if an error occurred.
		 */
		private HttpURLConnection verifyConnection(URLConnection conn) throws XMLRPCException {

				if(!(conn instanceof HttpURLConnection)) {
					throw new IllegalArgumentException("The URL is not valid for a http connection.");
				}

				// Validate the connection if its an SSL connection
				if(conn instanceof HttpsURLConnection) {

					HttpsURLConnection h = (HttpsURLConnection)conn;

					// Don't check, that URL matches the certificate.
					if(isFlagSet(FLAGS_SSL_IGNORE_INVALID_HOST)) {
						h.setHostnameVerifier(new HostnameVerifier() {
							public boolean verify(String host, SSLSession ssl) {
								return true;
							}
						});
					}

					// Associate the TrustManager with TLS and SSL connections, if present.
					if(trustManagers != null) {
						try {
							String[] sslContexts = new String[]{ "TLS", "SSL" };

							for(String ctx : sslContexts) {
								SSLContext sc = SSLContext.getInstance(ctx);
								sc.init(keyManagers, trustManagers, new SecureRandom());
								h.setSSLSocketFactory(sc.getSocketFactory());
							}

						} catch(Exception ex) {
							throw new XMLRPCException(ex);
						}

					}

					return h;

				}

				return (HttpURLConnection)conn;

		}

	}

	private class CancelException extends RuntimeException { }

}
