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
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tbee.tesla.dto.Tokens;

import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import kong.unirest.Config;
import kong.unirest.HttpRequest;
import kong.unirest.HttpRequestSummary;
import kong.unirest.HttpResponse;
import kong.unirest.Interceptor;
import kong.unirest.Unirest;
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
class TeslaMFALogic {
	static final Logger logger = LoggerFactory.getLogger(TeslaMFALogic.class);
	
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
	TeslaMFALogic(OkHttpClient okHttpClient, String logPrefix) {
		this.okHttpClient = okHttpClient;
		this.logPrefix = logPrefix;
	}
	
	            
	/**
	 * This login only fetches and remembers the access and refresh tokens needed for further actions.
	 * You may also set these manually using setTokens
	 * @return 
	 */
	Tokens login(String username, String password, String passcode) {
        
        try {
        	// construct URL making sure any encoding is done right
        	// https://auth.tesla.com/oauth2/v3/authorize?client_id=... 
    		String codeVerifier = generateCodeVerifier();
            String codeChallenge = computeChallenge(codeVerifier);
        	HttpUrl url = new HttpUrl.Builder()
        		    .scheme("https")
        		    .host("auth.tesla.com")
        		    .addPathSegment("oauth2")
        		    .addPathSegment("v1")
        		    .addPathSegment("authorize")
        		    .addQueryParameter("code_challenge", codeChallenge)
        		    .addQueryParameter("code_challenge_method", "S256")
        		    .addQueryParameter("client_id", "ownership")
        		    .addQueryParameter("response_type", "code")
        		    .addQueryParameter("scope", "offline_access openid ou_code email")
        		    .addQueryParameter("redirect_uri", "https://www.tesla.com/teslaaccount/owner-xp/auth/callback")
        		    .addQueryParameter("state", "Mdhhp1E5e3nIpj4bjq1EPCtebWlph10b2ZOwPvEq8jI")
        		    .addQueryParameter("audience", "https://ownership.tesla.com/")
        		    .addQueryParameter("locale", "nl-NL")
        		    .build();	
//        	https://auth.tesla.com/oauth2/v1/authorize?redirect_uri=https://www.tesla.com/teslaaccount/owner-xp/auth/callback&response_type=code&client_id=ownership&scope=offline_access%20openid%20ou_code%20email&audience=https%3A%2F%2Fownership.tesla.com%2F&locale=nl-nl        	
        	
        	Unirest.config()
		        .socketTimeout(500)
		        .connectTimeout(1000)
		        .concurrency(10, 5)
		        //.proxy(new Proxy("https://proxy"))
		        .setDefaultHeader("Accept", "application/json")
		        .followRedirects(true)
		        .enableCookieManagement(true)
		        .interceptor(new Interceptor() {
		            public void onRequest(HttpRequest<?> request, Config config) {
//		            	Optional<Body> body = request.getBody();
//		            	if (body.isPresent()) {
//		            		System.out.println(">> " + body.get());
//		            	}
		            }

		            public void onResponse(HttpResponse<?> response, HttpRequestSummary request, Config config) {
		            	System.out.println(">>>>\n" + request.asString() + "\n>>>>>");
		            	System.out.println("<<<<\n" + response.getBody() + "\n<<<<");
		            }
		        });
        	
        	// This is Tesla's MFA process
			Map<String, String> loginHiddenFields = requestLoginPage(url);
			String transactionId = loginHiddenFields.get("transaction_id");
			attemptMFALogin(username, password, url, loginHiddenFields);
			List<String> factorIds = obtainMFAFactorIds(transactionId, url);
if (1<2) System.exit(0);			
			verifyMFAPasscode(passcode, url, transactionId, factorIds);
			String authorizationCode = obtainMFAAuthorizationCode(url, transactionId);
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
	private Map<String, String> requestLoginPage(HttpUrl authorizeURL) throws IOException {
		Request request = new Request.Builder()
	            .url(authorizeURL)
	            .get()
	            .build();
		try (
			Response response = okHttpClient.newCall(request).execute();
			ResponseBody responseBody = response.body();
		) {
			failIfNotSuccesful(response);
			String content = responseBody.string();
			
			
			
//		{
//            GetRequest getRequest = Unirest.get("https://auth.tesla.com/oauth2/v1/authorize")            	
//    	            .queryString("client_id", "ownership")
//    	            .queryString("response_type", "code")
//    	            .queryString("scope", "offline_access openid ou_code email")
//        		    .queryString("redirect_uri", "https://www.tesla.com/teslaaccount/owner-xp/auth/callback")
//        		    .queryString("audience", "https://ownership.tesla.com/")
//    	            .queryString("state", "Mdhhp1E5e3nIpj4bjq1EPCtebWlph10b2ZOwPvEq8jI")    		    
//    	            .queryString("locale", "nl-NL");
//			HttpResponse<String> response = getRequest.asString();
//			failIfNotSuccesful(response);
//			String content = response.getBody();

			
			
			
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
				// some fields are used twice, we need the first one
				if (!loginHiddenFields.containsKey(name)) {
					loginHiddenFields.put(name, value);
				}
	        }
			return loginHiddenFields;
		}
	}

	/* */
	private void attemptMFALogin(String username, String password, HttpUrl authorizeURL, Map<String, String> loginHiddenFields) 
	throws IOException {
		
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
			Response response = okHttpClient.newCall(request).execute();
			ResponseBody responseBody = response.body();
		) {
			failIfNotSuccesful(response);
		}	
		
		
		
//        MultipartBody request = Unirest.post("https://auth.tesla.com/oauth2/v1/authorize")            	
//	            .queryString("client_id", "ownership")
//	            .queryString("response_type", "code")
//	            .queryString("scope", "offline_access openid ou_code email")
//	            .queryString("redirect_uri", "https://www.tesla.com/teslaaccount/owner-xp/auth/callback")
//	            .queryString("audience", "https://ownership.tesla.com/")
//	            .queryString("state", "Mdhhp1E5e3nIpj4bjq1EPCtebWlph10b2ZOwPvEq8jI")    		    
//	            .queryString("locale", "nl-NL")
//				.field("identity", username)
//				.field("credential", password);
//	            ;	            
////loginHiddenFields.put("_phase", "authenticate");
//	    for (Map.Entry<String, String> hiddenField : loginHiddenFields.entrySet()) {
//	    	request.field(hiddenField.getKey(), hiddenField.getValue());
//		}
//	    HttpResponse<Void> response = request.asEmpty();
//	    failIfNotSuccesful(response);
	}

