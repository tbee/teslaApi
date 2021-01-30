package org.tbee.tesla.dto;

import com.google.gson.JsonObject;

public class DriveState {
	private static final String SHIFT_STATE = "shift_state";
	final public String shiftState;
	final public JsonObject jsonObject;
	
	public DriveState(JsonObject jsonObject) {
		this.jsonObject = jsonObject;
		this.shiftState = (jsonObject.get(SHIFT_STATE).isJsonNull() ? "" : jsonObject.get(SHIFT_STATE).getAsString());
	}
	
	public DriveState(String shiftState) {
		this.jsonObject = new JsonObject();
		this.shiftState = shiftState;
	}
	
	@Override
	public String toString() {
		return super.toString()
		     + ", shiftState=" + shiftState
		     + ", json=" + jsonObject;
	}
}
