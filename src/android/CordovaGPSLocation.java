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

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;

import android.Manifest;
import org.apache.cordova.PermissionHelper;
import android.content.pm.PackageManager;
import org.apache.cordova.LOG;

/*
 * This class is the interface to the Geolocation.  It's bound to the geo object.
 */

public class CordovaGPSLocation extends CordovaPlugin {

	private CordovaLocationListener mListener;
	private LocationManager mLocationManager;

    String TAG = "CordovaGPSLocation";
    String [] permissions = { Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION };
    CallbackContext context;

	LocationManager getLocationManager() {
		return mLocationManager;
	}

	@Override
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		super.initialize(cordova, webView);
		mLocationManager = (LocationManager) cordova.getActivity().getSystemService(Context.LOCATION_SERVICE);
	}

	/**
	 * Executes the request and returns PluginResult.
	 *
	 * @param action
	 *            The action to execute.
	 * @param args
	 *            JSONArry of arguments for the plugin.
	 * @param callbackContext
	 *            The callback id used when calling back into JavaScript.
	 * @return True if the action was valid, or false if not.
	 * @throws JSONException
	 */
	public boolean execute(final String action, final JSONArray args,
			final CallbackContext callbackContext) {
        context = callbackContext;

		if (action == null || !action.matches("getPermission|getLocation|addWatch|clearWatch")) {
			return false;
		}

        if(!hasPermisssion()) {
            PermissionHelper.requestPermissions(this, 0, permissions);

            return true;
        }

		final String id = args.optString(0, "");

		if (action.equals("clearWatch")) {
			clearWatch(id);
			return true;
		}

		if (isGPSdisabled() && isNetworkDisabled() ) {
			fail(CordovaLocationListener.POSITION_UNAVAILABLE, "GPS and Network are disabled on this device.", callbackContext, false);
			return true;
		}

		if (action.equals("getLocation")) {
			getLastLocation(args, callbackContext);
		} else if (action.equals("addWatch")) {
			addWatch(id, callbackContext);
		}

		return true;
	}

	/**
	 * Called when the activity is to be shut down. Stop listener.
	 */
	public void onDestroy() {
		if (mListener != null) {
			mListener.destroy();
		}
	}

	/**
	 * Called when the view navigates. Stop the listeners.
	 */
	public void onReset() {
		this.onDestroy();
	}

	public JSONObject returnLocationJSON(Location loc) {
		JSONObject o = new JSONObject();

		try {
			o.put("latitude", loc.getLatitude());
			o.put("longitude", loc.getLongitude());
			o.put("altitude", (loc.hasAltitude() ? loc.getAltitude() : null));
			o.put("accuracy", loc.getAccuracy());
			o.put("heading",
					(loc.hasBearing() ? (loc.hasSpeed() ? loc.getBearing()
							: null) : null));
			o.put("velocity", loc.getSpeed());
			o.put("timestamp", loc.getTime());
			o.put("provider", loc.getProvider());
		} catch (JSONException e) {
			e.printStackTrace();
		}

		return o;
	}

	public void win(Location loc, CallbackContext callbackContext,
			boolean keepCallback) {
		PluginResult result = new PluginResult(PluginResult.Status.OK,
				this.returnLocationJSON(loc));
		result.setKeepCallback(keepCallback);
		callbackContext.sendPluginResult(result);
	}

	/**
	 * Location failed. Send error back to JavaScript.
	 *
	 * @param code
	 *            The error code
	 * @param msg
	 *            The error message
	 * @throws JSONException
	 */
	public void fail(int code, String msg, CallbackContext callbackContext,
			boolean keepCallback) {
		JSONObject obj = new JSONObject();
		String backup = null;
		try {
			obj.put("code", code);
			obj.put("message", msg);
		} catch (JSONException e) {
			obj = null;
			backup = "{'code':" + code + ",'message':'"
					+ msg.replaceAll("'", "\'") + "'}";
		}
		PluginResult result;
		if (obj != null) {
			result = new PluginResult(PluginResult.Status.ERROR, obj);
		} else {
			result = new PluginResult(PluginResult.Status.ERROR, backup);
		}

		result.setKeepCallback(keepCallback);
		callbackContext.sendPluginResult(result);
	}

	private boolean isGPSdisabled() {
		boolean gps_enabled;
		try {
			gps_enabled = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
		} catch (Exception ex) {
			ex.printStackTrace();
			gps_enabled = false;
		}

		return !gps_enabled;
	}

	private boolean isNetworkDisabled() {
		boolean network_enabled;
		try {
			network_enabled = mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
		} catch (Exception ex) {
			ex.printStackTrace();
			network_enabled = false;
		}

		return !network_enabled;
	}


	private boolean validLocation(Location location, int maximumAge){
		return (location != null && (System.currentTimeMillis() - location.getTime()) <= maximumAge);
	}


	private void getLastLocation(JSONArray args, CallbackContext callbackContext) {
		int maximumAge;
		try {
			maximumAge = args.getInt(0);
		} catch (JSONException e) {
			e.printStackTrace();
			maximumAge = 1000;
		}

		// Check if we can use lastKnownLocation to get a quick reading and use
		// less battery
		Location last = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
		if ( !validLocation(last, maximumAge) ){
			last = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
		}

		if ( validLocation(last, maximumAge) ) {
			PluginResult result = new PluginResult(PluginResult.Status.OK, returnLocationJSON(last));
			callbackContext.sendPluginResult(result);
		} else {
			getCurrentLocation(callbackContext, Integer.MAX_VALUE);
		}
	}

	private void clearWatch(String id) {
		getListener().clearWatch(id);
	}

	private void getCurrentLocation(CallbackContext callbackContext, int timeout) {
		getListener().addCallback(callbackContext, timeout);
	}

	private void addWatch(String timerId, CallbackContext callbackContext) {
		getListener().addWatch(timerId, callbackContext);
	}

	private CordovaLocationListener getListener() {
		if (mListener == null) {
			mListener = new CordovaLocationListener(this, LocationUtils.APPTAG);
		}
		return mListener;
	}

    public void onRequestPermissionResult(int requestCode, String[] permissions,
                                          int[] grantResults) throws JSONException
    {
        PluginResult result;
        //This is important if we're using Cordova without using Cordova, but we have the geolocation plugin installed
        if(context != null) {
            for (int r : grantResults) {
                if (r == PackageManager.PERMISSION_DENIED) {
                    LOG.d(TAG, "Permission Denied!");
                    result = new PluginResult(PluginResult.Status.ILLEGAL_ACCESS_EXCEPTION);
                    context.sendPluginResult(result);
                    return;
                }

            }
            result = new PluginResult(PluginResult.Status.OK);
            context.sendPluginResult(result);
        }
    }

    public boolean hasPermisssion() {
        for(String p : permissions)
        {
            if(!PermissionHelper.hasPermission(this, p))
            {
                return false;
            }
        }
        return true;
    }

    /*
     * We override this so that we can access the permissions variable, which no longer exists in
     * the parent class, since we can't initialize it reliably in the constructor!
     */

    public void requestPermissions(int requestCode)
    {
        PermissionHelper.requestPermissions(this, requestCode, permissions);
    }

}