	/* */
	private List<String> obtainMFAFactorIds(String transactionId, HttpUrl authorizeURL) throws IOException {

		// construct URL making sure any encoding is done right
		// https://auth.tesla.com//oauth2/v1/authorize/mfa/factors?transaction_id=9EYDgZpp
		HttpUrl url = new HttpUrl.Builder()
			    .scheme("https")
			    .host("auth.tesla.com")
			    .addPathSegment("oauth2")
			    .addPathSegment("v1")
			    .addPathSegment("authorize")
			    .addPathSegment("mfa")
			    .addPathSegment("factors")
			    .addQueryParameter("transaction_id", transactionId)
			    .build();

	    // do a get
	    Request request = new Request.Builder()
	    		.get()
	            .url(url)
	            .build();
		try (
			Response response = okHttpClient.newCall(request).execute();
			ResponseBody responseBody = response.body();
		) {
			failIfNotSuccesful(response);
			String content = responseBody.string();
			
			JsonObject responseJsonObject = gson.fromJson(content, JsonObject.class);
			failOnError(responseJsonObject);
		
			List<String> factorIds = new ArrayList<>();
			JsonArray jsonArray = responseJsonObject.getAsJsonArray("data");
			for (int i = 0; i < jsonArray.size(); i++) { 
				String factorId = jsonArray.get(i).getAsJsonObject().get("id").getAsString();
				logger.trace("{}factorId={}", logPrefix, factorId);
				factorIds.add(factorId);
			}
			return factorIds;
			
			
//		{
//            GetRequest getRequest = Unirest.get("https://auth.tesla.com/oauth2/v1/authorize/mfa/factors")
//    	            .queryString("transaction_id", "transactionId");
//			HttpResponse<JsonNode> response = getRequest.asJson();
//			failIfNotSuccesful(response);
//			JsonNode jsonNode = response.getBody();			
//					
//			List<String> factorIds = new ArrayList<>();
//			JSONArray jsonArray = jsonNode.getArray();
//			for (int i = 0; i < jsonArray.length(); i++) { 
//				String factorId = jsonArray.getJSONObject(i).getString("id");
//				logger.trace("{}factorId={}", logPrefix, factorId);
//				factorIds.add(factorId);
//			}
//			return factorIds;
		}	
	}

