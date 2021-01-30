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
