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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tbee.tesla.dto.ChargeState;
import org.tbee.tesla.dto.ClimateState;
import org.tbee.tesla.dto.DriveState;
import org.tbee.tesla.dto.GUISettings;
import org.tbee.tesla.dto.Tokens;
import org.tbee.tesla.dto.Vehicle;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import okhttp3.JavaNetCookieJar;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import okhttp3.logging.HttpLoggingInterceptor.Level;

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
	static final Logger logger = LoggerFactory.getLogger(TeslaAPI.class);
	
    // API contants
    static final String URL_BASE = "https://owner-api.teslamotors.com/";
    static final String URL_VERSION = "api/1/";
    static final String URL_VEHICLES = "vehicles/";
	static final String HEADER_AUTHORIZATION = "Authorization";

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
		
		// Setup the logger
		HttpLoggingInterceptor logging = new HttpLoggingInterceptor(new okhttp3.logging.HttpLoggingInterceptor.Logger() { // if we do not use a lambda here, the method log output will read "log(", which will make grepping easier if required
			@Override
			public void log(String s) {
				logger.trace("{}{}", logPrefix, s);
			}
		});
		logging.setLevel(Level.BODY);
		
		// Initialize the HTTP client
		okHttpClient = new OkHttpClient.Builder()
			.protocols(Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1))
	        .connectTimeout(30, TimeUnit.SECONDS)
	        .writeTimeout(30, TimeUnit.SECONDS)
	        .readTimeout(2, TimeUnit.MINUTES)
	        .cookieJar(new JavaNetCookieJar(cookieManager))
	        .addNetworkInterceptor(logging)
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
	 * This semi-login only fetches and remembers the access and refresh tokens needed for further actions.
	 * You may also set these manually using setTokens
	 * In order to obtain the authorizationCode the user needs to complete the whole login process and extra the authorizationCode from the double redirects in the web browser flow.
	 * @param authorizationCode
	 * @return 
	 */
	public Tokens login(String authorizationCode) {
        Tokens tokens = new TeslaAuthLogin(okHttpClient, logPrefix).login(authorizationCode);
		setTokens(tokens);
		return tokens;
	}
    
	/**
	* This login only fetches and remembers the access and refresh tokens needed for further actions.
	* You may also set these manually using setTokens
	* @param username
	* @param password
	* @param passcode
	* @return 
	*/
	public Tokens login(String username, String password, String passcode) {
		Tokens tokens = new TeslaMFALogin(okHttpClient, logPrefix).login(username, password, passcode);
		setTokens(tokens);
		return tokens;
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
        Tokens tokens = new TeslaNoMFALogin(okHttpClient, logPrefix).login(username, password);
		setTokens(tokens);
		return tokens;
	}
	
	/**
	 * This method fetches (and remembers/replaces) new access and refresh tokens
	 * @return 
	 */
	public Tokens refreshTokens() {
		Tokens tokens = new TeslaLoginHelper(okHttpClient, logPrefix).refreshTokens(this.tokens);
		setTokens(tokens);
		return tokens;
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
