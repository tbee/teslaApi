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

import java.time.Instant;

public class Tokens {
	final public String accessToken;
	final public String refreshToken;
	final public Instant createdAt;
	final public long expiresIn;
	final public Instant expiresAt;
	
	public Tokens(String accessToken, String refreshToken) {
		this.accessToken = accessToken;
		this.refreshToken = refreshToken;
		this.createdAt = Instant.now();
		this.expiresIn = -1;
		this.expiresAt = null;
	}
	
	public Tokens(String accessToken, String refreshToken, Instant createdAt, long expiresIn) {
		this.accessToken = accessToken;
		this.refreshToken = refreshToken;
		this.createdAt = createdAt;
		this.expiresIn = expiresIn;
		this.expiresAt = createdAt.plusSeconds(expiresIn);
	}
	
	@Override
	public String toString() {
		return super.toString()
		     + ",expiresAt="  + expiresAt
		     ;
	}
}
