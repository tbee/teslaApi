package org.tbee.tesla.dto;

import com.google.gson.JsonObject;

public class ClimateState {
	private static final String IS_REAR_DEFROSTER_ON = "is_rear_defroster_on";
	final public Boolean isRearDefrosterOn;
	final public JsonObject jsonObject;
	
	public ClimateState(JsonObject jsonObject) {
		this.jsonObject = jsonObject;
		this.isRearDefrosterOn = (jsonObject.get(IS_REAR_DEFROSTER_ON).isJsonNull() ? null : jsonObject.get(IS_REAR_DEFROSTER_ON).getAsBoolean());
	}
	
	public ClimateState(Boolean isRearDefrosterOn) {
		this.jsonObject = new JsonObject();
		this.isRearDefrosterOn = isRearDefrosterOn;
	}
	
	@Override
	public String toString() {
		return super.toString()
		     + ", isRearDefrosterOn=" + isRearDefrosterOn
		     + ", json=" + jsonObject;
	}
}
