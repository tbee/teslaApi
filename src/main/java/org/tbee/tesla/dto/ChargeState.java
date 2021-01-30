package org.tbee.tesla.dto;

import com.google.gson.JsonObject;

public class ChargeState {
	private static final String BATTERY_LEVEL = "battery_level";
	final public Integer batteryLevel;
	final public JsonObject jsonObject;
	
	public ChargeState(JsonObject jsonObject) {
		this.jsonObject = jsonObject;
		this.batteryLevel = (jsonObject.get(BATTERY_LEVEL).isJsonNull() ? null : jsonObject.get(BATTERY_LEVEL).getAsInt());
	}
	
	public ChargeState(int batteryLevel) {
		this.jsonObject = new JsonObject();
		this.batteryLevel = batteryLevel;
	}
	
	@Override
	public String toString() {
		return super.toString()
		     + ", batteryLevel=" + batteryLevel
		     + ", json=" + jsonObject;
	}
}
