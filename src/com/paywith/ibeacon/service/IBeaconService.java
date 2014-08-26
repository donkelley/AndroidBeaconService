/**
 * Radius Networks, Inc.
 * http://www.radiusnetworks.com
 *
 * @author David G. Young
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.paywith.ibeacon.service;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.provider.ContactsContract;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.Builder;
import android.util.Log;
import android.view.WindowManager;

import com.paywith.ibeacon.R;
import com.paywith.bluetooth.BluetoothCrashResolver;
import com.paywith.ibeacon.IBeacon;
import com.paywith.ibeacon.IBeaconManager;
import com.paywith.ibeacon.Region;
//import com.paywith.ibeacon.BuildConfig;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * @author dyoung
 */

@TargetApi(5)
public class IBeaconService extends Service {
    public static final String TAG = "IBeaconService";

    private Map<Region, RangeState> rangedRegionState = new HashMap<Region, RangeState>();
    private Map<Region, MonitorState> monitoredRegionState = new HashMap<Region, MonitorState>();
    private BluetoothAdapter bluetoothAdapter;
    private boolean scanning;
    private boolean scanningPaused;
    private Date lastIBeaconDetectionTime = new Date();
    private HashSet<IBeacon> trackedBeacons;
    int trackedBeaconsPacketCount;
    private Handler handler = new Handler();
    private int bindCount = 0;
    private BluetoothCrashResolver bluetoothCrashResolver;
    private boolean scanCyclerStarted = false;
    private boolean scanningEnabled = false;

	private static final String PREFERENCES = "com.paywith.paywith";
	private Context context;
	private SharedPreferences settings;// = context.getSharedPreferences(PREFERENCES, 0);
	private SharedPreferences.Editor editor;
	
	private IBeacon lastBeaconFound = null;
	private IBeacon lastBeaconFound2 = null;
	
	private Integer old_locid = 0;
	private Integer old_locid2 = 0;
	
	private Integer lastlocationid = 0;
	private String lastaction = "";
	
	private Integer noPaywithBeaconCount = 0;
	
    /*
     * The scan period is how long we wait between restarting the BLE advertisement scans
     * Each time we restart we only see the unique advertisements once (e.g. unique iBeacons)
     * So if we want updates, we have to restart.  iOS gets updates once per second, so ideally we
     * would restart scanning that often to get the same update rate.  The trouble is that when you 
     * restart scanning, it is not instantaneous, and you lose any iBeacon packets that were in the 
     * air during the restart.  So the more frequently you restart, the more packets you lose.  The
     * frequency is therefore a tradeoff.  Testing with 14 iBeacons, transmitting once per second,
     * here are the counts I got for various values of the SCAN_PERIOD:
     * 
     * Scan period     Avg iBeacons      % missed
     *    1s               6                 57
     *    2s               10                29
     *    3s               12                14
     *    5s               14                0
     *    
     * Also, because iBeacons transmit once per second, the scan period should not be an even multiple
     * of seconds, because then it may always miss a beacon that is synchronized with when it is stopping
     * scanning.
     * 
     */

    private long scanPeriod = IBeaconManager.DEFAULT_FOREGROUND_SCAN_PERIOD;
    private long betweenScanPeriod = IBeaconManager.DEFAULT_FOREGROUND_BETWEEN_SCAN_PERIOD;

    private List<IBeacon> simulatedScanData = null;

    // don kelley addition for running service in background and talking to paywith app:

	private BroadcastReceiver mReceiver;
	
    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class IBeaconBinder extends Binder {
        public IBeaconService getService() {
            Log.i(TAG, "getService of IBeaconBinder called");
            // Return this instance of LocalService so clients can call public methods
            return IBeaconService.this;
        }
    }


    /**
     * Command to the service to display a message
     */
    public static final int MSG_START_RANGING = 2;
    public static final int MSG_STOP_RANGING = 3;
    public static final int MSG_START_MONITORING = 4;
    public static final int MSG_STOP_MONITORING = 5;
    public static final int MSG_SET_SCAN_PERIODS = 6;
    public static final int MSG_SET_DO_LOCK = 7;
    public static final int MSG_SET_DO_UNLOCK = 8;
    public static final int MSG_START_SCANNING = 9;
    public static final int MSG_STOP_SCANNING = 10;


	Map<String, String> apiresult = null;  
	public PayWithAPI apiInstance;// = new PayWithAPI(this.getBaseContext());
	
    static class IncomingHandler extends Handler {
        private final WeakReference<IBeaconService> mService;

