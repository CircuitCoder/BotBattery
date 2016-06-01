package io.c3.lab;
/**
 * Line tracker along with voltage sensor
 * @author Liu Xiaoyi <me@c-3.io>
 */

import lejos.hardware.Battery;
import lejos.hardware.lcd.LCD;
import lejos.hardware.motor.EV3LargeRegulatedMotor;
import lejos.hardware.port.MotorPort;
import lejos.hardware.port.SensorPort;
import lejos.hardware.sensor.EV3ColorSensor;
import lejos.hardware.sensor.EV3TouchSensor;
import lejos.hardware.sensor.SensorMode;
import lejos.robotics.RegulatedMotor;

public class Main {
	static boolean running = false;
	
	// Reference light value
	static final double STANDARD = 0.3;
	
	// Base motor speed
	static final double BASE = 200;
	
	// For P control
	static final double RATIO = 800;
	
	// For D control
	static final double DFACTOR = 1;
	
	// For I control
	static final double IFACTOR = 0.3;
	
	public static void main(String args[]) throws InterruptedException {
		
		// Initial touch sensor
		EV3TouchSensor touchModes = new EV3TouchSensor(SensorPort.S2);
		SensorMode touchProvider = touchModes.getTouchMode();
		LCD.setAutoRefresh(false);
		
		float touchSample[] = new float[touchProvider.sampleSize()];
		touchProvider.fetchSample(touchSample, 0);
		
		// Wait for a press to start
		while(touchSample[0] < 0.5) {
			touchProvider.fetchSample(touchSample, 0);
		}
		
		while(touchSample[0] > 0.5) {
			touchProvider.fetchSample(touchSample, 0);
		}
		
		LCD.drawString("Starting...", 0, 6);
		
		running = true;
				
		Thread voltThread = new Thread(new Runnable() {
			public int iter = 0;
			@Override
			public void run() {
				LCD.setAutoRefresh(false);
				while(running) {
					int volt = Battery.getVoltageMilliVolt();
					LCD.clear();
					LCD.drawString(String.format("Measure: %d", ++iter), 0, 0);
					LCD.drawString(String.format("Volt: %dmV",volt), 0, 1);
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		});
		
		Thread controlThread = new Thread(new Runnable() {
			@Override
			public void run() {
				EV3ColorSensor colorSensor = new EV3ColorSensor(SensorPort.S1);
				SensorMode lightProvider = colorSensor.getRedMode();
				float sample[] = new float[lightProvider.sampleSize()];
				
				RegulatedMotor left = new EV3LargeRegulatedMotor(MotorPort.B);
				RegulatedMotor right = new EV3LargeRegulatedMotor(MotorPort.C);
				
				left.forward();
				right.forward();
				
				double lastDelta = STANDARD;
				
				while(running) {
					lightProvider.fetchSample(sample, 0);
					double delta = sample[0] - STANDARD;
					delta += (delta-lastDelta)*DFACTOR;
					
					// Follow on the right side
					double leftPower = - RATIO * delta + BASE;
					double rightPower = + RATIO * delta + BASE;
					
					LCD.drawString(String.format("Delta: %f", delta), 0, 3);
					LCD.drawString(String.format("LPower: %f", leftPower), 0, 4);
					LCD.drawString(String.format("RPower: %f", rightPower), 0, 5);
					LCD.drawString(String.format("Stored: %f", lastDelta), 0, 6);
					LCD.drawString(String.format("D-Delta: %f", delta - lastDelta), 0, 6);
					LCD.asyncRefresh();
					
					left.setSpeed((int) leftPower);
					right.setSpeed((int) rightPower);
					
					// Update speed
					left.forward();
					right.forward();
					
					lastDelta = lastDelta * IFACTOR + delta * (1-IFACTOR);
				}
				
				left.flt(true);
				right.flt(true);
				
				left.close();
				right.close();
				colorSensor.close();
			}
		});
		
		voltThread.start();
		controlThread.start();
		
		// Prevent that one press triggers two switches
		Thread.sleep(1000);
		
		while(touchSample[0] < 0.5) {
			touchProvider.fetchSample(touchSample, 0);
		}
		
		while(touchSample[0] > 0.5) {
			touchProvider.fetchSample(touchSample, 0);
		}
		
		running = false;
		touchModes.close();
	}
}
