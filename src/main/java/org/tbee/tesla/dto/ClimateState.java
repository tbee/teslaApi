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