        IncomingHandler(IBeaconService service) {
            mService = new WeakReference<IBeaconService>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            IBeaconService service = mService.get();
            StartRMData startRMData = (StartRMData) msg.obj;

            if (service != null) {
                switch (msg.what) {
                    case MSG_START_RANGING:
                        Log.i(TAG, "start ranging received");
                        service.startRangingBeaconsInRegion(startRMData.getRegionData(), new com.paywith.ibeacon.service.Callback(startRMData.getCallbackPackageName()));
                        service.setScanPeriods(startRMData.getScanPeriod(), startRMData.getBetweenScanPeriod());
                    break;
                    case MSG_STOP_RANGING:
                        Log.i(TAG, "stop ranging received");
                        service.stopRangingBeaconsInRegion(startRMData.getRegionData());
                        service.setScanPeriods(startRMData.getScanPeriod(), startRMData.getBetweenScanPeriod());
                    break;
                    case MSG_START_MONITORING:
                        Log.i(TAG, "start monitoring received");
                        service.startMonitoringBeaconsInRegion(startRMData.getRegionData(), new com.paywith.ibeacon.service.Callback(startRMData.getCallbackPackageName()));
                        service.setScanPeriods(startRMData.getScanPeriod(), startRMData.getBetweenScanPeriod());
                    break;
                    case MSG_STOP_MONITORING:
                        Log.i(TAG, "stop monitoring received");
                        service.stopMonitoringBeaconsInRegion(startRMData.getRegionData());
                        service.setScanPeriods(startRMData.getScanPeriod(), startRMData.getBetweenScanPeriod());
                    break;
                    case MSG_SET_SCAN_PERIODS:
                        Log.i(TAG, "set scan intervals received");
                        service.setScanPeriods(startRMData.getScanPeriod(), startRMData.getBetweenScanPeriod());
                    break;
                    case MSG_SET_DO_LOCK:
                        Log.i(TAG, "do locks");
                        getLock(service.getBaseContext());
                    break;
                    case MSG_SET_DO_UNLOCK:
                        Log.i(TAG, "do locks");
                        PowerManager.WakeLock lock=getLock(service.getBaseContext());

                        if (lock.isHeld())
                            lock.release();
                    break;
                    case MSG_START_SCANNING:
                        Log.i(TAG, "start scanning received");
                        service.startScanning(new com.paywith.ibeacon.service.Callback(startRMData.getCallbackPackageName()));
                    break;
                    case MSG_STOP_SCANNING:
                        Log.i(TAG, "stop scanning received");
                        service.stopScanning(new com.paywith.ibeacon.service.Callback(startRMData.getCallbackPackageName()));
                    break;
                    default:
                        super.handleMessage(msg);
                }
            }
        }
    }

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final Messenger mMessenger = new Messenger(new IncomingHandler(this));

