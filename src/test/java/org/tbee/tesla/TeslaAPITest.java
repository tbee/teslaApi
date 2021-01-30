package org.tbee.tesla;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.tbee.tesla.TeslaAPI;
import org.tbee.tesla.dto.DriveState;
import org.tbee.tesla.dto.GUISettings;
import org.tbee.tesla.dto.Tokens;

public class TeslaAPITest {

	private static final String VEHICLE_ID = "TEST";
	
	final TeslaAPI teslaApi = spy(TeslaAPI.class);

	/**
	 * 
	 */
	@Before
	public void before() {
		// GIVEN
		teslaApi.setTokens(new Tokens("A", "R"));
		doReturn(new GUISettings("C")).when(teslaApi).getGUISettings(anyString());
		doReturn(new DriveState("P")).when(teslaApi).getDriveState(anyString());
		doReturn(null).when(teslaApi).setTemps(anyString(), anyDouble(), anyDouble());
		doReturn(null).when(teslaApi).setPreconditioningMax(anyString(), anyBoolean());
		doReturn((String)null).when(teslaApi).wakeUp(anyString());
	}

	/**
	 * 
	 */
	@Test
	public void setTempsWithConversionCTest() {
		// GIVEN
		doReturn(new GUISettings("C")).when(teslaApi).getGUISettings(anyString());
		
		// WHEN
		teslaApi.setTempsWithConversion(VEHICLE_ID, 21.0, 21.0, null);
		
		// THEN
		verify(teslaApi, times(1)).setTemps(VEHICLE_ID, 21.0, 21.0);
	}

	/**
	 * 
	 */
	@Test
	public void setTempsWithConversionFTest() {
		// GIVEN
		doReturn(new GUISettings("F")).when(teslaApi).getGUISettings(anyString());
		
		// WHEN
		teslaApi.setTempsWithConversion(VEHICLE_ID, 72.0, 72.0, null);
		
		// THEN
		verify(teslaApi, times(1)).setTemps(VEHICLE_ID, 22.2, 22.2);
	}

	/**
	 * 
	 */
	@Test
	public void wakeUpTest() {
		// GIVEN
		doReturn(new DriveState("P")).when(teslaApi).getDriveState(anyString());
		
		// WHEN
		String shiftState = teslaApi.wakeUp(VEHICLE_ID, 1000, 100);
		
		// THEN
		Assert.assertEquals("P", shiftState);
		verify(teslaApi, times(1)).wakeUp(VEHICLE_ID);
	}

	/**
	 * 
	 */
	@Test
	public void wakeUpNoShiftStateTest() {
		// GIVEN
		doReturn(new DriveState("")).when(teslaApi).getDriveState(anyString());
		
		// WHEN
		String shiftState = teslaApi.wakeUp(VEHICLE_ID, 1000, 100);
		
		// THEN
		Assert.assertEquals("P", shiftState);
		verify(teslaApi, atLeast(10)).wakeUp(VEHICLE_ID);
	}

	/**
	 * 
	 */
	@Test
	public void wakeUpDrivingShiftStateTest() {
		// GIVEN
		doReturn(new DriveState("D")).when(teslaApi).getDriveState(anyString());
		
		// WHEN
		String shiftState = teslaApi.wakeUp(VEHICLE_ID, 1000, 100);
		
		// THEN
		Assert.assertEquals("D", shiftState);
		verify(teslaApi, times(1)).wakeUp(VEHICLE_ID);
	}

	/**
	 * 
	 */
	@Test
	public void wakeUpNullShiftStateTest() {
		// GIVEN
		doReturn(null).when(teslaApi).getDriveState(anyString());
		
		// WHEN
		String shiftState = teslaApi.wakeUp(VEHICLE_ID, 1000, 100);
		
		// THEN
		Assert.assertNull(shiftState);
		verify(teslaApi, atLeast(10)).wakeUp(VEHICLE_ID);
	}
}
