/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
 */
package fr.louisbl.cordova.gpslocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.cordova.CallbackContext;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

public class CordovaLocationListener implements LocationListener {
	public static int PERMISSION_DENIED = 1;
	public static int POSITION_UNAVAILABLE = 2;
	public static int TIMEOUT = 3;

	public HashMap<String, CallbackContext> watches = new HashMap<String, CallbackContext>();

	protected boolean mIsRunning = false;

	private CordovaGPSLocation mOwner;
	private List<CallbackContext> mCallbacks = new ArrayList<CallbackContext>();
	private Timer mTimer = null;
	private String TAG;
	private Boolean ignoringNetworkLocations = false;

	public CordovaLocationListener(CordovaGPSLocation owner, String tag) {
		mOwner = owner;
		TAG = tag;
	}

	@Override
	public void onLocationChanged(Location location) {
		Log.d(TAG, "The location has been updated!");
		if ((location.getProvider().equals(LocationManager.GPS_PROVIDER) ) || !ignoringNetworkLocations) {
			if (location.getProvider().equals(LocationManager.GPS_PROVIDER)  ) {
				Log.d(TAG, "Got a GPS location");
				// Once we got a GPS location we dont care anymore of Network locations
				ignoringNetworkLocations = true;
			} else {
				Log.d(TAG, "Got a NETWORK location");
			}
			win(location);
		}
	}

	@Override
	public void onProviderDisabled(String provider) {
		if (!mOwner.getLocationManager().isProviderEnabled(LocationManager.GPS_PROVIDER) && !mOwner.getLocationManager().isProviderEnabled(LocationManager.NETWORK_PROVIDER)){
			fail(POSITION_UNAVAILABLE, "All locations providers are disabled.");
		}
	}

	public int size() {
		return watches.size() + mCallbacks.size();
	}

	public void addWatch(String timerId, CallbackContext callbackContext) {
		watches.put(timerId, callbackContext);

		if (size() == 1) {
			start();
		}
	}

	public void addCallback(CallbackContext callbackContext, int timeout) {
		if (mTimer == null) {
			mTimer = new Timer();
		}

		mTimer.schedule(new LocationTimeoutTask(callbackContext, this), timeout);
		mCallbacks.add(callbackContext);

		if (size() == 1) {
			start();
		}
	}

	public void listenToNetworkLocations(CallbackContext callbackContext) {
		ignoringNetworkLocations = false;
		callbackContext.success("Listening for network locations until a GPS location is received");
	}

	public void clearWatch(String timerId) {
		if (watches.containsKey(timerId)) {
			watches.remove(timerId);
		}
		if (size() == 0) {
			stop();
		}
	}

	public void destroy() {
		stop();
	}

	protected void fail(int code, String message) {
		cancelTimer();
		ignoringNetworkLocations = false;

		for (CallbackContext callbackContext : mCallbacks) {
			mOwner.fail(code, message, callbackContext, false);
		}

		if (watches.size() == 0) {
			stop();
		}

		mCallbacks.clear();

		for (CallbackContext callbackContext : watches.values()) {
			mOwner.fail(code, message, callbackContext, true);
		}
	}

	protected void win(Location loc) {
		cancelTimer();

		for (CallbackContext callbackContext : mCallbacks) {
			mOwner.win(loc, callbackContext, false);
		}

		if (watches.size() == 0) {
			stop();
		}

		mCallbacks.clear();

		for (CallbackContext callbackContext : watches.values()) {
			mOwner.win(loc, callbackContext, true);
		}
	}

	private void start() {
		mOwner.getLocationManager().requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0, this);
		mOwner.getLocationManager().requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
	}

	private void stop() {
		cancelTimer();
		mOwner.getLocationManager().removeUpdates(this);
	}

	private void cancelTimer() {
		if (mTimer != null) {
			mTimer.cancel();
			mTimer.purge();
			mTimer = null;
		}
	}

	private class LocationTimeoutTask extends TimerTask {

		private CallbackContext mCallbackContext = null;
		private CordovaLocationListener mListener = null;

		public LocationTimeoutTask(CallbackContext callbackContext,
				CordovaLocationListener listener) {
			mCallbackContext = callbackContext;
			mListener = listener;
		}

		@Override
		public void run() {
			for (CallbackContext callbackContext : mListener.mCallbacks) {
				if (mCallbackContext == callbackContext) {
					mListener.mCallbacks.remove(callbackContext);
					break;
				}
			}

			if (mListener.size() == 0) {
				mListener.stop();
			}
		}
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		Log.d(TAG, "Provider " + provider + " status changed to " + status);
	}

	@Override
	public void onProviderEnabled(String provider) {
		Log.d(TAG, "Provider " + provider + " has been enabled.");
	}
}
