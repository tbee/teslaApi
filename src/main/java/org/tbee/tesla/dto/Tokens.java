package org.tbee.tesla.dto;

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
