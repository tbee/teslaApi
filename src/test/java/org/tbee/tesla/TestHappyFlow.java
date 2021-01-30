package org.tbee.tesla;

import java.util.List;
import java.util.Scanner;

import org.junit.Assert;
import org.tbee.tesla.TeslaAPI;
import org.tbee.tesla.dto.ChargeState;
import org.tbee.tesla.dto.ClimateState;
import org.tbee.tesla.dto.DriveState;
import org.tbee.tesla.dto.GUISettings;
import org.tbee.tesla.dto.Tokens;
import org.tbee.tesla.dto.Vehicle;

public class TestHappyFlow {
	
	public static void main(String[] args) {
		final String TESLA_USERNAME = args[0];
		final String TESLA_PASSWORD = args[1];
		final String TESLA_VIN = (args.length < 3 ? null : args[2]);
		
		// Start the API
		final TeslaAPI teslaAPI = new TeslaAPI();
		teslaAPI.setLogPrefix(TESLA_USERNAME + ": ");
		
		// login
		System.out.println("login");
		Scanner scanner = new Scanner(System.in);
		System.out.println("passcode ('-' to skip MFA):");
		String passcode = scanner.next();
		Tokens tokens = ("-".equals(passcode.trim()) 
				? teslaAPI.login(TESLA_USERNAME, TESLA_PASSWORD)             // without MFA
				: teslaAPI.login(TESLA_USERNAME, TESLA_PASSWORD, passcode)); // with MFA
		Assert.assertNotNull(tokens);
		Assert.assertNotNull(tokens.accessToken);
		Assert.assertNotNull(tokens.refreshToken);
		System.out.println("Access token: " + tokens.accessToken);
		
		// get vehicles
		System.out.println("getVehicles");
		List<Vehicle> vehicles = teslaAPI.getVehicles();
		Assert.assertNotEquals(0, vehicles.size());
		if (TESLA_VIN != null) {
			Assert.assertEquals(TESLA_VIN, vehicles.get(0).vin);
		}
		Vehicle vehicle = vehicles.get(0);
		String vehicleId = vehicle.id;
		teslaAPI.setLogPrefix(TESLA_USERNAME + " - " + vehicle.vin + " - " + vehicle.displayName + ": ");
		
		// Do the same via setting the tokens to see if that works correctly
		{
			// login alternative
			final TeslaAPI teslaAPI2 = new TeslaAPI();
			teslaAPI2.setTokens(tokens);
			
			// get vehicles
			System.out.println("getVehicles2");
			List<Vehicle> vehicles2 = teslaAPI2.getVehicles();
			Assert.assertNotEquals(0, vehicles2.size());
			if (TESLA_VIN != null) {
				Assert.assertEquals(TESLA_VIN, vehicles2.get(0).vin);
			}
		}
		
		// Wake the vehicle up
		System.out.println("wakeUp");
		// Assert.assertTrue(teslaAPI.wakeUp(vehicleId));
		Assert.assertEquals("P", teslaAPI.wakeUp(vehicleId, 2 * 60 * 1000, 5 * 1000)); // two minutes and 5 seconds
		
		// getDriveState
		System.out.println("getDriveState");
		DriveState driveState = teslaAPI.getDriveState(vehicleId);
		Assert.assertEquals("P", driveState.shiftState);
		// getGUISettings
		System.out.println("getGUISettings");
		GUISettings guiSettings = teslaAPI.getGUISettings(vehicleId);
		Assert.assertEquals("C", guiSettings.guiTemperatureUnits);
		// getChargeState
		System.out.println("getChargeState");
		ChargeState chargeState = teslaAPI.getChargeState(vehicleId);
		Assert.assertTrue(chargeState.batteryLevel > 0);
		// getChargeState
		System.out.println("getClimateState");
		ClimateState climateState = teslaAPI.getClimateState(vehicleId);
		Assert.assertNotNull(climateState.isRearDefrosterOn);
		
		// test commands
		// flash
		// System.out.println("flashLights");
		//Assert.assertNull(teslaAPI.flashLights(vehicleId));
		// setChargeLimit
		System.out.println("charge");
		Assert.assertNull(teslaAPI.setChargeLimit(vehicleId, 90));
		Assert.assertNull(teslaAPI.startCharging(vehicleId));
		// setChargeLimit
		System.out.println("setTemps");
		Assert.assertNull(teslaAPI.setTempsWithConversion(vehicleId, 21.0, 21.0, null));
		// lockDoors
		System.out.println("lockDoors");
		Assert.assertNull(teslaAPI.lockDoors(vehicleId));
		// setSentryMode
		System.out.println("setSentryMode");
		Assert.assertNull(teslaAPI.setSentryMode(vehicleId, true));
		// windowControl still in beta
		System.out.println("windowControl");
		Assert.assertNotNull(teslaAPI.windowControl(vehicleId, "close"));
		// sunRoofControl
		System.out.println("sunRoofControl");
		Assert.assertNull(teslaAPI.sunRoofControl(vehicleId, "close"));
		
//		// Start auto conditioning
//		System.out.println("startAutoConditioning");
//		Assert.assertNull(teslaAPI.startAutoConditioning(vehicleId));
//		// heatSeat
//		System.out.println("heatSeat");
//		Assert.assertNull(teslaAPI.heatSeat(vehicleId, TeslaAPI.HeatSeat.DRIVER, TeslaAPI.HeatSeatLevel.LOW));
//		System.out.println("sleep");
//		sleep(3000);
//		// heatSeat
//		System.out.println("heatSeat");
//		Assert.assertNull(teslaAPI.heatSeat(vehicleId, TeslaAPI.HeatSeat.DRIVER, TeslaAPI.HeatSeatLevel.OFF));
//		// Start and stop auto conditioning
//		System.out.println("stopAutoConditioning");
//		Assert.assertNull(teslaAPI.stopAutoConditioning(vehicleId));
		
		// Start and stop defrost (stopping will leave the preconditioning active)
//		System.out.println("setPreconditioningMax true");
//		Assert.assertNull(teslaAPI.setPreconditioningMax(vehicleId, true));
//		System.out.println("sleep");
//		sleep(3000);
//		System.out.println("setPreconditioningMax false");
//		Assert.assertNull(teslaAPI.setPreconditioningMax(vehicleId, false));
		
		// do a refresh tokens
		System.out.println("refreshTokens");
		teslaAPI.refreshTokens();
		Assert.assertNotEquals(0, teslaAPI.getVehicles().size());

		// we got here, yay
		System.out.println("Test is succesful");
	}
	
	static private void sleep(int ms) {
		try {
			Thread.sleep(ms);
		} 
		catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
