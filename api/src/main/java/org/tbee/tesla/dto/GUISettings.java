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
