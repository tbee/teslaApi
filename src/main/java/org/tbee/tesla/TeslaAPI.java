package org.tbee.tesla;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tbee.tesla.dto.ChargeState;
import org.tbee.tesla.dto.ClimateState;
import org.tbee.tesla.dto.DriveState;
import org.tbee.tesla.dto.GUISettings;
import org.tbee.tesla.dto.Tokens;
import org.tbee.tesla.dto.Vehicle;

import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.JavaNetCookieJar;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

/**
 * A Java implementation of Tesla REST API based on https://www.teslaapi.io/
 * 
 * This wrapper is stateful because it remembers the oauth tokens, and thus an instance can be used for one account at a time.
 * The individual vehicles within an account are accessed as a parameter to the methods.
 * This implementation tries to stay as close to Tesla's API as possible, with a few small additions:
 * - There is a wakUp method that has a retry logic added, it verifies communication by trying to fetch the shift state.
 * - The heatSeat method has an alias using enums for readability, because the seat numbering has a gap (0,1,2,4,5).
 * - Commands interpret the response's reason to see if it actually is not an error; like "already_set" when trying to set a temperature.
 * 
 * Example usage:
 * 		TeslaAPI teslaAPI = new TeslaAPI();
 * 		teslaAPI.login(TESLA_USERNAME, TESLA_PASSWORD, TESLA_MFA_PASSCODE); // or use setTokens
 * 		List<Vehicle> vehicles = teslaAPI.getVehicles();
 * 		String vehicleId = vehicles.get(0).id;
 * 		teslaAPI.flashLights(vehicleId)
 */
public class TeslaAPI {
	private static final String CLIENT_SECRET = "c7257eb71a564034f9419ee651c7d0e5f7aa6bfbd18bafb5c5c033b093bb2fa3";
	private static final String CLIENT_ID = "81527cff06843c8634fdc09e8ac0abefb46ac849f38fe1e431c2ef2106796384";	

	static final Logger logger = LoggerFactory.getLogger(TeslaAPI.class);
	
    // API contants
    private static final String URL_BASE = "https://owner-api.teslamotors.com/";
    private static final String URL_VERSION = "api/1/";
    private static final String URL_VEHICLES = "vehicles/";
	private static final String HEADER_AUTHORIZATION = "Authorization";

    // For HTTP
	private final Gson gson = new Gson();
	private final OkHttpClient okHttpClient;
	private final MediaType JsonMediaType = MediaType.parse("application/json; charset=utf-8");
	
	// State
	private Tokens tokens = null;
	private String authorizationHeader = null;
	
