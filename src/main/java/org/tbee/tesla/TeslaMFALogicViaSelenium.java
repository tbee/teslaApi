package org.tbee.tesla;

import java.io.File;

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

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tbee.tesla.dto.Tokens;

import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
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
class TeslaMFALogicViaSelenium {
	static final Logger logger = LoggerFactory.getLogger(TeslaMFALogicViaSelenium.class);
	
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
	TeslaMFALogicViaSelenium(OkHttpClient okHttpClient, String logPrefix) {
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
//        	// construct the login page URL (it is used 3 times)
//    		String codeVerifier = generateCodeVerifier();
//            String codeChallenge = computeChallenge(codeVerifier);
//        	HttpUrl authorizeUrl = new HttpUrl.Builder()
//        		    .scheme("https")
//        		    .host("auth.tesla.com")
//        		    .addPathSegment("oauth2")
//        		    .addPathSegment("v1")
//        		    .addPathSegment("authorize")
//        		    .addQueryParameter("code_challenge", codeChallenge)
//        		    .addQueryParameter("code_challenge_method", "S256")
//        		    .addQueryParameter("client_id", "ownership")
//        		    .addQueryParameter("response_type", "code")
//        		    .addQueryParameter("scope", "offline_access openid ou_code email")
//        		    .addQueryParameter("redirect_uri", "https://www.tesla.com/teslaaccount/owner-xp/auth/callback")
//        		    .addQueryParameter("state", "Mdhhp1E5e3nIpj4bjq1EPCtebWlph10b2ZOwPvEq8jI")
//        		    .addQueryParameter("audience", "https://ownership.tesla.com/")
//        		    .addQueryParameter("locale", "nl-NL")
//        		    .build();	
//        	// https://auth.tesla.com/oauth2/v1/authorize?redirect_uri=https://www.tesla.com/teslaaccount/owner-xp/auth/callback&response_type=code&client_id=ownership&scope=offline_access%20openid%20ou_code%20email&audience=https%3A%2F%2Fownership.tesla.com%2F&locale=nl-nl        	
//        	        	
//        	// This is Tesla's MFA process
//			Map<String, String> loginHiddenFields = requestLoginPage(authorizeUrl);
//			String transactionId = loginHiddenFields.get("transaction_id");
//			attemptMFALogin(username, password, authorizeUrl, loginHiddenFields);
//			List<String> factorIds = obtainMFAFactorIds(transactionId);
//			verifyMFAPasscode(passcode, transactionId, factorIds);
//			String authorizationCode = obtainMFAAuthorizationCode(authorizeUrl, transactionId);
//			Tokens oauthTokens = obtainMFAOAuthTokens(codeVerifier, authorizationCode);
//			Tokens apiTokens = obtainAPITokensUsingMFA(oauthTokens.accessToken);
//			
//			// we need to oauthToken to do the refresh of the api accesstoken
//			apiTokens = new Tokens(apiTokens.accessToken, oauthTokens.refreshToken, apiTokens.createdAt, apiTokens.expiresIn);
//			
//			return apiTokens;
        	
        	
			System.setProperty("webdriver.gecko.driver", new File("./src/main/drivers/geckodriver-0.30.0").getAbsolutePath());
        	WebDriver driver = new FirefoxDriver();
//			System.setProperty("webdriver.chrome.driver", new File("./src/main/drivers/chromedriver-95.0.4638.17").getAbsolutePath());
//			ChromeOptions options = new ChromeOptions();
//			options.addArguments("start-maximized"); // open Browser in maximized mode
//			options.addArguments("disable-infobars"); // disabling infobars
//			options.addArguments("--disable-extensions"); // disabling extensions
//			options.addArguments("--disable-gpu"); // applicable to windows os only
//			options.addArguments("--disable-dev-shm-usage"); // overcome limited resource problems
//			options.addArguments("--no-sandbox"); // Bypass OS security model
//			WebDriver driver = new ChromeDriver(options);
            WebDriverWait wait = new WebDriverWait(driver, 10);
            try {
            	// goto the login page
                driver.get("https://www.tesla.com/nl_nl/teslaaccount");
                
                // populate the login page and submit
				wait.until(ExpectedConditions.presenceOfElementLocated(By.id("form-input-identity"))).sendKeys(username);
				wait.until(ExpectedConditions.presenceOfElementLocated(By.id("form-input-credential"))).sendKeys(password);
				wait.until(ExpectedConditions.presenceOfElementLocated(By.id("form-submit-continue"))).click();

				// possible recaptcha -> this will result in a image puzzle to be solved. 
				String page = driver.getPageSource();
				if (page.contains("recaptcha")) {
					wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(0));

					// click recaptcha checkbox
					wait.until(ExpectedConditions.presenceOfElementLocated(By.className("recaptcha-checkbox"))).click();
					
					// ... solve image puzzle
					
					driver.switchTo().parentFrame();
				}
				
				// passcode
				wait.until(ExpectedConditions.presenceOfElementLocated(By.id("form-input-passcode"))).sendKeys(passcode);
				wait.until(ExpectedConditions.presenceOfElementLocated(By.id("form-submit"))).click();
				
				Thread.sleep(50000);
            } 
            finally {
                driver.quit();
            }
            
        	
        	System.exit(0); return null;
        } 
        catch (Exception e) {
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
				// The two sets overlap so we can just collect them all, but one field has different values and we need the first one
				if (!loginHiddenFields.containsKey(name)) {
					loginHiddenFields.put(name, value);
				}
	        }
			logger.trace("{}hidden fields: {}", logPrefix, loginHiddenFields);
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
//	            // TBEERNOT all headers copied from webbrowser
//	            .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
//	        	.addHeader("Accept-Encoding", "gzip, deflate, br")
//	        	.addHeader("Accept-Language", "en-US,en;q=0.5")
//	        	.addHeader("Connection", "keep-alive")
//	        	.addHeader("Content-Length", "157")
//	        	.addHeader("Content-Type", "application/x-www-form-urlencoded")
//	        	// handled by okhttp: .addHeader("Cookie", "_ga_KFP8T9JWYJ=GS1.1.1626536715.2.1.1626536790.0; _ga=GA1.2.839819551.1620560363; _abck=9FA9EEF1E8E6513E3DDA553019C710E0~0~YAAQDKQFF6wpetN7AQAA/+FY2wYg5EeVKSfJVbRwWeiVSClG9O/L+bzos0Ju+MFgdVZxrvrk0OJVmsrlL3UtwRUlcoeGTTYvtF+j0SBSko7zMpMmerS30FNGQ5XYhsAxwuyXdkoUML+HjMw1HSMAb82xkqylhmNomHHrzoE7paxhHyY88SgIdusQcObIEe5LFvVoTnWBK5H7IP/qeUXwskHBb+XrkiuV6LG8YfMFjrfUSMPyAJOQJRLNVl0Go7YiKjMhNyjaWunVEIXNwv7sSn0iTylsndEc1jkLkEHP7mXTn58gVzBMRW11g0zBCnJ5fHdwdKqrWTyHKzNVOYpuj9CmiC6piNp3581GWbQCiGJAWaDyxFL2jDgdQHDNGtgckNcou…28L2ShHx9afF6W2DZ/VG6xAetOcrkq2lwW6UwWngNoA0mCK1C1zPNd5ZSY74fKIOA04Qa4D+tj5VWPIOH5Nf2rr/fYfS1o1XZ9FLCHUUaBBB7ugUopv/hbHX+4EGtQyV/0oYZyw2aUi28YFzu0Yw1fnWRYHGZ04eMEMnhkLdsTw1Qn8TgIMiQqHGdiub9BqZ/47dy86oYO/AjXOnD1/o4LnyxJxPknR124o2QpzeM/mm4H7FQqAEbhPAqXimfIQPXiSiipGMNIxjbg==~3556933~3229249; AKA_A2=A; bm_sv=24A8C49300B77490D65D84FBC9335FF0~HcTy6fvR4hBy3zakV4rHln6Pj9SL/z0Dh1muNogCwks8M6dE5xcTOiVukb9/oZNjTLSCPuigEw18mxUp0Ly2jf0ZQ5Tnx4Ac3hoChNjXcVAtAj50Nn+xkduztoL5o9fv2RxeCaRIBWw8MNosC8tQ0OrBnZDReUnvLV7fQLHbMU0=")
//	        	.addHeader("Host", "auth.tesla.com")
//	        	.addHeader("Origin", "https://auth.tesla.com")
//	        	.addHeader("Referer", "https://auth.tesla.com/oauth2/v1/authorize?redirect_uri=https://www.tesla.com/teslaaccount/owner-xp/auth/callback&response_type=code&client_id=ownership&scope=offline_access%20openid%20ou_code%20email&audience=https%3A%2F%2Fownership.tesla.com%2F&locale=nl-nl")
//	        	.addHeader("Sec-Fetch-Dest", "document")
//	        	.addHeader("Sec-Fetch-Mode", "navigate")
//	        	.addHeader("Sec-Fetch-Site", "same-origin")
//	        	.addHeader("Sec-Fetch-User", "?1")
//	        	.addHeader("TE", "trailers")
//	        	.addHeader("Upgrade-Insecure-Requests", "1")
//	        	.addHeader("User-Agent", "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:92.0) Gecko/20100101 Firefox/92.0")
	        	.build();
		try (
			Response response = okHttpClient.newCall(request).execute();
			ResponseBody responseBody = response.body();
		) {
			failIfNotSuccesful(response);
		}	
	}

	/* */
	private List<String> obtainMFAFactorIds(String transactionId) throws IOException {

		// construct URL making sure any encoding is done right
		// https://auth.tesla.com//oauth2/v1/authorize/mfa/factors?transaction_id=9EYDgZpp
		HttpUrl url = new HttpUrl.Builder()
			    .scheme("https")
			    .host("auth.tesla.com")
			    .addPathSegment("oauth2")
			    .addPathSegment("v1") // v3 does not make a difference
			    .addPathSegment("authorize")
			    .addPathSegment("mfa")
			    .addPathSegment("factors")
			    .addQueryParameter("transaction_id", transactionId)
			    .build();				

	    // do a get
	    Request request = new Request.Builder()
	    		.get()
	            .url(url)
//	            // TBEERNOT all headers copied from webbrowser
//			    .addHeader("Accept", "application/json")
//	    		.addHeader("Accept-Encoding", "gzip, deflate, br")
//	    		.addHeader("Accept-Language", "en-US,en;q=0.5")
//	    		.addHeader("Connection" ,"keep-alive")
//	    		.addHeader("Content-Type", "application/json;charset=UTF-8")
//				// handled by okhttp: .addHeader("Cookie", "_ga_KFP8T9JWYJ=GS1.1.1626536715.2.1.1626536790.0; _ga=GA1.2.839819551.1620560363; _abck=9FA9EEF1E8E6513E3DDA553019C710E0~0~YAAQDKQFF0lPetN7AQAAIBFb2wbKi1GeSUqduNwMRTuxNKnBpiR29IGWo6pdTohRL71cwgmzib/LbGKrretaBqp7WnBWj8amDFyYs93ADtNiR5SM/8FM9Iyf6Ze2DlhSPJnhxiKwlwc7MRlkLaFZZnNV+wbIQC7mZ70/qYp3ZGWv2zTrhQ3798S5+Dea1fdQ00ieOoztYagelfM/FYewS39x+1Wb0ZM8PnEB/Mhpy1wIISG0j6iU0A6f8UeMk4uIBr/oBVfYVuYeYXnEdIVpEHVKN9R5z/P3OtlSXz0r8o/l83melSIDkMLyz590osCIE3raffovjO9cdU7+s5OEbd4uZchLINOz3h2ifk5+sGEvy8AMIXdgQ69NU4ZZyzRyO6vLU…28L2ShHx9afF6W2DZ/VG6xAetOcrkq2lwW6UwWngNoA0mCK1C1zPNd5ZSY74fKIOA04Qa4D+tj5VWPIOH5Nf2rr/fYfS1o1XZ9FLCHUUaBBB7ugUopv/hbHX+4EGtQyV/0oYZyw2aUi28YFzu0Yw1fnWRYHGZ04eMEMnhkLdsTw1Qn8TgIMiQqHGdiub9BqZ/47dy86oYO/AjXOnD1/o4LnyxJxPknR124o2QpzeM/mm4H7FQqAEbhPAqXimfIQPXiSiipGMNIxjbg==~3556933~3229249; AKA_A2=A; bm_sv=24A8C49300B77490D65D84FBC9335FF0~HcTy6fvR4hBy3zakV4rHln6Pj9SL/z0Dh1muNogCwks8M6dE5xcTOiVukb9/oZNjTLSCPuigEw18mxUp0Ly2jf0ZQ5Tnx4Ac3hoChNjXcVDswU8PkKt/fvgH+IP4aWQ428Pmqsj/sJwr4HZcvEOYW5mN+wKlsuof3TshCljM95w=")
//				.addHeader("Host", "auth.tesla.com")
//				.addHeader("Referer", "https://auth.tesla.com/oauth2/v1/authorize?redirect_uri=https://www.tesla.com/teslaaccount/owner-xp/auth/callback&response_type=code&client_id=ownership&scope=offline_access%20openid%20ou_code%20email&audience=https%3A%2F%2Fownership.tesla.com%2F&locale=nl-nl")
//				.addHeader("Sec-Fetch-Dest", "empty")
//				.addHeader("Sec-Fetch-Mode", "cors")
//				.addHeader("Sec-Fetch-Site", "same-origin")
//				.addHeader("TE", "trailers")
//				.addHeader("User-Agent", "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:92.0) Gecko/20100101 Firefox/92.0")
//				.addHeader("X-Requested-With", "XMLHttpRequest")
	            .build();
		try (
			Response response = okHttpClient.newCall(request).execute();
			ResponseBody responseBody = response.body();
		) {			
			// get the content as JSON
			failIfNotSuccesful(response);
			String content = responseBody.string();			
			JsonObject responseJsonObject = gson.fromJson(content, JsonObject.class);
			failOnError(responseJsonObject);
		
			// Extract the factor ids
			List<String> factorIds = new ArrayList<>();
			JsonArray jsonArray = responseJsonObject.getAsJsonArray("data");
			for (int i = 0; i < jsonArray.size(); i++) { 
				String factorId = jsonArray.get(i).getAsJsonObject().get("id").getAsString();
				logger.trace("{}factorId={}", logPrefix, factorId);
				factorIds.add(factorId);
			}
			return factorIds;
		}	
	}

	/* */
	private void verifyMFAPasscode(String passcode, String transactionId, List<String> factorIds)
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
	private Tokens obtainMFAOAuthTokens(String codeVerifier, String authorizationCode) throws IOException {
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
