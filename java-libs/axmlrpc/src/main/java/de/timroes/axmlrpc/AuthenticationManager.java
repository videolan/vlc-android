package de.timroes.axmlrpc;

import de.timroes.base64.Base64;
import java.net.HttpURLConnection;

/**
 * The AuthenticationManager handle basic HTTP authentication.
 * 
 * @author Tim Roes
 */
public class AuthenticationManager {
	
	private String user;
	private String pass;

	/**
	 * Clear the username and password. No basic HTTP authentication will be used
	 * in the next calls.
	 */
	public void clearAuthData() {
		this.user = null;
		this.pass = null;
	}
	
	/**
	 * Set the username and password that should be used to perform basic
	 * http authentication.
	 * 
	 * @param user Username
	 * @param pass Password
	 */
	public void setAuthData(String user, String pass) {
		this.user = user;
		this.pass = pass;
	}

	/**
	 * Set the authentication at the HttpURLConnection.
	 * 
	 * @param http The HttpURLConnection to set authentication.
	 */
	public void setAuthentication(HttpURLConnection http) {
		
		if(user == null || pass == null 
				|| user.length() <= 0 || pass.length() <= 0) {
			return;
		}

		String base64login = Base64.encode(user + ":" + pass);

		http.addRequestProperty("Authorization", "Basic " + base64login);
		
	}
	
}