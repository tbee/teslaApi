# teslaApi

This is a Java implementation of the Tesla API.

Example usage:

```Java
		TeslaAPI teslaAPI = new TeslaAPI();
		teslaAPI.login(TESLA_USERNAME, TESLA_PASSWORD, TESLA_MFA_PASSCODE); // or use setTokens
		List<Vehicle> vehicles = teslaAPI.getVehicles();
		String vehicleId = vehicles.get(0).id;
		teslaAPI.flashLights(vehicleId)
```