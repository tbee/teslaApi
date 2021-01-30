package org.tbee.tesla.dto;

import com.google.gson.JsonObject;

public class GUISettings {
	private static final String GUI_TEMPERATURE_UNITS = "gui_temperature_units";
	final public String guiTemperatureUnits;
	final public JsonObject jsonObject;
	
	public GUISettings(JsonObject jsonObject) {
		this.jsonObject = jsonObject;
		this.guiTemperatureUnits = (jsonObject.get(GUI_TEMPERATURE_UNITS).isJsonNull() ? "" : jsonObject.get(GUI_TEMPERATURE_UNITS).getAsString());
	}
	
	public GUISettings(String guiTemperatureUnits) {
		this.jsonObject = new JsonObject();
		this.guiTemperatureUnits = guiTemperatureUnits;
	}
	
	@Override
	public String toString() {
		return super.toString()
		     + ", shiftState=" + guiTemperatureUnits
		     + ", json=" + jsonObject;
	}
}
