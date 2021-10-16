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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tbee.tesla.dto.Tokens;

import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

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
class TeslaLoginHelper {
	static final Logger logger = LoggerFactory.getLogger(TeslaLoginHelper.class);
	
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
	TeslaLoginHelper(OkHttpClient okHttpClient, String logPrefix) {
		this.okHttpClient = okHttpClient;
		this.logPrefix = logPrefix;
	}
	
	/* */
	void failIfNotSuccesful(Response response) {
		if (!response.isSuccessful()) {
			throw new RuntimeException("Request not succesful: "  + response);
		}
	}

	/* */
	void failOnError(JsonObject jsonObject) {
		JsonObject errorJsonObject = jsonObject.getAsJsonObject("error");
		if (errorJsonObject == null) {
			return;
		}
		String message = errorJsonObject.get("message").getAsString();
		if (message == null) {
			return;
		}
		throw new RuntimeException(message);
	}
	
	/* */
	String generateCodeVerifier() {
		
		// random 43-128 long string, no idea yet what this is used for
		String base = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
		StringBuffer stringBuffer = new StringBuffer();
		Random random = new Random();
		for (int i = 0; i < 86; i++) {
			int idx = random.nextInt(base.length());
			stringBuffer.append(base.substring(idx, idx + 1));
		}
		String key = stringBuffer.toString();
		String encoded = encodeMFABase64(key);
        return encoded;
    }
	
	/* */
	String computeChallenge(String verifier) {
		String hash = Hashing.sha256()
				  .hashString(verifier, StandardCharsets.UTF_8)
				  .toString();
		String encoded = encodeMFABase64(hash);
        return encoded;
    }
	
	/* */
	String encodeMFABase64(String s) {
		String encoded = Base64.getEncoder().encodeToString(s.getBytes());
		encoded = encoded
	            .replace("+", "-")
	            .replace("/", "_")
	            .replace("=", "")
	            .trim();
		return encoded;
	}
	
	/**
	 * This method fetches (and remembers/replaces) new access and refresh tokens
	 * @return 
	 */
	Tokens refreshTokens(Tokens oldTokens) {
		
		// url to fetch token from
		HttpUrl url = new HttpUrl.Builder()
			    .scheme("https")
			    .host("auth.tesla.com")
			    .addPathSegment("oauth2")
			    .addPathSegment("v1")
			    .addPathSegment("token")
			    .build();

		// request body is JSON
	    JsonObject requestJsonObject = new JsonObject();
	    requestJsonObject.addProperty("grant_type", "refresh_token");
        requestJsonObject.addProperty("refresh_token", oldTokens.refreshToken);
	    requestJsonObject.addProperty("client_id", "ownerapi");
	    
	    // post the form
	    Request request = new Request.Builder()
	            .url(url)
	            .post(RequestBody.create(gson.toJson(requestJsonObject), JsonMediaType))
	            .build();
		try (
			Response response = okHttpClient.newCall(request).execute();
			ResponseBody responseBody = response.body();
		) {
			failIfNotSuccesful(response);
			String content = responseBody.string();
			
			JsonObject responseJsonObject = gson.fromJson(content, JsonObject.class);
			failOnError(responseJsonObject);
			Tokens newTokens = createTokensFromJsonObject(responseJsonObject);
			logger.trace("{}apiTokens={}", logPrefix, oldTokens);
			
			// you don't get a new refresh token, so we need to use the old one
			newTokens = new Tokens(newTokens.accessToken, oldTokens.refreshToken, newTokens.expiresAt, newTokens.expiresIn);
			
			return newTokens;
		}
        catch (IOException e) {
            throw new RuntimeException(e);
        }
	}
	
	/* */
	static Tokens createTokensFromJsonObject(JsonObject jsonObject) {
		String accessToken = jsonObject.get("access_token").getAsString();
		String refreshToken = null;
		if (jsonObject.get("refresh_token") != null) {
			refreshToken = jsonObject.get("refresh_token").getAsString();
		}
		Instant createdAt = Instant.now();
		if (jsonObject.get("created_at") != null) {
			long createdAtValue = jsonObject.get("created_at").getAsLong();
			createdAt = Instant.ofEpochSecond(createdAtValue);
		}
		long expiresIn = jsonObject.get("expires_in").getAsLong();
		return new Tokens(accessToken, refreshToken, createdAt, expiresIn);		
	}
}
