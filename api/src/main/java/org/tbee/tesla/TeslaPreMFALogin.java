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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tbee.tesla.dto.Tokens;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * The MFA based login flow
 */
class TeslaPreMFALogin {
	static final Logger logger = LoggerFactory.getLogger(TeslaPreMFALogin.class);
	
	// For HTTP
	private final Gson gson = new Gson();
	private final OkHttpClient okHttpClient;
	private final MediaType JsonMediaType = MediaType.parse("application/json; charset=utf-8");
	
	// For improved logging 
	private final String logPrefix;

	private final TeslaLoginHelper helper;	

	
	/**
	 * 
	 */
	TeslaPreMFALogin(OkHttpClient okHttpClient, String logPrefix) {
		this.okHttpClient = okHttpClient;
		this.logPrefix = logPrefix;
		this.helper = new TeslaLoginHelper(okHttpClient, logPrefix);
	}
	
	            
	/**
	 * This login only fetches and remembers the access and refresh tokens needed for further actions.
	 * This is a non-MFA (multi factor authentication) login.
	 * You may also set these manually using setTokens
	 * @param username
	 * @param password
	 * @return 
	 */
	Tokens login(String username, String password) {
        // Create the request body
        JsonObject requestJsonObject = new JsonObject();
        requestJsonObject.addProperty("grant_type", "password");
        requestJsonObject.addProperty("email", username);
        requestJsonObject.addProperty("password", password);
        return fetchTokens(requestJsonObject);
	}
	
	/*
	 * The same handling for login and refreshTokens
	 */
	private Tokens fetchTokens(JsonObject requestJsonObject) {
        try {
            requestJsonObject.addProperty("client_id", helper.CLIENT_ID);
            requestJsonObject.addProperty("client_secret", helper.CLIENT_SECRET);
            String requestBody = gson.toJson(requestJsonObject);

            // Call the REST service
			Request request = new Request.Builder()
	                .url(TeslaAPI.URL_BASE + "oauth/token")
					.post(RequestBody.create(requestBody, JsonMediaType))
	                .build();
			try (
				Response response = okHttpClient.newCall(request).execute();
				ResponseBody responseBody = response.body();
			) {
				if (!response.isSuccessful()) {
					return null;
				}
	
				// Parse the result and remember the two tokens
				String responseBodyContent = responseBody.string();
				JsonObject responseJsonObject = gson.fromJson(responseBodyContent, JsonObject.class);
				Tokens tokens = helper.createTokensFromJsonObject(responseJsonObject);
				return tokens;
			}
        } 
        catch (IOException e) {
            throw new RuntimeException(e);
        }
	}
	
	/**
	 * This method fetches new access and refresh tokens
	 * @return 
	 */
	Tokens refreshTokens(Tokens tokens) {
        // Create the request body
        JsonObject requestJsonObject = new JsonObject();
        requestJsonObject.addProperty("grant_type", "refresh_token");
        requestJsonObject.addProperty("refresh_token", tokens.refreshToken);
        return fetchTokens(requestJsonObject);
    }
}