	// For improved logging 
	private String logPrefix = "";

	
	/**
	 * 
	 */
	public TeslaAPI() {
		// Create a cookie store
		CookieManager cookieManager = new CookieManager();
		cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
		
		// Initialize the HTTP client
		okHttpClient = new OkHttpClient.Builder()
			.protocols(Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1))
	        .connectTimeout(30, TimeUnit.SECONDS)
	        .writeTimeout(30, TimeUnit.SECONDS)
	        .readTimeout(2, TimeUnit.MINUTES)
	        .cookieJar(new JavaNetCookieJar(cookieManager))
	        .addInterceptor(chain -> {
	        	Request request = chain.request();
	        	String requestBodyContent = "";
	        	if (request.body() != null) {
	                Buffer buffer = new Buffer();
	                request.newBuilder().build().body().writeTo(buffer); // create a copy request and use that to write the body
	                requestBodyContent = buffer.readUtf8();
	        	}
			    logger.trace("{}{} {}", logPrefix, request, requestBodyContent);
			    Response response = chain.proceed(request);
			    if (!response.isSuccessful()) {
				    logger.warn("{}{} {}", logPrefix, response, response.peekBody(10000).string());
			    }
			    else {
				    logger.trace("{}{} {}", logPrefix, response, response.peekBody(10000).string());			    	
			    }
			    return response;
			})
	        .build();
	}

	/**
	 * 
	 * @param s
	 */
	public void setLogPrefix(String s) {
		if (s == null) {
			throw new IllegalArgumentException("Cannot be null");
		}
		logPrefix = s;
	}
	public String getLogPrefix() {
		return logPrefix;
	}
	
	            
	/**
	 * This login only fetches and remembers the access and refresh tokens needed for further actions.
	 * You may also set these manually using setTokens
	 * @return 
	 */
	public Tokens login(String username, String password, String passcode) {
        
        try {
        	// construct URL making sure any encoding is done right
        	// https://auth.tesla.com/oauth2/v3/authorize?client_id=... 
    		String codeVerifier = generateCodeVerifier();
            String codeChallenge = computeChallenge(codeVerifier);
        	HttpUrl url = new HttpUrl.Builder()
        		    .scheme("https")
        		    .host("auth.tesla.com")
        		    .addPathSegment("oauth2")
        		    .addPathSegment("v3")
        		    .addPathSegment("authorize")
        		    .addQueryParameter("client_id", "ownerapi")
        		    .addQueryParameter("code_challenge", codeChallenge)
        		    .addQueryParameter("code_challenge_method", "S256")
        		    .addQueryParameter("redirect_uri", "https://auth.tesla.com/void/callback")
        		    .addQueryParameter("response_type", "code")
        		    .addQueryParameter("scope", "openid email offline_access")
        		    .addQueryParameter("state", "TeslaTasks")
        		    .build();
        	
        	// This is Tesla's MFA process
			Map<String, String> loginHiddenFields = processMFALoginPage(url);
			String transactionId = loginHiddenFields.get("transaction_id");
			attemptMFALogin(username, password, url, loginHiddenFields);
			List<String> factorIds = obtainMFAFactorIds(transactionId);
			verifyMFAPasscode(passcode, url, transactionId, factorIds);
			String authorizationCode = obtainMFAAuthorizationCode(url, transactionId);
			Tokens oauthTokens = obtainMFAOAuthTokens(codeVerifier, url, authorizationCode);
			Tokens apiTokens = obtainAPITokensUsingMFA(oauthTokens.accessToken);
			
			// we need to oauthToken to do the refresh of the api accesstoken
			apiTokens = new Tokens(apiTokens.accessToken, oauthTokens.refreshToken, apiTokens.createdAt, apiTokens.expiresIn);
			setTokens(apiTokens);
			
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
	            loginHiddenFields.put(name, value);
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
	}

	/*
	 * TBEERNOT TODO: support multiple factorIds. Can we simply loop through them in the verify until one is accepted?
	 */
	private List<String> obtainMFAFactorIds(String transactionId) throws IOException {

		// construct URL making sure any encoding is done right
		// https://auth.tesla.com/oauth2/v3/authorize?client_id=... 
		HttpUrl url = new HttpUrl.Builder()
			    .scheme("https")
			    .host("auth.tesla.com")
			    .addPathSegment("oauth2")
			    .addPathSegment("v3")
			    .addPathSegment("authorize")
			    .addPathSegment("mfa")
			    .addPathSegment("factors")
			    .addQueryParameter("transaction_id", transactionId)
			    .build();

	    // post the form
	    Request request = new Request.Builder()
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
		}	
	}

	/* */
	private void verifyMFAPasscode(String passcode, HttpUrl authorizeURL, String transactionId, List<String> factorIds)
	throws IOException {
		
		// construct URL making sure any encoding is done right
		// https://auth.tesla.com/oauth2/v3/authorize?client_id=... 
		HttpUrl url = new HttpUrl.Builder()
			    .scheme("https")
			    .host("auth.tesla.com")
			    .addPathSegment("oauth2")
			    .addPathSegment("v3")
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
	private void failIfNotSuccesful(Response response) {
		if (!response.isSuccessful()) {
			throw new RuntimeException("Request not succesful: "  + response);
		}
	}

	/* */
	private void failOnError(JsonObject jsonObject) {
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
	private String generateCodeVerifier() {
		
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
	private String computeChallenge(String verifier) {
		String hash = Hashing.sha256()
				  .hashString(verifier, StandardCharsets.UTF_8)
				  .toString();
		String encoded = encodeMFABase64(hash);
        return encoded;
    }
	
	/* */
	private String encodeMFABase64(String s) {
		String encoded = Base64.getEncoder().encodeToString(s.getBytes());
		encoded = encoded
	            .replace("+", "-")
	            .replace("/", "_")
	            .replace("=", "")
	            .trim();
		return encoded;
	}

	/**
	 * This login only fetches and remembers the access and refresh tokens needed for further actions.
	 * This is a non-MFA (multi factor authentication) login.
	 * You may also set these manually using setTokens
	 * @param username
	 * @param password
	 * @return 
	 */
	public Tokens login(String username, String password) {
        // Create the request body
        JsonObject requestJsonObject = new JsonObject();
        requestJsonObject.addProperty("grant_type", "password");
        requestJsonObject.addProperty("email", username);
        requestJsonObject.addProperty("password", password);
        return fetchTokensV1(requestJsonObject);
	}
	
	/*
	 * The same handling for login and refreshTokens
	 */
	private Tokens fetchTokensV1(JsonObject requestJsonObject) {
        try {
            requestJsonObject.addProperty("client_id", CLIENT_ID);
            requestJsonObject.addProperty("client_secret", CLIENT_SECRET);
            String requestBody = gson.toJson(requestJsonObject);

            // Call the REST service
			Request request = new Request.Builder()
	                .url(URL_BASE + "oauth/token")
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
				Tokens tokens = createTokensFromJsonObject(responseJsonObject);
				setTokens(tokens);
				return tokens;
			}
        } 
        catch (IOException e) {
            throw new RuntimeException(e);
        }
	}

	/**
	 * This method fetches (and remembers/replaces) new access and refresh tokens
	 * @return 
	 */
	public Tokens refreshTokens() {
		
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
	    requestJsonObject.addProperty("grant_type", "refresh_token");
        requestJsonObject.addProperty("refresh_token", tokens.refreshToken);
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
			Tokens tokens = createTokensFromJsonObject(responseJsonObject);
			logger.trace("{}apiTokens={}", logPrefix, tokens);
			
			// you don't get a new refresh token, so we need to use the old one
			tokens = new Tokens(tokens.accessToken, this.tokens.refreshToken, tokens.expiresAt, tokens.expiresIn);
			setTokens(tokens);
			
			return tokens;
		}
        catch (IOException e) {
            throw new RuntimeException(e);
        }
	}
	
	/* */
	private Tokens createTokensFromJsonObject(JsonObject jsonObject) {
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
	
	/**
	 * When we do not use the login method, provide the tokens manually.
	 * @param tokens
	 */
	public void setTokens(Tokens tokens) {
		this.tokens = tokens;
	    logger.debug("using tokens: {}{}", logPrefix, tokens);
		this.authorizationHeader = "Bearer " + tokens.accessToken;
	}
	public Tokens getTokens() {
		return tokens;
	}
	
	/**
	 * @return 
	 * 
	 */
	public List<Vehicle> getVehicles() {
		doTokensCheck();
		
        try {
            // Call the REST service
			Request request = new Request.Builder()
	                .url(URL_BASE + URL_VERSION + URL_VEHICLES)
	                .header(HEADER_AUTHORIZATION, authorizationHeader)
					.get()
	                .build();
			try (
					Response response = okHttpClient.newCall(request).execute();
					ResponseBody responseBody = response.body();
			) {
				if (!response.isSuccessful()) {
					return Collections.emptyList();
				}
	
				// Parse the result and build a list of vehicles
				// {"response":[{"id":242342423,"vehicle_id":123123123123,"vin":"12312312321","display_name":"Tarah", ...
				String responseBodyContent = responseBody.string();
				JsonObject responseJsonObject = gson.fromJson(responseBodyContent, JsonObject.class);
				List<Vehicle> vehicles = new ArrayList<>();
				responseJsonObject.get("response").getAsJsonArray().forEach((jsonElement) -> vehicles.add(new Vehicle(jsonElement.getAsJsonObject())));
				return vehicles;
			}
        } 
        catch (IOException e) {
            throw new RuntimeException(e);
        }
	}
    
    /**
     * Get a vehicle by its VIN
     * @param vin
     * @return
     */
    public Vehicle getVehicleByVIN(String vin) {
        List<Vehicle> vehicles = getVehicles();
        Vehicle vehicle = null;
        for (Vehicle vehicleCandidate : vehicles) {
        	if (vin.equals(vehicleCandidate.vin)) {
        		vehicle = vehicleCandidate;
        		break;
        	}
        }
        return vehicle;
    }
	
	/**
	 * @return null if everything is ok, or an error string if not
	 */
	public String wakeUp(String vehicleId) {
		doTokensCheck();
		
        try {
            // Call the REST service
			Request request = new Request.Builder()
	                .url(URL_BASE + URL_VERSION + URL_VEHICLES + vehicleId + "/wake_up")
	                .header(HEADER_AUTHORIZATION, authorizationHeader)
					.post(RequestBody.create("", JsonMediaType))
	                .build();
			try (
				Response response = okHttpClient.newCall(request).execute();
			) {
				return response.isSuccessful() ? null : "request failed with HTTP " + response.code();
			}
        } 
        catch (IOException e) {
            throw new RuntimeException(e);
        }
	}
	
	/**
	 * Combine wakeup and getDriveState to make sure the car really is wake, and retry until successful or the time expires.
	 * NOTE: If a drive state is returned, but it does not contain a shift state, and the time expires, P is assumed. 
	 * @return the shiftState (because that often determines follow up actions; don't meddle with the car when it is driving)
	 */
	public String wakeUp(String vehicleId, int retryDurationInMS, int sleepTimeInMS) {
    	long retryUntil = System.currentTimeMillis() + retryDurationInMS;
    	
    	// until we're satified
    	int attemptCnt = 0;
        while (true) {
            long now = System.currentTimeMillis();
        	
        	// attempt wakeup
            logger.debug("{}Waking up {}, attempt={}", logPrefix, vehicleId, ++attemptCnt);
            String wakeUp = wakeUp(vehicleId);
            
            // get drive state
            DriveState driveState = getDriveState(vehicleId);
            String shiftState = (driveState == null ? null : driveState.shiftState);
            logger.debug("{}Waking up {}, wakeUp={}, shiftState={}, driveState={}", logPrefix, vehicleId, wakeUp, shiftState, driveState);
            
            // If shift state is set, we're done
			if (!isEmpty(shiftState)) {  
	            logger.debug("{}Waking up {} succes, shiftState={}", logPrefix, vehicleId, shiftState);
                return shiftState;
            }
			
            // If we got a driveState, but the shift state is empty, and max time expired, assume 'P'
			if (driveState != null && isEmpty(shiftState) && now > retryUntil) {
                logger.debug("{}Waking up {}, shiftState is empty, maxTimeExpired => assuming P", logPrefix, vehicleId);
                return "P";
            }
            
            // if max time expired, exit
			if (now > retryUntil) {
		        logger.debug("{}Waking up {} failed", logPrefix, vehicleId);
		        return null;
            }

			// Sleep 5 seconds
            sleep(sleepTimeInMS);
        }
	}

	/**
	 * @return null if everything is ok, or an error string if not
	 */
	public String flashLights(String vehicleId) {
		return doCommand(vehicleId, "flash_lights", "");
	}
	
	/**
	 * @return null if everything is ok, or an error string if not
	 */
	public String startAutoConditioning(String vehicleId) {
		return doCommand(vehicleId, "auto_conditioning_start", "");
	}
	
	/**
	 * @return null if everything is ok, or an error string if not
	 */
	public String stopAutoConditioning(String vehicleId) {
		return doCommand(vehicleId, "auto_conditioning_stop", "");
	}
	
	/**
	 * The parameters are always in celsius, regardless of the region the car is in or the display settings of the car.
	 * @return null if everything is ok, or an error string if not
	 */
	public String setTemps(String vehicleId, double driverTemp, double passengerTemp) {
		return doCommand(vehicleId, "set_temps", String.format(Locale.US, "{\"driver_temp\" : \"%3.1f\", \"passenger_temp\" : \"%3.1f\"}",driverTemp, passengerTemp));
	}

	/**
	 * Set temperature, but use GUI setting to convert to value to celcius (as required by setTemps) if unit is not specified
	 * @return null if everything is ok, or an error string if not
	 */
	public String setTempsWithConversion(String vehicleId, double driverTemp, double passengerTemp, String unit) {
		
		// If the unit is not specified, get it from the GUI settings
		if (unit == null) {
			GUISettings guiSettings = getGUISettings(vehicleId);
			unit = guiSettings.guiTemperatureUnits;
		}
		if (unit != null) {
			unit = unit.toUpperCase();
		}
		
		// The parameters of the API call are always in celsius, so we need to convert F to C for example
		if ("F".equals(unit)) {
			driverTemp = convertF2C(driverTemp);
			passengerTemp = convertF2C(passengerTemp);
		}
		
		// do it
		return setTemps(vehicleId, driverTemp, passengerTemp);
	}
	private double convertF2C(double f) {
		double unrounded = (f - 32.0) * (5.0/9.0); // 0°C = (32°F − 32) × 5/9
		double rounded = BigDecimal.valueOf(unrounded).setScale(1, RoundingMode.HALF_UP).doubleValue();
		return rounded;
	}
	
	/**
	 * The parameters are always in celsius, regardless of the region the car is in or the display settings of the car.
	 * @return null if everything is ok, or an error string if not
	 */
	public String setPreconditioningMax(String vehicleId, boolean on) {
		return doCommand(vehicleId, "set_preconditioning_max", String.format(Locale.US, "{\"on\" : \"%s\"}","" + on));
	}

	/**
	 * @return null if everything is ok, or an error string if not
	 */
	public String startCharging(String vehicleId) {
		return doCommand(vehicleId, "charge_start", "", "charging", "complete");
	}
	
	/**
	 * @return null if everything is ok, or an error string if not
	 */
	public String stopCharging(String vehicleId) {
		return doCommand(vehicleId, "charge_stop", "");
	}
	
	/**
	 * @return null if everything is ok, or an error string if not
	 */
	public String setChargeLimit(String vehicleId, int percent) {
		if (percent < 1 || percent > 100) {
            throw new IllegalArgumentException("Percent must be between 0 and 100");
		}
		return doCommand(vehicleId, "set_charge_limit", String.format("{\"percent\" : \"%d\"}", percent));
	}

	/**
	 * @return null if everything is ok, or an error string if not
	 */
	public String lockDoors(String vehicleId) {
		return doCommand(vehicleId, "door_lock", "");
	}
	
	/**
	 * @return null if everything is ok, or an error string if not
	 */
	public String unlockDoors(String vehicleId) {
		return doCommand(vehicleId, "door_unlock", "");
	}
	
	/**
	 * @return null if everything is ok, or an error string if not
	 */
	public String setSentryMode(String vehicleId, boolean state) {
		return doCommand(vehicleId, "set_sentry_mode", String.format("{\"on\" : \"%s\"}", "" + state));
	}
	
	/**
	 * @return null if everything is ok, or an error string if not
	 */
	public String windowControl(String vehicleId, String command) {
		return doCommand(vehicleId, "window_control", String.format("{\"command\" : \"%s\"}", "" + command));
	}
	
	/**
	 * @return null if everything is ok, or an error string if not
	 */
	public String sunRoofControl(String vehicleId, String state) {
		return doCommand(vehicleId, "sun_roof_control", String.format("{\"state\" : \"%s\"}", "" + state));
	}
	
	/**
	 * @return null if everything is ok, or an error string if not
	 */
	public String heatSeat(String vehicleId, int heater, int level) {
		return doCommand(vehicleId, "remote_seat_heater_request", String.format("{\"heater\" : \"%s\", \"level\" : \"%s\"}", "" + heater, "" + level));
	}
	
	enum HeatSeat {
		DRIVER(0),
		PASSENGER(1),
		REARLEFT(2),
		REARCENTER(4),
		REARRIGHT(5);
		
		private final int nr;

		private HeatSeat(int nr) {
			this.nr = nr;
		}
	}
	
	enum HeatSeatLevel {
		OFF(0),
		LOW(1),
		MEDIUM(2),
		HIGH(3);
		
		private final int nr;

		private HeatSeatLevel(int nr) {
			this.nr = nr;
		}
	}

	/**
	 * @return null if everything is ok, or an error string if not
	 */
	public String heatSeat(String vehicleId, HeatSeat heatSeat, HeatSeatLevel heatSeatLevel) {
		return heatSeat(vehicleId, heatSeat.nr, heatSeatLevel.nr);
	}
	
	/**
	 * @return null if everything is ok, or an error string if not
	 */
	public String heatSteeringWheel(String vehicleId, boolean state) {
		return doCommand(vehicleId, "remote_steering_wheel_heater_request", String.format("{\"on\" : \"%s\"}", "" + state));
	}
	
	/*
	 * 
	 */
	private String doCommand(String vehicleId, String command, String bodyContent) {
		return doCommand(vehicleId, command, bodyContent, new String[] {});
	}
	
	/*
	 * 
	 */
	private String doCommand(String vehicleId, String command, String bodyContent, String... okReasons) {
		doTokensCheck();
		
        try {
            // Call the REST service
			Request request = new Request.Builder()
	                .url(URL_BASE + URL_VERSION + URL_VEHICLES + vehicleId + "/command/" + command)
	                .header(HEADER_AUTHORIZATION, authorizationHeader)
					.post(RequestBody.create(bodyContent, JsonMediaType))
	                .build();
			try (
				Response response = okHttpClient.newCall(request).execute();
				ResponseBody responseBody = response.body();
			) {
				int code = response.code();
				if (!response.isSuccessful()) {
					logger.warn("{}response is not succesful: {}", logPrefix, response);
					return "request failed with HTTP " + code;
				}
	
				// Parse the result and build a list of vehicles
				// {"response":{"result": true,,"reason": ...
				String responseBodyContent = responseBody.string();
				JsonObject responseJsonObject = gson.fromJson(responseBodyContent, JsonObject.class);
				responseJsonObject = responseJsonObject.get("response").getAsJsonObject();
				boolean result = responseJsonObject.get("result").getAsBoolean();
				String reason = responseJsonObject.get("reason").getAsString();
				if (reason.equals("already_set") || Arrays.asList(okReasons).contains(reason)) {
					reason = "";
				}
				if (!result) logger.warn(reason + ": " + request + " " + bodyContent + " " + response + " " + responseBodyContent);
				if (isEmpty(reason)) {
					reason = null; 
				}
				return reason;
			}
        } 
        catch (IOException e) {
            throw new RuntimeException(e);
        }
	}
	
	/**
	 * @return null if request had an error 
	 * 
	 */
	public ChargeState getChargeState(String vehicleId) {
		return getState(vehicleId, "charge_state", ChargeState.class);
	}
	
	/**
	 * @return null if request had an error 
	 * 
	 */
	public ClimateState getClimateState(String vehicleId) {
		return getState(vehicleId, "climate_state", ClimateState.class);
	}
	
	/**
	 * @return null if request had an error 
	 * 
	 */
	public DriveState getDriveState(String vehicleId) {
		return getState(vehicleId, "drive_state", DriveState.class);
	}
	
	/**
	 * @return null if request had an error 
	 * 
	 */
	public GUISettings getGUISettings(String vehicleId) {
		return getState(vehicleId, "gui_settings", GUISettings.class);
	}
	
	/**
	 * @return null if request had an error 
	 * 
	 */
	private <T> T getState(String vehicleId, String urlSuffix, Class<T> clazz) {
		doTokensCheck();
		
        try {
            // Call the REST service
			Request request = new Request.Builder()
	                .url(URL_BASE + URL_VERSION + URL_VEHICLES + vehicleId + "/data_request/" + urlSuffix)
	                .header(HEADER_AUTHORIZATION, authorizationHeader)
					.get()
	                .build();
			try (
				Response response = okHttpClient.newCall(request).execute();
				ResponseBody responseBody = response.body();
			) {
				if (!response.isSuccessful()) {
					logger.warn("{}response is not succesful: {}", logPrefix, response);
					return null;
				}
	
				// Parse the result and build a list of vehicles
				// {"response":{"gps_as_of":1565873014,"heading":71,"latitude":51.823154,"longitude":5.786151,"native_latitu ...
				String responseBodyContent = responseBody.string();
				JsonObject responseJsonObject = gson.fromJson(responseBodyContent, JsonObject.class);
				responseJsonObject = responseJsonObject.get("response").getAsJsonObject();
				return clazz.getConstructor(JsonObject.class).newInstance(responseJsonObject);
			}
        } 
        catch (Exception e) {
            throw new RuntimeException(e);
        }
	}

	/*
	 * 
	 */
	private void doTokensCheck() {
		if (tokens == null) {
			throw new RuntimeException("No login or setTokens was done.");
		}
	}
	
	/*
	 * 
	 * @param s
	 * @return
	 */
	private boolean isEmpty(String s) {
		return s == null || s.trim().isEmpty();
	}

	/*
	 * 
	 */
	static private void sleep(int ms) {
		try {
			Thread.sleep(ms);
		} 
		catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
