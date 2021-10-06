package org.tbee.tesla.dto;

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

import com.google.gson.JsonObject;

public class Vehicle {
	final public String id;
	final public String vin;
	final public String displayName;
	final public String state; // can be "asleep", "waking", or "online"
	final public JsonObject jsonObject;
	
	/**
	 * Mainly for testing
	 */
	public Vehicle(String id, String vin, String displayName, String state) {
		this.jsonObject = new JsonObject();
		this.id = id;
		this.vin = vin;
		this.state = state;
		this.displayName = displayName;
	}
	
	/**
	 * 
	 */
	public Vehicle(JsonObject jsonObject) {
		this.jsonObject = jsonObject;
		this.id = jsonObject.get("id").getAsString();
		this.vin = jsonObject.get("vin").getAsString();
		this.state = jsonObject.get("state").getAsString();
		this.displayName = jsonObject.get("display_name").getAsString();
	}

	public boolean isAsleep() {
		return "asleep".equalsIgnoreCase(state);
	}
	
	public boolean isWaking() {
		return "waking".equalsIgnoreCase(state);
	}
	
	public boolean isOnline() {
		return "online".equalsIgnoreCase(state);
	}
	
	@Override
	public String toString() {
		return super.toString()
			 + ", id=" + id
		     + ", vin=" + vin
		     + ", displayName=" + displayName
		     + ", json=" + jsonObject;
	}
}
