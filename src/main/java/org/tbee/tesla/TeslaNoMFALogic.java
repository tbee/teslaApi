package org.tbee.tesla;

/*-
 * #%L
 * TeslaAPI
 * %%
 * Copyright (C) 2020 - 2021 Tom Eugelink
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tbee.tesla.dto.Tokens;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * The MFA based login flow
 */
class TeslaNoMFALogic {
	static final Logger logger = LoggerFactory.getLogger(TeslaNoMFALogic.class);
	
	static final String CLIENT_SECRET = "c7257eb71a564034f9419ee651c7d0e5f7aa6bfbd18bafb5c5c033b093bb2fa3";
	static final String CLIENT_ID = "81527cff06843c8634fdc09e8ac0abefb46ac849f38fe1e431c2ef2106796384";	

    // For HTTP
	private final Gson gson = new Gson();
	private final OkHttpClient okHttpClient;
	private final MediaType JsonMediaType = MediaType.parse("application/json; charset=utf-8");
	
	// For improved logging 
	private final String logPrefix;

	
	/**
	 * 
	 */
	TeslaNoMFALogic(OkHttpClient okHttpClient, String logPrefix) {
		this.okHttpClient = okHttpClient;
		this.logPrefix = logPrefix;
	}
	
	            
	/**
	 * This login only fetches and remembers the access and refresh tokens needed for further actions.
	 * You may also set these manually using setTokens
	 * @return 
	 */
	Tokens login(String username, String password) {
        
        try {
        	// construct URL making sure any encoding is done right
        	// https://auth.tesla.com/oauth2/v3/authorize?client_id=... 
    		String codeVerifier = TeslaMFALogic.generateCodeVerifier();
        	HttpUrl url = new HttpUrl.Builder() // TBEERNOT
        		    .scheme("https")
        		    .host("auth.tesla.com")
        		    .addPathSegment("oauth2")
        		    .addPathSegment("v1") 
        		    .addPathSegment("authorize")
        		    .addQueryParameter("client_id", "teslaweb")
        		    .addQueryParameter("redirect_uri", "https://www.tesla.com/nl_NL/openid-connect/generic")
        		    .addQueryParameter("response_type", "code")
        		    .addQueryParameter("scope", "openid email profile")
        		    .addQueryParameter("state", "TeslaTasks")
        		    .build();
        	
        	// This is Tesla's MFA process
			Map<String, String> loginHiddenFields = processMFALoginPage(url);
			String authorizationCode = attemptMFALogin(username, password, url, loginHiddenFields);
			Tokens oauthTokens = obtainMFAOAuthTokens(codeVerifier, url, authorizationCode);
			Tokens apiTokens = obtainAPITokensUsingMFA(oauthTokens.accessToken);
			
			// we need to oauthToken to do the refresh of the api accesstoken
			apiTokens = new Tokens(apiTokens.accessToken, oauthTokens.refreshToken, apiTokens.createdAt, apiTokens.expiresIn);
			
			return apiTokens;
        } 
        catch (IOException e) {
            throw new RuntimeException(e);
        }
	}

	/* */
	private Map<String, String> processMFALoginPage(HttpUrl authorizeURL) throws IOException {
		Request request = new Request.Builder()
	            .url(authorizeURL)
	            .get()
	            .build();
		try (
			Response response = okHttpClient.newCall(request).execute();
			ResponseBody responseBody = response.body();
		) {
			TeslaMFALogic.failIfNotSuccesful(response);
			String content = responseBody.string();

			// find all hidden inputs
			Map<String, String> loginHiddenFields = new HashMap<>();
			Pattern inputNamePattern = Pattern.compile("name=\"([^\"]+)");
			Pattern inputValuePattern = Pattern.compile("value=\"([^\"]*)\"");
			Matcher matcher = Pattern.compile("<input type=\"hidden\" [^>]+>").matcher(content);
	        while (matcher.find()) {
	            String input = matcher.group();

	            // name
	            Matcher inputNameMatcher = inputNamePattern.matcher(input);
	            String name = (!inputNameMatcher.find() ? null : inputNameMatcher.group().substring(6));
	            
	            // value
	            Matcher inputValueMatcher = inputValuePattern.matcher(input);
	            String value = (!inputValueMatcher.find() ? null : inputValueMatcher.group().substring(7).replace("\"", ""));
	            
	            // remember
				logger.trace("{}hidden field: {}={}", logPrefix, name, value);
				// the first set of fiels is the login, we need those, not the second
				if (!loginHiddenFields.containsKey(name)) {
					loginHiddenFields.put(name, value);
				}
	        }
			return loginHiddenFields;
		}
	}

