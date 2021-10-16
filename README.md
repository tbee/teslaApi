# teslaApi

This is a Java implementation of the Tesla API.

IMPORTANT: In summer 2021 Tesla has added a recaptcha to the login process. This is an image like "select all squares that show traffic lights", which is impossible to automate. Hence the implementation's login process doesn't work anymore. The only viable approach seems to be to run the login in an embedded browser, but because Java's webview control uses native calls to webkit, it has not been possible to automatically examine the two consecutive redirects which contain the all important authorization code. It is doubtful if a server / web based implementation will be possible, the only option seems to be to use native mobile apps that do allow to inspecting the redirects.

**Work in progress**

Example usage:

```Java
		TeslaAPI teslaAPI = new TeslaAPI();
		// inoperable: teslaAPI.login(TESLA_USERNAME, TESLA_PASSWORD, TESLA_MFA_PASSCODE); // or use setTokens
		List<Vehicle> vehicles = teslaAPI.getVehicles();
		String vehicleId = vehicles.get(0).id;
		teslaAPI.flashLights(vehicleId)
```
