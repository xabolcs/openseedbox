package com.openseedbox.code.libs;

import java.net.URLDecoder;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import play.Logger;
import play.libs.WS;
import play.libs.WS.HttpResponse;
import play.mvc.Http.Request;
import play.mvc.Scope.Params;
import play.mvc.results.Redirect;

/**
 * Originally imported from https://github.com/playframework/play1/blob/1.7.1/framework/src/play/libs/OAuth2.java
 *
 * Library to access resources protected by OAuth 2.0. For OAuth 1.0a, see play.libs.OAuth. See the facebook-oauth2
 * example for usage.
 */
public class OAuth2 {

	protected static final String CLIENT_ID_NAME = "client_id";
	protected static final String REDIRECT_URI = "redirect_uri";
	protected static final String CLIENT_SECRET_NAME = "client_secret";
	protected static final String CODE_NAME = "code";

	public String authorizationURL;
	public String accessTokenURL;
	public String clientid;
	public String secret;

	public OAuth2(String authorizationURL, String accessTokenURL, String clientid, String secret) {
		this.accessTokenURL = accessTokenURL;
		this.authorizationURL = authorizationURL;
		this.clientid = clientid;
		this.secret = secret;
	}

	public static boolean isCodeResponse() {
		return Params.current().get(CODE_NAME) != null;
	}

	/**
	 * First step of the OAuth2 process: redirects the user to the authorisation page
	 *
	 * @param callbackURL
	 *            The callback URL
	 */
	public void retrieveVerificationCode(String callbackURL) {
		retrieveVerificationCode(callbackURL, new HashMap<String, String>());
	}

	/**
	 * First step of the oAuth2 process. This redirects the user to the authorization page on the oAuth2 provider. This
	 * is a helper method that only takes one parameter name,value pair and then converts them into a map to be used by
	 * {@link #retrieveVerificationCode(String, Map)}
	 *
	 * @param callbackURL
	 *            The URL to redirect the user to after authorization
	 * @param parameterName
	 *            An additional parameter name
	 * @param parameterValue
	 *            An additional parameter value
	 */
	public void retrieveVerificationCode(String callbackURL, String parameterName, String parameterValue) {
		Map<String, String> parameters = new HashMap<>();
		parameters.put(parameterName, parameterValue);
		retrieveVerificationCode(callbackURL, parameters);
	}

	/**
	 * First step of the oAuth2 process. This redirects the user to the authorisation page on the oAuth2 provider.
	 *
	 * @param callbackURL
	 *            The URL to redirect the user to after authorisation
	 * @param parameters
	 *            Any additional parameters that weren't included in the constructor. For example you might need to add
	 *            a response_type.
	 */
	public void retrieveVerificationCode(String callbackURL, Map<String, String> parameters) {
		parameters.put(CLIENT_ID_NAME, clientid);
		parameters.put(REDIRECT_URI, callbackURL);
		Logger.debug("retrieveVerificationCode: parameters - %s", parameters.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(", ")));
		throw new Redirect(authorizationURL, parameters);
	}

	public void retrieveVerificationCode() {
		retrieveVerificationCode(Request.current().getBase() + Request.current().url);
	}

	public Response retrieveAccessToken(String callbackURL) {
		String accessCode = Params.current().get(CODE_NAME);
		Map<String, Object> params = new HashMap<>();
		params.put(CLIENT_ID_NAME, clientid);
		params.put(CLIENT_SECRET_NAME, secret);
		params.put(REDIRECT_URI, callbackURL);
		params.put(CODE_NAME, accessCode);
		return retrieveAccessToken(callbackURL, params);
	}

	protected Response retrieveAccessToken(String callbackURL, Map<String, Object> params) {
		Logger.debug("retrieveAccessToken(%s): params - %s", this.getClass().getSimpleName(), params.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(", ")));
		HttpResponse response = WS.url(accessTokenURL).params(params).get();
		Logger.debug("retrieveAccessToken(%s): response - %s", this.getClass().getSimpleName(), response.getString());
		return new Response(response);
	}

	public Response retrieveAccessToken() {
		return retrieveAccessToken(Request.current().getBase() + Request.current().url);
	}

	/**
	 * @deprecated Use @{link play.libs.OAuth2.retrieveVerificationCode()} instead
	 */
	@Deprecated
	public void requestAccessToken() {
		retrieveVerificationCode();
	}

	/**
	 * @return The access token
	 * @deprecated Use @{link play.libs.OAuth2.retrieveAccessToken()} instead
	 */
	@Deprecated
	public String getAccessToken() {
		return retrieveAccessToken().accessToken;
	}

	public static class Response {
		public final String accessToken;
		public final Error error;
		public final WS.HttpResponse httpResponse;

		protected Response(String accessToken, Error error, WS.HttpResponse response) {
			this.accessToken = accessToken;
			this.error = error;
			this.httpResponse = response;
		}

		public Response(WS.HttpResponse response) {
			this.httpResponse = response;
			this.accessToken = getAccessToken(response);
			if (this.accessToken != null) {
				this.error = null;
			} else {
				this.error = Error.oauth2(response);
			}
		}

		public static Response error(Error error, WS.HttpResponse response) {
			return new Response(null, error, response);
		}

		private String getAccessToken(WS.HttpResponse httpResponse) {
			if (httpResponse.getContentType().contains("application/json")) {
				JsonElement accessToken = httpResponse.getJson().getAsJsonObject().get("access_token");
				return accessToken != null ? accessToken.getAsString() : null;
			} else {
				return httpResponse.getQueryString().get("access_token");
			}
		}
	}

	public static class Error {
		public final Type type;
		public final String error;
		public final String description;

		public enum Type {
			COMMUNICATION, OAUTH, UNKNOWN
		}

		protected Error(Type type, String error, String description) {
			this.type = type;
			this.error = error;
			this.description = description;
		}

		static Error communication() {
			return new Error(Type.COMMUNICATION, null, null);
		}

		public static Error oauth2(Map<String, String> responseParams) {
			String error = responseParams.get("error");
			if (error != null && !error.contains(" ") && error.indexOf("+") != error.lastIndexOf("+")) {
				error = URLDecoder.decode(error);
			}
			String description = responseParams.get("error_description");
			if (description != null && !description.contains(" ") && description.indexOf("+") != description.lastIndexOf("+")) {
				description = URLDecoder.decode(description);
			}
			return new Error(Type.OAUTH, error, description);
		}
		static Error oauth2(WS.HttpResponse response) {
			if (response.getQueryString().containsKey("error")) {
				return Error.oauth2(response.getQueryString());
			} else if (response.getContentType().contains("application/json")) {
				JsonObject jsonResponse = response.getJson().getAsJsonObject();
				JsonElement error = jsonResponse.getAsJsonPrimitive("error");
				JsonElement description = jsonResponse.getAsJsonPrimitive("error_description");
				return new Error(Type.OAUTH,
						error != null ? error.getAsString() : null,
						description != null ? description.getAsString() : null
				);
			} else if (response.getContentType().startsWith("text/javascript")) { // Stupid Facebook returns JSON with
				// the wrong encoding
				JsonObject jsonResponse = response.getJson().getAsJsonObject().getAsJsonObject("error");
				return new Error(Type.OAUTH, jsonResponse.getAsJsonPrimitive("type").getAsString(),
						jsonResponse.getAsJsonPrimitive("message").getAsString());
			} else {
				return new Error(Type.UNKNOWN, null, null);
			}
		}

		@Override
		public String toString() {
			return "OAuth2 Error: " + type + " - " + error + " (" + description + ")";
		}
	}

}
