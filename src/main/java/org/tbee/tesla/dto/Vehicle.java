package org.tbee.tesla.dto;

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