	/* */
	private void verifyMFAPasscode(String passcode, HttpUrl authorizeURL, String transactionId, List<String> factorIds)
	throws IOException {
		
		// construct URL making sure any encoding is done right
		// https://auth.tesla.com//oauth2/v1/authorize/mfa/verify 
		HttpUrl url = new HttpUrl.Builder()
			    .scheme("https")
			    .host("auth.tesla.com")
			    .addPathSegment("oauth2")
			    .addPathSegment("v1")
			    .addPathSegment("authorize")
			    .addPathSegment("mfa")
			    .addPathSegment("verify")
			    .build();

		// The factor id indicates what factor (device) was used to generate the passcode with.
		// But apparently they can just be looped over until one factor marks the passcode as valid
		boolean passcodePassed = false;
		for (String factorId : factorIds) {
			
			// construct json
		    JsonObject requestJsonObject = new JsonObject();
		    requestJsonObject.addProperty("transaction_id", transactionId);
		    requestJsonObject.addProperty("factor_id", factorId);
		    requestJsonObject.addProperty("passcode", passcode);
		    
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
				boolean approved = responseJsonObject.getAsJsonObject("data").get("approved").getAsBoolean();
				logger.trace("{}approved={}", logPrefix, approved);	
				boolean valid = responseJsonObject.getAsJsonObject("data").get("valid").getAsBoolean();
				logger.trace("{}valid={}", logPrefix, valid);
				boolean flagged = responseJsonObject.getAsJsonObject("data").get("flagged").getAsBoolean();
				logger.trace("{}flagged={}", logPrefix, flagged);
				
				if (approved && valid && !flagged) {
					logger.trace("{}passcode passed on factor {}", logPrefix, factorId);
					passcodePassed = true;
					break;
				}
			}
		}
		if (!passcodePassed) {
			throw new RuntimeException("Passcode was not approved or valid");
		}
	}

	/* */
	private String obtainMFAAuthorizationCode(HttpUrl authorizeURL, String transactionId) throws IOException {

		// This time the client should not follow redirects, because we need the location of the redirect
		OkHttpClient nonRedirectionOkHttpClient = this.okHttpClient.newBuilder().followRedirects(false).build();
        
		// construct form 
	    FormBody.Builder formBuilder = new FormBody.Builder() // sends the fields in the body as key1=value1&key2=value2
			.add("transaction_id", transactionId);
	    
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
	    requestJsonObject.addProperty("client_id", "ownerapi");
	    requestJsonObject.addProperty("code_verifier", codeVerifier);
	    requestJsonObject.addProperty("code", authorizationCode);
	    requestJsonObject.addProperty("redirect_uri", "https://auth.tesla.com/void/callback");
	    
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
			Tokens tokens = createTokensFromJsonObject(responseJsonObject);
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
		    requestJsonObject.addProperty("client_secret", CLIENT_SECRET);
		    
		    // post the form
		    Request request = new Request.Builder()
		            .url(url)
					.addHeader("Authorization", "Bearer " + shortlivedAccessToken) 
		            .post(RequestBody.create(gson.toJson(requestJsonObject), JsonMediaType))
		            .build();
			try (
				Response response = okHttpClient.newCall(request).execute();
				ResponseBody responseBody = response.body();
			) {
				failIfNotSuccesful(response);
				String content = responseBody.string();
				
				JsonObject responseJsonObject = gson.fromJson(content, JsonObject.class);
				tokens = createTokensFromJsonObject(responseJsonObject);
			}
		}
		return tokens;
	}
	
	/* */
	static void failIfNotSuccesful(Response response) {
		if (!response.isSuccessful()) {
			throw new RuntimeException("Request not succesful: "  + response);
		}
	}
	static void failIfNotSuccesful(kong.unirest.HttpResponse response) {
		if (response.getStatus() != 200) {
			throw new RuntimeException("Request not succesful: "  + response.getStatus() + " "  + response.getStatusText());
		}
	}

	/* */
	static void failOnError(JsonObject jsonObject) {
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
	static String generateCodeVerifier() {
		
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
	static String computeChallenge(String verifier) {
		String hash = Hashing.sha256()
				  .hashString(verifier, StandardCharsets.UTF_8)
				  .toString();
		String encoded = encodeMFABase64(hash);
        return encoded;
    }
	
	/* */
	static String encodeMFABase64(String s) {
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