	/* */
	private String attemptMFALogin(String username, String password, HttpUrl authorizeURL, Map<String, String> loginHiddenFields) 
	throws IOException {
		
		// This time the client should not follow redirects, because we need the location of the redirect
		OkHttpClient nonRedirectionOkHttpClient = this.okHttpClient.newBuilder().followRedirects(false).build();
        
	    // construct form populated with hidden inputs plus credentials
	    FormBody.Builder formBuilder = new FormBody.Builder() // sends the fields in the body as key1=value1&key2=value2
			.add("identity", username)
			.add("credential", password);
	    for (Map.Entry<String, String> hiddenField : loginHiddenFields.entrySet()) {
	    	formBuilder.add(hiddenField.getKey(), hiddenField.getValue());
	    }
	    
	    // post the form
	    Request request = new Request.Builder()
	            .url(authorizeURL)
	            .post(formBuilder.build())
	            .build();
		try (
			Response response = nonRedirectionOkHttpClient.newCall(request).execute();
			ResponseBody responseBody = response.body();
		) {
			// should return 302
			String location = response.header("location");
			logger.trace("{}location={}", logPrefix, location);
			Matcher matcher = Pattern.compile("code=([^&]*)&").matcher(location);
			String authorizationCode = (matcher.find() ? matcher.group().substring(5).replace("&", "") : "");
			logger.trace("{}authorizationCode={}", logPrefix, authorizationCode);
			return authorizationCode;
		}	
	}

	/* */
	private Tokens obtainMFAOAuthTokens(String codeVerifier, HttpUrl authorizeURL, String authorizationCode) throws IOException {
		// url to fetch token from
		HttpUrl url = new HttpUrl.Builder()
			    .scheme("https")
			    .host("auth.tesla.com")
			    .addPathSegment("oauth2")
			    .addPathSegment("v3")
			    .addPathSegment("token")
			    .build();

		// request body is JSON
	    JsonObject requestJsonObject = new JsonObject();
	    requestJsonObject.addProperty("grant_type", "authorization_code");
	    requestJsonObject.addProperty("client_id", "teslaweb");
	    requestJsonObject.addProperty("code_verifier", codeVerifier);
	    requestJsonObject.addProperty("code", authorizationCode);
	    requestJsonObject.addProperty("redirect_uri", "https://www.tesla.com/nl_NL/openid-connect/generic");
	    
	    // post the form
	    Request request = new Request.Builder()
	            .url(url)
	            .post(RequestBody.create(gson.toJson(requestJsonObject), JsonMediaType))
	            .build();
		try (
			Response response = okHttpClient.newCall(request).execute();
			ResponseBody responseBody = response.body();
		) {
			TeslaMFALogic.failIfNotSuccesful(response);
			String content = responseBody.string();
			
			JsonObject responseJsonObject = gson.fromJson(content, JsonObject.class);
			TeslaMFALogic.failOnError(responseJsonObject);
			Tokens tokens = TeslaMFALogic.createTokensFromJsonObject(responseJsonObject);
			logger.trace("{}oauthTokens={}", logPrefix, tokens);
			return tokens;
		}
	}

	/* */
	private Tokens obtainAPITokensUsingMFA(String shortlivedAccessToken) throws IOException {
		Tokens tokens;
		{		
			// url to fetch token from
			HttpUrl url = new HttpUrl.Builder()
				    .scheme("https")
				    .host("owner-api.teslamotors.com")
				    .addPathSegment("oauth")
				    .addPathSegment("token")
				    .build();

			// request body is JSON
		    JsonObject requestJsonObject = new JsonObject();
		    requestJsonObject.addProperty("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
		    requestJsonObject.addProperty("client_id", CLIENT_ID);
//		    requestJsonObject.addProperty("client_secret", CLIENT_SECRET);
		    
		    // post the form
		    Request request = new Request.Builder()
		            .url(url)
					.addHeader("Authorization", "Bearer " + shortlivedAccessToken) 
		            .post(RequestBody.create(gson.toJson(requestJsonObject), JsonMediaType))
		            .build();
			try (
				//
				// This is not working and returns a 401 "unsupported SSO app"
				///
				Response response = okHttpClient.newCall(request).execute();
				ResponseBody responseBody = response.body();
			) {
				TeslaMFALogic.failIfNotSuccesful(response);
				String content = responseBody.string();
				
				JsonObject responseJsonObject = gson.fromJson(content, JsonObject.class);
				tokens = TeslaMFALogic.createTokensFromJsonObject(responseJsonObject);
			}
		}
		return tokens;
	}
}