    /**
     * When binding to the service, we return an interface to our messenger
     * for sending messages to the service.
     */
    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "binding");
        bindCount++;
        return mMessenger.getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "unbinding");
        bindCount--;
        return false;
    }

    @Override
    public void onCreate() {
        //Log.i(TAG, "iBeaconService version "+ BuildConfig.VERSION_NAME+" is starting up");
        getBluetoothAdapter();
        bluetoothCrashResolver = new BluetoothCrashResolver(this);
        bluetoothCrashResolver.start();
        
        setupApiConnection();
        
        // Look for simulated scan data
        try {
            Class klass = Class.forName("com.paywith.ibeacon.SimulatedScanData");
            java.lang.reflect.Field f = klass.getField("iBeacons");
            this.simulatedScanData = (List<IBeacon>) f.get(null);
        } catch (ClassNotFoundException e) {
            if (IBeaconManager.debug)
                Log.d(TAG, "No com.paywith.ibeacon.SimulatedScanData class exists.");
        } catch (Exception e) {
            Log.e(TAG, "Cannot get simulated Scan data.  Make sure your com.paywith.ibeacon.SimulatedScanData class defines a field with the signature 'public static List<IBeacon> iBeacons'", e);
        }

        IntentFilter theFilter = new IntentFilter();
        theFilter.addAction(Intent.ACTION_SCREEN_OFF);
        theFilter.addAction(Intent.ACTION_SCREEN_ON);
        this.context = this.getBaseContext();
        settings = context.getSharedPreferences(PREFERENCES, 0);
        editor = settings.edit();
    }

    private void setupApiConnection() {
    	// this needs to happen not only during onCreate but also when restarting a beacon searching routine after logging in
        apiInstance = new PayWithAPI(this.getBaseContext());
    	//PayWithAPI apiInstance = new PayWithAPI(this.getBaseContext());
        apiInstance.setCurrentLaunchUrl("");
    }
    
    @Override
    @TargetApi(18)
    public void onDestroy() {
    	Log.e("service","onDestroy()");
        if (android.os.Build.VERSION.SDK_INT < 18) {
            Log.w(TAG, "Not supported prior to API 18.");
            return;
        }
		setNextLaunchUrl(null);
        bluetoothCrashResolver.stop();
        Log.i(TAG, "onDestroy called.  stopping scanning");
        handler.removeCallbacksAndMessages(null);
        scanLeDevice(false);
        if (bluetoothAdapter != null) {
            bluetoothAdapter.stopLeScan((BluetoothAdapter.LeScanCallback)getLeScanCallback());
            lastScanEndTime = new Date().getTime();
        }
        // remove any remaining system notifications that this service has generated
		NotificationManager notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.cancelAll();

    }

    private int ongoing_notification_id = 1;

    /* 
     * Returns true if the service is running, but all bound clients have indicated they are in the background
     */
    private boolean isInBackground() {
        if (IBeaconManager.debug) Log.d(TAG, "bound client count:" + bindCount);
        return bindCount == 0;
    }
    

	
	public Boolean getUserLoggedIn() {
		// Don Kelley, Aug 2014
		return settings.getBoolean("userLoggedIn",  false);
	}
	
	public void setNextLaunchUrl(String mLaunchURL) {
		// Don Kelley, August 2014
		editor.putString("nextLaunchUrl", mLaunchURL);
		editor.commit();
	}
	
	public void setNextLaunchName(String mMerchantName) {
		// Don Kelley, August 2014
		editor.putString("MerchantName", mMerchantName);
		editor.commit();
	}

    /**
     * methods for clients
     */
	
	public void startScanning(Callback callback) {
        setupApiConnection();
		if (getUserLoggedIn()) {
	        enableScanning();
		}
	}
	
	public void stopScanning(Callback callback) {
	    disableScanning();
	}

    public void startRangingBeaconsInRegion(Region region, Callback callback) {
    	//Log.e("Service","startRangingBeaconsInRegion()");
        synchronized (rangedRegionState) {
            if (rangedRegionState.containsKey(region)) {
                Log.i(TAG, "Already ranging that region -- will replace existing region.");
                rangedRegionState.remove(region); // need to remove it, otherwise the old object will be retained because they are .equal
            }
            rangedRegionState.put(region, new RangeState(callback));
        }
        if (IBeaconManager.debug)
            Log.d(TAG, "Currently ranging " + rangedRegionState.size() + " regions.");
        if (!scanningEnabled && getUserLoggedIn()) {
            enableScanning();
        }
    }

    public void stopRangingBeaconsInRegion(Region region) {
        synchronized (rangedRegionState) {
            rangedRegionState.remove(region);
        }
        if (IBeaconManager.debug)
            Log.d(TAG, "Currently ranging " + rangedRegionState.size() + " regions.");

        if (scanningEnabled && rangedRegionState.size() == 0 && monitoredRegionState.size() == 0) {
            disableScanning();
        }
    }

    // make background service persistent (auto restarts if killed off)???
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.i("Service", "onStartCommand");
		return android.app.Service.START_STICKY;
	}
	
    public void startMonitoringBeaconsInRegion(Region region, Callback callback) {
        if (IBeaconManager.debug)
            Log.d(TAG, "startMonitoring called");

        synchronized (monitoredRegionState) {
            if (monitoredRegionState.containsKey(region)) {
                Log.i(TAG, "Already monitoring that region -- will replace existing region monitor.");
                monitoredRegionState.remove(region); // need to remove it, otherwise the old object will be retained because they are .equal
            }
            monitoredRegionState.put(region, new MonitorState(callback));
        }
        if (IBeaconManager.debug)
            Log.d(TAG, "Currently monitoring " + monitoredRegionState.size() + " regions.");
        if (!scanningEnabled) {
            enableScanning();
        }
    }

    public void stopMonitoringBeaconsInRegion(Region region) {
        if (IBeaconManager.debug) Log.d(TAG, "stopMonitoring called");
        synchronized (monitoredRegionState) {
            monitoredRegionState.remove(region);
        }
        if (IBeaconManager.debug)
            Log.d(TAG, "Currently monitoring " + monitoredRegionState.size() + " regions.");
        if (scanningEnabled && rangedRegionState.size() == 0 && monitoredRegionState.size() == 0) {
            disableScanning();
        }
    }

    public void setScanPeriods(long scanPeriod, long betweenScanPeriod) {
        this.scanPeriod = scanPeriod;
        this.betweenScanPeriod = betweenScanPeriod;
        long now = new Date().getTime();
        if (nextScanStartTime > now) {
            // We are waiting to start scanning.  We may need to adjust the next start time
            // only do an adjustment if we need to make it happen sooner.  Otherwise, it will
            // take effect on the next cycle.
            long proposedNextScanStartTime = (lastScanEndTime + betweenScanPeriod);
            if (proposedNextScanStartTime < nextScanStartTime) {
                nextScanStartTime = proposedNextScanStartTime;
                Log.i(TAG, "Adjusted nextScanStartTime to be " + new Date(nextScanStartTime));
            }
        }
        if (scanStopTime > now) {
            // we are waiting to stop scanning.  We may need to adjust the stop time
            // only do an adjustment if we need to make it happen sooner.  Otherwise, it will
            // take effect on the next cycle.
            long proposedScanStopTime = (lastScanStartTime + scanPeriod);
            if (proposedScanStopTime < scanStopTime) {
                scanStopTime = proposedScanStopTime;
                Log.i(TAG, "Adjusted scanStopTime to be " + new Date(scanStopTime));
            }
        }
    }

    private long lastScanStartTime = 0l;
    private long lastScanEndTime = 0l;
    private long nextScanStartTime = 0l;
    private long scanStopTime = 0l;

    public void enableScanning() {
        scanningEnabled = true;
/*
get lock
        PowerManager.WakeLock lock=getLock(this.getApplicationContext());

        if (!lock.isHeld())
            lock.acquire();
*/
        if (!scanCyclerStarted)
            scanLeDevice(true);
    }

    public void disableScanning() {
        scanningEnabled = false;
    }

    @TargetApi(18)
    private void scanLeDevice(final Boolean enable) {
    	//Log.e("Service","scanLeDevice()");
        scanCyclerStarted = true;
        if (android.os.Build.VERSION.SDK_INT < 18) {
            Log.w(TAG, "Not supported prior to API 18.");
            return;
        }
        if (getBluetoothAdapter() == null) {
            Log.e(TAG, "No bluetooth adapter.  iBeaconService cannot scan.");
            Log.w(TAG, "exiting");
            return;
        }
        if (enable) {
            long millisecondsUntilStart = nextScanStartTime - (new Date().getTime());
            if (millisecondsUntilStart > 0) {
                if (IBeaconManager.debug)
                    Log.d(TAG, "Waiting to start next bluetooth scan for another " + millisecondsUntilStart + " milliseconds");
                // Don't actually wait until the next scan time -- only wait up to 1 second.  this
                // allows us to start scanning sooner if a consumer enters the foreground and expects
                // results more quickly
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                    	Log.e("Service","calling scanLeDevice(true)");
                        scanLeDevice(true);
                    }
                }, millisecondsUntilStart > 1000 ? 1000 : millisecondsUntilStart);
                return;
            }

            trackedBeacons = new HashSet<IBeacon>();
            trackedBeaconsPacketCount = 0;
            if (scanning == false || scanningPaused == true) {
                scanning = true;
                scanningPaused = false;
                try {
                    if (getBluetoothAdapter() != null) {
                        if (getBluetoothAdapter().isEnabled()) {
                            if (bluetoothCrashResolver.isRecoveryInProgress()) {
                                Log.e(TAG, "Skipping scan because crash recovery is in progress.");
                            }
                            else {
                                if (scanningEnabled) {
                                    getBluetoothAdapter().startLeScan((BluetoothAdapter.LeScanCallback)getLeScanCallback());
                                }
                                else {
                                    if (IBeaconManager.debug) Log.d(TAG, "Scanning unnecessary - no monitoring or ranging active.");
                                }
                            }
                            lastScanStartTime = new Date().getTime();
                        } else {
                            Log.e(TAG, "Bluetooth is disabled.  Cannot scan for iBeacons.");
                            //scheduleScanStop();
                            disableScanning();
                        }
                    }
                } catch (Exception e) {
                    Log.e("Service", "Exception starting bluetooth scan.  Perhaps bluetooth is disabled or unavailable?");
                }
            } else {
                if (IBeaconManager.debug) Log.d(TAG, "We are already scanning");
            }
            scanStopTime = (new Date().getTime() + scanPeriod);
            scheduleScanStop();

            if (IBeaconManager.debug) Log.d(TAG, "Scan started");
        } else {
            if (IBeaconManager.debug) Log.d(TAG, "disabling scan");
            scanning = false;
            if (getBluetoothAdapter() != null) {
                getBluetoothAdapter().stopLeScan((BluetoothAdapter.LeScanCallback)getLeScanCallback());
                lastScanEndTime = new Date().getTime();
            }
        }
    }

    private void scheduleScanStop() {
        // Stops scanning after a pre-defined scan period.
        long millisecondsUntilStop = scanStopTime - (new Date().getTime());
        if (millisecondsUntilStop > 0) {
            if (IBeaconManager.debug)
                Log.d(TAG, "Waiting to stop scan for another " + millisecondsUntilStop + " milliseconds");
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    scheduleScanStop();
                }
            }, millisecondsUntilStop > 1000 ? 1000 : millisecondsUntilStop);
        } else {
            finishScanCycle();
        }
    }

    @TargetApi(18)
    private void finishScanCycle() {
        if (android.os.Build.VERSION.SDK_INT < 18) {
            Log.w(TAG, "Not supported prior to API 18.");
            return;
        }
        if (IBeaconManager.debug) Log.d(TAG, "Done with scan cycle");
        processExpiredMonitors();
        if (scanning == true) {
            processRangeData();
            // If we want to use simulated scanning data, do it here.  This is used for testing in an emulator
            if (simulatedScanData != null) {
                // if simulatedScanData is provided, it will be seen every scan cycle.  *in addition* to anything actually seen in the air
                // it will not be used if we are not in debug mode
                Log.w(TAG, "Simulated scan data is deprecated and will be removed in a future release. Please use the new BeaconSimulator interface instead.");

                if (0 != (getApplicationInfo().flags &= ApplicationInfo.FLAG_DEBUGGABLE)) {
                    for (IBeacon iBeacon : simulatedScanData) {
                        processIBeaconFromScan(iBeacon);
                    }
                } else {
                    Log.w(TAG, "Simulated scan data provided, but ignored because we are not running in debug mode.  Please remove simulated scan data for production.");
                }
            }

            if (getBluetoothAdapter() != null) {
                if (getBluetoothAdapter().isEnabled()) {
                    getBluetoothAdapter().stopLeScan((BluetoothAdapter.LeScanCallback)getLeScanCallback());
                    lastScanEndTime = new Date().getTime();
                } else {
                    Log.w(TAG, "Bluetooth is disabled.  Cannot scan for iBeacons.");
                }
            }

            if (!anyRangingOrMonitoringRegionsActive()) {
                if (IBeaconManager.debug)
                    Log.d(TAG, "Not starting scan because no monitoring or ranging regions are defined.");
                scanCyclerStarted = false;
            } else {
                if (IBeaconManager.debug)
                    Log.d(TAG, "Restarting scan.  Unique beacons seen last cycle: " + trackedBeacons.size()+" Total iBeacon advertisement packets seen: "+trackedBeaconsPacketCount);

                scanningPaused = true;
                nextScanStartTime = (new Date().getTime() + betweenScanPeriod);
                if (scanningEnabled) {
                    scanLeDevice(true);
                }
                else {
                    if (IBeaconManager.debug) Log.d(TAG, "Scanning disabled.  No ranging or monitoring regions are active.");
                    scanCyclerStarted = false;
                }
            }
        }
    }

    private Object leScanCallback;
    @TargetApi(18)
    private Object getLeScanCallback() {
        if (leScanCallback == null) {
            leScanCallback = new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, final int rssi,final byte[] scanRecord) {
                    if (IBeaconManager.debug) Log.d(TAG, "got record");
                        new ScanProcessor().execute(new ScanData(device, rssi, scanRecord));
                }
            };
        }

        return leScanCallback;
    }

    private class ScanData {
        public ScanData(BluetoothDevice device, int rssi, byte[] scanRecord) {
            this.device = device;
            this.rssi = rssi;
            this.scanRecord = scanRecord;
        }

        @SuppressWarnings("unused")
        public BluetoothDevice device;
        public int rssi;
        public byte[] scanRecord;
    }

    private void processRangeData() {
    	// Don Kelley August 2014 (based loosely on original method from Radius)
    	// This is the method that does most of the custom paywith magical logic.
    	
    	// always check if user logged in before continuing
    	if (!getUserLoggedIn()) {
    		Log.e("processRangeData() ","user logged out");
            disableScanning();
            return;
    	}
        Iterator<Region> regionIterator = rangedRegionState.keySet().iterator();
        while (regionIterator.hasNext()) {
            Region region = regionIterator.next();
            RangeState rangeState = rangedRegionState.get(region);
            if (IBeaconManager.debug)
                Log.d(TAG, "Calling ranging callback");
            rangeState.getCallback().call(IBeaconService.this, "rangingData", new RangingData(rangeState.finalizeIBeacons(), region));

        	//Log.e("iterator",trackedBeacons);
        	IBeacon nearest_beacon = findNearest(trackedBeacons);

        	if (nearest_beacon == null) {
        		//Log.e("nearest_beacon() result","no beacon found");
    			// if no paywith beacon nearby, remove any of our notifications.
        		// Need this to only happen after several iterations of no beacon found.... 
        		noPaywithBeaconCount = noPaywithBeaconCount + 1; // inc the counter and remove notification if higher than 10 cycles
        		
        		if (noPaywithBeaconCount > 10) {
        			// clear all paywith notifications... nothing has been found for 10 cycles
        			NotificationManager notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        			notificationManager.cancelAll();
        			old_locid = 0;
        			old_locid2 = 0;
        			lastBeaconFound = null;
        			lastBeaconFound2 = null;
        			lastaction = "cancelAll";
        			setNextLaunchUrl(null);
        		}
        		return;
        	}
        	noPaywithBeaconCount = 0; // always reset the sequential nobeaconsfound counter once a nearby paywith beacon is found
        	
        	Integer m1 = nearest_beacon.getMinor();
        	Integer M1 = nearest_beacon.getMajor();
        	Integer m2 = 0; // just some safe default settings
        	Integer M2 = 0;
        	Integer m3 = 0;
        	Integer M3 = 0;
        	if (lastBeaconFound != null) {
        		m2 = lastBeaconFound.getMinor();
        		M2 = lastBeaconFound.getMajor();
        	}

        	if (lastBeaconFound2 != null) {
        		m3 = lastBeaconFound2.getMinor();
        		M3 = lastBeaconFound2.getMajor();
        	}

			//Log.e("service",m1.toString() + " = " + m2.toString() + " = " + m3.toString() + ", " + M1.toString() + " = " + M2.toString() + " = " + M3.toString());
    		lastBeaconFound2 = lastBeaconFound;
    		lastBeaconFound = nearest_beacon;
    		
    		//Log.e("matching?",m1.toString() + " = " + m2.toString() + ", " + M1.toString() + " = " + M2.toString());
        	if (!m1.equals(m2) || !M1.equals(M2) || !m2.equals(m3) || !M2.equals(M3)) {
        		// simple debouncer queue:
        		// We only get past this point if beacon found is same as last beacon found and also the one before that.
        		return;
        	}
        	
        	
        	Integer thisminor = nearest_beacon.getMinor();
        	String muuid = nearest_beacon.getProximityUuid();
        	Integer mmajor = nearest_beacon.getMajor();
        	Integer mminor = nearest_beacon.getMinor();
        	Double distance = nearest_beacon.getAccuracy();

        	String last_updated_at = null; // need to do the date formatting below.
        	String current_datetime = null;
        	String beaconurl;
    		String beaconname;
    		Integer location_id;
    		// also need to store results of this api beacon call in sharedprefs and check
    		// if data for a found beacon exists there and use that data before making a
    		// possibly unneccessary api callback about it.
    		
    		// note: I need to do something like this still (from iOS app):

        	String format = "yyyy-MM-dd HH:mm:ss Z";
    	    SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.US);
    	    //System.out.format("%30s %s\n", format, sdf.format(new Date(0)));
    	    //sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
    	    current_datetime = sdf.format(new Date(0)); // this will be used for beacon database getUpdatedAt/setUpdatedAt string dates    	 
    				 
    	    // Set a Beacon Shell object into UserHelper with last updated at of right now
    	    // so that we do not query for this Beacon again until +24hours since we do not want to overwhelm device
    	    /*NSDateFormatter *dateFormatter = [[NSDateFormatter alloc]init];
    	    [dateFormatter setDateFormat:DATE_FORMAT];
    	    NSString *last_updated_at = [dateFormatter stringFromDate:[NSDate date]];*/
    		// the call should include last_updated_at, @"last_updated_at
        	
        	// experimenting with storing in my new sqlite db and looking for existing records there before hitting api

            DatabaseHandler db = new DatabaseHandler(this);
             
            /**
             * CRUD Operations
             * */
            
            //Log.e("Select:", "Looking for beacon in db..");
            BeaconStore foundBeacon = db.getBeacon(muuid, mmajor, mminor);

            // Read all beacons (just a database demo example - not sure we'll ever need it in our app)
            /*Log.e("Reading: ", "Reading all beacons.."); 
            List<BeaconStore> beacons = db.getAllBeacons();       
             
            for (BeaconStore cn : beacons) {
                String log = "Id: "+cn.getID()+" ,uuid: " + cn.getUuid() + " ,major: " + cn.getMajor() + " ,minor: " + cn.getMinor();
                    // Writing Contacts to log
                Log.e("Beacon: ", log);
            }*/
            
            // beacon isn't in our phone database so poll api about it
            if (foundBeacon == null) {
            	Log.e("Service","Making API Call");
                //if (distance < 1) {
                // distance handling is done in nearest_beacon() now...
                //Log.e("IBeaconService","*** mminor=" + mminor.toString() + ", distance =" + distance.toString());
                Map<String, String> beaconApiData = runAppAPI("beaconInfoRequest", muuid, mmajor.toString(), mminor.toString());

                //Log.e("Insert: ", "Inserting beacon from api results.."); 
                //String mername = beaconApiData.get("name");
                //Integer locid = Integer.parseInt(beaconApiData.get("location_id"));
                //String url = beaconApiData.get("url");  
                
        		beaconurl = beaconApiData.get("url");
        		beaconname = beaconApiData.get("name");
        		location_id = Integer.parseInt(beaconApiData.get("location_id"));  		

                // Insert Beacon
                db.addBeacon(new BeaconStore(muuid, mmajor, mminor, current_datetime, beaconname, location_id, beaconurl));
                
            } else {

            	//Log.e("Service","!*!*! NOT Making API Call - beacon already in our sql db!");

        		beaconurl = foundBeacon.getUrl();
        		beaconname = foundBeacon.getMerchantName();
        		location_id = foundBeacon.getLocationId();   
            }
            
            // Check if database current_datetime for this beacon is older than 1 hour and older than 24 hours.
            // different logic may result based on those values.
            // for example, if greater than 24 hours old, we should still poll the api for updated info about this beacon.
            // 
            
            //Log.e("ibeaconservice","about to send data to app");
            //Log.e("beaconurl"," " + beaconurl);
        	if (beaconurl != null && beaconurl != "signinfailure") {
        		// force open app at payment page:
        		//Log.e("service","beaconurl = " + beaconurl);
        		String previousNotificationUrl = apiInstance.getCurrentLaunchUrl();
    			// only generate notification if it's different from the last one
    			String notetext = "Pay with your phone";
    			notetext = notetext + " at " + beaconname;
    			
    			//Log.e("service","lastaction=" + lastaction + ", lastlocationid=" + lastlocationid.toString() + ", location_id=" + location_id.toString());
    			//Log.e("locid before N/1/2",location_id+"/"+old_locid+"/"+old_locid2);
				// if location_id has changed from last two sent by a notification in this background service:
				if (location_id.equals(old_locid) && old_locid.equals(old_locid2)){
					//old_locid = location_id;
					PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
					KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
		            boolean isScreenOn = powerManager.isScreenOn();
					boolean keyboardLocked = km.inKeyguardRestrictedInputMode();
		              
        			if (isForeground("com.paywith.paywith") && isScreenOn && !keyboardLocked) {
        				// only if the app is running main on your phone AND phone is currently unlocked

        				// only do this if:
        				// the last thing this service did was NOT send a location id
        				// OR the last thing this service did was NOT send this same location change

        				//if (!lastaction.equals("moveLocationToTop") || 
        				//		lastaction.equals("moveLocationToTop") && !lastlocationid.equals(location_id)) {

            				//Log.e("service","sending new location to locationlist:" + location_id.toString());
        					// tell app to move this location to top of location list if possible
        					moveLocationToTop(location_id.toString());
        				//}
        				lastaction = "moveLocationToTop";
        			} else {
        				// only if app is running in background or not running at all.

        				// only do this if:
        				// the last thing this service did was NOT send a location id
        				// OR the last thing this service did was NOT send this same location change
        				// ALSO only notify if this location hasn't been notified within last hour.  (still necessary?  not sure...)
        				// (trying without the hour thing for now).

        				if (!lastaction.equals("generateNotification") ||
        						lastaction.equals("generateNotification") && !lastlocationid.equals(location_id)) { // add hour timer here also        					
        				
        					// generate notification.
        					generateNotification("Pay Here",notetext, beaconurl, location_id.toString());// this generates system notification
        					setNextLaunchUrl(beaconurl);
        					setNextLaunchName(beaconname);
        					lastaction = "generateNotification";
        				}// else {
        					//Log.e("service","skipping generateNotification because same as last sent event");
        				//}
        			}
        			lastlocationid = location_id;
				}
				// update location_id queue history
    			old_locid2 = old_locid;
    			old_locid = location_id;	
        	}
        }

    }

    private void processExpiredMonitors() {
        Iterator<Region> monitoredRegionIterator = monitoredRegionState.keySet().iterator();
        while (monitoredRegionIterator.hasNext()) {
            Region region = monitoredRegionIterator.next();
            MonitorState state = monitoredRegionState.get(region);
            if (state.isNewlyOutside()) {
                if (IBeaconManager.debug) Log.d(TAG, "found a monitor that expired: " + region);
                state.getCallback().call(IBeaconService.this, "monitoringData", new MonitoringData(state.isInside(), region));
            }
        }
    }

    private void processIBeaconFromScan(IBeacon iBeacon) {
        //if (IBeaconManager.debug) Log.d(TAG,"iBeacon detected multiple times in scan cycle :" + iBeacon.getProximityUuid() + " "+ iBeacon.getMajor() + " " + iBeacon.getMinor());
        //Log.e("ibeacon?","test");

    	// check for uuid = 5e6e6190-0b83-0132-9a3d-12313d1ca6ab (paywith beacon uuid) or skip:
    	// note: estimote default uuid is B9407F30-F5F8-466E-AFF9-25556B57FE6D
    	// note: radius default uuid is 52414449-5553-4E45-5457-4F524B53434F
    	// note: IOD default uuid is 
    	//Log.w("check if paywithbeacon",iBeacon.getProximityUuid().toString().toUpperCase());
    	if (!iBeacon.getProximityUuid().toString().toUpperCase().equals("B9407F30-F5F8-466E-AFF9-25556B57FE6D") &&
    			!iBeacon.getProximityUuid().toString().toUpperCase().equals("5E6E6190-0B83-0132-9A3D-12313D1CA6AB")) {
    		// not our beacon?  don't include it!
    		//Log.w("check result","not paywith");
    		return;
    	}
    	
    	lastIBeaconDetectionTime = new Date();
        trackedBeaconsPacketCount++;
        if (trackedBeacons.contains(iBeacon)) {
            if (IBeaconManager.debug)
                Log.d(TAG,"iBeacon detected multiple times in scan cycle :" + iBeacon.getProximityUuid() + " "+ iBeacon.getMajor() + " " + iBeacon.getMinor());
        }
        trackedBeacons.add(iBeacon);
        if (IBeaconManager.debug)
            Log.d(TAG,"iBeacon detected :" + iBeacon.getProximityUuid() + " " + iBeacon.getMajor() + " " + iBeacon.getMinor());
        //runAppAPI();
        List<Region> matchedRegions = null;
        synchronized(monitoredRegionState) {
            matchedRegions = matchingRegions(iBeacon,
                    monitoredRegionState.keySet());
        }
        Iterator<Region> matchedRegionIterator = matchedRegions.iterator();
        while (matchedRegionIterator.hasNext()) {
        	Log.d(TAG, "looping through region iterator");
            Region region = matchedRegionIterator.next();
            MonitorState state = monitoredRegionState.get(region);
            if (state.markInside()) {
                state.getCallback().call(IBeaconService.this, "monitoringData",
                        new MonitoringData(state.isInside(), region));
            }
        }

        if (IBeaconManager.debug) Log.d(TAG, "looking for ranging region matches for this ibeacon");
        synchronized (rangedRegionState) {
            matchedRegions = matchingRegions(iBeacon, rangedRegionState.keySet());
        }
        matchedRegionIterator = matchedRegions.iterator();
        while (matchedRegionIterator.hasNext()) {
            Region region = matchedRegionIterator.next();
            if (IBeaconManager.debug) Log.d(TAG, "matches ranging region: " + region);
            RangeState rangeState = rangedRegionState.get(region);
            synchronized (rangeState) {
            	rangeState.addIBeacon(iBeacon);
			}
        }
    }

    private class ScanProcessor extends AsyncTask<ScanData, Void, Void> {

        @Override
        protected Void doInBackground(ScanData... params) {
        	if (IBeaconManager.debug) Log.d(TAG, "ScanProcessor");
            ScanData scanData = params[0];

            IBeacon iBeacon = IBeacon.fromScanData(scanData.scanRecord,scanData.rssi, scanData.device);

            if (iBeacon != null)
                processIBeaconFromScan(iBeacon);

            bluetoothCrashResolver.notifyScannedDevice(scanData.device, (BluetoothAdapter.LeScanCallback)getLeScanCallback());
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
        }

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected void onProgressUpdate(Void... values) {
        }
    }

    private List<Region> matchingRegions(IBeacon iBeacon, Collection<Region> regions) {
        List<Region> matched = new ArrayList<Region>();
            Iterator<Region> regionIterator = regions.iterator();
            while (regionIterator.hasNext()) {
                Region region = regionIterator.next();
                if (region.matchesIBeacon(iBeacon)) {
                    matched.add(region);
                } else {
                    if (IBeaconManager.debug) Log.d(TAG, "This region does not match: " + region);
                }

            }

        return matched;
    }

    private void launchApp(String murl) {
    	// call this to launch the main paywith app
    	Intent intent = new Intent("android.intent.category.LAUNCHER");
    	intent.setClassName("com.paywith.paywith", "com.paywith.paywith.MainActivity");
    	//intent.setClassName("com.paywith.paywith", "com.paywith.paywith.BeaconToAppActivity");
    	intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    	intent.putExtra("OpenUrl", murl);
    	startActivity(intent);
    }

    private Map<String, String> runAppAPI(String sent_action, String muuid, String mmajor, String mminor) {
		//Log.e("IBeaconService","runAppAPI() sent_action=" + sent_action);
    	// call this to access the main paywith app API callbacks.
    	//call like this:
    	//Log.e("IBeaconService","runAppAPI");
    	//	runAppAPI("beaconInfoRequest", muuid, mmajor, mminor);
    	if (sent_action.equals("beaconInfoRequest")) {
    		//Log.e("IBeaconService apiInstance created","sent_action = beaconInfoResult");
    		apiresult = apiInstance.getBeacon(muuid,  mmajor,  mminor);
    		if (apiresult == null) {
    			//Log.e("IBeaconService:runAppAPI","apiresult == null");
    			return null;
    		}

    		String beaconname = apiresult.get("name");
    		if (beaconname == "") {
    			// need beaconname for this to be of any use
    			return null;
    		}
    		
    	}

    	return apiresult;
    }
    
    protected void generateNotification(String title, String merchanttext, String murl, String location_id) {
    	
    	String ns = Context.NOTIFICATION_SERVICE;
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);

    	Builder mNotifyBuilder = new NotificationCompat.Builder(this);

        //int icon = R.drawable.ic_launcher;
        //CharSequence tickerText = "PayWith";
        //long when = System.currentTimeMillis();

        //@SuppressWarnings("deprecation")
		//Notification notification = new Notification(icon,
        //        tickerText, when);

    	mNotifyBuilder.setContentText(merchanttext)
    		.setContentTitle(title)
    	    .setSmallIcon(R.drawable.ic_launcher);//logo
    	
        //notification.flags |= Notification.FLAG_AUTO_CANCEL;
        Context context = getApplicationContext();
    	Intent notificationIntent = new Intent("android.intent.category.LAUNCHER");
        //CharSequence contentTitle = title;
        //CharSequence contentText = merchanttext;

    	notificationIntent.putExtra("OpenUrl", murl);
    	notificationIntent.putExtra("MerchantName", merchanttext);
    	notificationIntent.setClassName("com.paywith.paywith", "com.paywith.paywith.MainActivity");

        //PendingIntent contentIntent = PendingIntent
       //         .getActivity(context, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT);

        //notification.setLatestEventInfo(context, contentTitle,
        //        contentText, contentIntent);

        //mNotificationManager.notify(ongoing_notification_id, notification);
        
    	PendingIntent contentIntent = PendingIntent
    		                .getActivity(context, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT);
    	// PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT

    	// this gives us flexibility to add vibration and sound etc:
    	Notification note = mNotifyBuilder.build();

    	// THIS line is what makes the notification tappable to launch app and generate mCard payment:
    	note.setLatestEventInfo(context, title, merchanttext, contentIntent);

    	// make phone vibrate and make sound on notification:
        note.defaults |= Notification.DEFAULT_VIBRATE;
        note.defaults |= Notification.DEFAULT_SOUND;
        
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        //Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP

        note.flags |= Notification.FLAG_AUTO_CANCEL;
	    // Because the ID remains unchanged, the existing notification is updated.
        mNotificationManager.notify(ongoing_notification_id, note);
    	//Log.e("notification","sent");
    }
    
    protected void moveLocationToTop(String location_id) {
    	Intent intent = new Intent("android.intent.category.LAUNCHER");
    	intent.setClassName("com.paywith.paywith", "com.paywith.paywith.MainActivity");
    	intent.putExtra("location_id", location_id);
    	//intent.setClassName("com.paywith.paywith", "com.paywith.paywith.BeaconToAppActivity");
    	intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    	startActivity(intent);
    }
    
    /*
     Returns false if no ranging or monitoring regions have beeen requested.  This is useful in determining if we should scan at all.
     */
    public boolean anyRangingOrMonitoringRegionsActive() {
        return (rangedRegionState.size() + monitoredRegionState.size()) > 0;
    }

    @TargetApi(18)
    private BluetoothAdapter getBluetoothAdapter() {
        if (android.os.Build.VERSION.SDK_INT < 18) {
            Log.w(TAG, "Not supported prior to API 18.");
            return null;
        }
        if (bluetoothAdapter == null) {
            // Initializes Bluetooth adapter.
            final BluetoothManager bluetoothManager = (BluetoothManager) this.getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE);
            bluetoothAdapter = bluetoothManager.getAdapter();
        }
        return bluetoothAdapter;
    }

    //TA changes
    static final String NAME = IBeaconService.class.getName();
    private static volatile PowerManager.WakeLock lockStatic = null;

    synchronized private static PowerManager.WakeLock getLock(Context context) {
        if (lockStatic == null) {
            PowerManager mgr = (PowerManager)context.getSystemService(Context.POWER_SERVICE);

            lockStatic = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, NAME);
            lockStatic.setReferenceCounted(true);
        }

        return(lockStatic);
    }
    
    public boolean isForeground(String myPackage){
    	ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
    	List< ActivityManager.RunningTaskInfo > runningTaskInfo = manager.getRunningTasks(1); 

    	ComponentName componentInfo = runningTaskInfo.get(0).topActivity;
    	if(componentInfo.getPackageName().equals(myPackage)) {
    		return true;
    	}
    	return false;
    }
	
	protected synchronized IBeacon findNearest(Collection<IBeacon> beacons) {
		// must convert linkedhashmap to map for looping through data
		//Map<String, Beacon> beaconsMap = beacons; //beacons
		//Iterator<Entry<String, Beacon>> beaconIt = beacons.entrySet().iterator();
		// nearestDistance is initially set higher than any beacon would reasonably be
		Double nearestDistance = IBeaconManager.alertRangeNear;//200.0;
		// nearestBeacon will be set to the Beacon object whenever it's found to be nearest
		IBeacon nearestBeacon = null;// = new IBeacon();
		//Collection<IBeacon> beaconsToCheck;
		//synchronized Collection<IBeacon> beaconsToCheck() {
			
		//}

	    /*public synchronized Collection<IBeacon> finalizeIBeacons() {
	        ArrayList<IBeacon> iBeacons = new ArrayList<IBeacon>();
	        Map<IBeacon,RangedIBeacon> newRangedIBeacons = new HashMap<IBeacon,RangedIBeacon>();

	        synchronized (rangedIBeacons) {
	            for (IBeacon iBeacon : rangedIBeacons.keySet()) {
	            	
	            	
	            }
	        }
	    }*/
		synchronized (beacons) {
			
			for (Iterator<IBeacon> iterator = beacons.iterator(); iterator.hasNext();) {
				//Log.e("looping through beacons", "findnearest");
				IBeacon aBeacon = (IBeacon) iterator.next();
				Integer minor = aBeacon.getMinor();
				Double aBeaconDistance = aBeacon.getAccuracy();
				//Log.d("findNearest", minor.toString() + " :: " + aBeaconDistance.toString());
				//Long aBeaconTimeScannedEpoch = aBeacon.getTimeScannedEpoch();
				if (aBeaconDistance < nearestDistance) {
					// this beacon is closest in list so far... (first beacon in list always at first followed by anything closer)
					nearestBeacon = aBeacon;
					nearestDistance = aBeaconDistance;
				}
			}

		}
		
		return nearestBeacon;
	}
}
