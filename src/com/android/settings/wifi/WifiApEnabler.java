/*
 * Copyright (C) 2010 The Android Open Source Project
 *
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
 */

package com.android.settings.wifi;

import com.android.settings.R;
import com.android.settings.wifi.HotsoptService;
import com.android.settings.WirelessSettings;

import java.util.ArrayList;
import java.util.List;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

public class WifiApEnabler {
    private final Context mContext;
    private final SwitchPreference mSwitch;
    private final CharSequence mOriginalSummary;
    private WifiManager mWifiManager;
    private final IntentFilter mIntentFilter;

    ConnectivityManager mCm;
    private String[] mWifiRegexs;
    /* Indicates if we have to wait for WIFI_STATE_CHANGED intent */
    private boolean mWaitForWifiStateChange;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiManager.WIFI_AP_STATE_CHANGED_ACTION.equals(action)) {
                handleWifiApStateChanged(intent.getIntExtra(
                        WifiManager.EXTRA_WIFI_AP_STATE, WifiManager.WIFI_AP_STATE_FAILED));
            } else if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
                if (mWaitForWifiStateChange == true) {
                    handleWifiStateChanged(intent.getIntExtra(
                            WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN));
                }
            } else if (ConnectivityManager.ACTION_TETHER_STATE_CHANGED.equals(action)) {
                ArrayList<String> available = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_AVAILABLE_TETHER);
                ArrayList<String> active = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_ACTIVE_TETHER);
                ArrayList<String> errored = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_ERRORED_TETHER);
                updateTetherState(available.toArray(), active.toArray(), errored.toArray());
            } else if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                enableWifiSwitch();
            }

        }
    };

    public WifiApEnabler(Context context, SwitchPreference switchPreference) {
        mContext = context;
        mSwitch = switchPreference;
        mOriginalSummary = switchPreference.getSummary();
        switchPreference.setPersistent(false);
        mWaitForWifiStateChange = false;

        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        mCm = (ConnectivityManager)mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        mWifiRegexs = mCm.getTetherableWifiRegexs();

        mIntentFilter = new IntentFilter(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(ConnectivityManager.ACTION_TETHER_STATE_CHANGED);
        mIntentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        mIntentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
    }

    public void resume() {
        mContext.registerReceiver(mReceiver, mIntentFilter);
        enableWifiSwitch();
    }

    public void pause() {
        mContext.unregisterReceiver(mReceiver);
    }

    private void enableWifiSwitch() {
        boolean isAirplaneMode = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
        if(!isAirplaneMode) {
            mSwitch.setEnabled(true);
        } else {
            mSwitch.setSummary(mOriginalSummary);
            mSwitch.setEnabled(false);
        }
    }

    public void setSoftapEnabled(boolean enable) {
        final ContentResolver cr = mContext.getContentResolver();
        int wifiSavedState = 0;
        /**
         * Disable Wifi if enabling tethering
         */
        int wifiState = mWifiManager.getWifiState();
        if (enable && ((wifiState == WifiManager.WIFI_STATE_ENABLING) ||
                    (wifiState == WifiManager.WIFI_STATE_ENABLED))) {
            mWifiManager.setWifiEnabled(false);
            Settings.Global.putInt(cr, Settings.Global.WIFI_SAVED_STATE, 1);
        }
        /**
         * Check if we have to wait for the WIFI_STATE_CHANGED intent
         * before we re-enable the Checkbox.
         */
        if (!enable) {
            try {
                wifiSavedState = Settings.Global.getInt(cr, Settings.Global.WIFI_SAVED_STATE);
            } catch (Settings.SettingNotFoundException e) {
                ;
            }

            if (wifiSavedState == 1) {
                 mWaitForWifiStateChange = true;
            }
        }

        if (mWifiManager.setWifiApEnabled(null, enable)) {
            /* Disable here, enabled on receiving success broadcast */
            mSwitch.setEnabled(false);
        } else {
            mSwitch.setSummary(R.string.wifi_error);
        }

        /**
         * If needed, restore Wifi on tether disable
         */
        if (!enable) {
            if (wifiSavedState == 1) {
                mWifiManager.setWifiEnabled(true);
                Settings.Global.putInt(cr, Settings.Global.WIFI_SAVED_STATE, 0);
            }
        }
    }

    public void updateConfigSummary(WifiConfiguration wifiConfig) {
        String s = mContext.getString(
                com.android.internal.R.string.wifi_tether_configure_ssid_default);
        mSwitch.setSummary(String.format(
                    mContext.getString(R.string.wifi_tether_enabled_subtext),
                    (wifiConfig == null) ? s : wifiConfig.SSID));
    }

    private void updateTetherState(Object[] available, Object[] tethered, Object[] errored) {
        boolean wifiTethered = false;
        boolean wifiErrored = false;

        for (Object o : tethered) {
            String s = (String)o;
            for (String regex : mWifiRegexs) {
                if (s.matches(regex)) wifiTethered = true;
            }
        }
        for (Object o: errored) {
            String s = (String)o;
            for (String regex : mWifiRegexs) {
                if (s.matches(regex)) wifiErrored = true;
            }
        }

        if (wifiTethered) {
            WifiConfiguration wifiConfig = mWifiManager.getWifiApConfiguration();
            updateConfigSummary(wifiConfig);
        } else if (wifiErrored) {
            mSwitch.setSummary(R.string.wifi_error);
        }
    }

    private void handleWifiApStateChanged(int state) {
        String hotsoptServiceClassName = HotsoptService.class.getName();
        switch (state) {
            case WifiManager.WIFI_AP_STATE_ENABLING:
                mSwitch.setSummary(R.string.wifi_tether_starting);
                mSwitch.setEnabled(false);
                break;
            case WifiManager.WIFI_AP_STATE_ENABLED:
                /**
                 * Summary on enable is handled by tether
                 * broadcast notice
                 */
                if (isWifiHotsoptEnabled() &&
                        (!isServiceRunning(mContext, hotsoptServiceClassName))) {
                    Intent intent = new Intent().setClassName(mContext,
                            hotsoptServiceClassName);
                    mContext.startService(intent);
                }
                mSwitch.setChecked(true);
                /* Doesnt need the airplane check */
                mSwitch.setEnabled(true);
                break;
            case WifiManager.WIFI_AP_STATE_DISABLING:
                mSwitch.setSummary(R.string.wifi_tether_stopping);
                mSwitch.setEnabled(false);
                break;
            case WifiManager.WIFI_AP_STATE_DISABLED:
                if (isWifiHotsoptEnabled() &&
                        isServiceRunning(mContext, hotsoptServiceClassName)) {
                    Intent intent = new Intent().setClassName(mContext,
                            hotsoptServiceClassName);
                    mContext.stopService(intent);
                }
                mSwitch.setChecked(false);
                mSwitch.setSummary(mOriginalSummary);
                if (mWaitForWifiStateChange == false) {
                    enableWifiSwitch();
                }
                break;
            default:
                mSwitch.setChecked(false);
                mSwitch.setSummary(R.string.wifi_error);
                enableWifiSwitch();
        }
    }

    private void handleWifiStateChanged(int state) {
        switch (state) {
            case WifiManager.WIFI_STATE_ENABLED:
            case WifiManager.WIFI_STATE_UNKNOWN:
                enableWifiSwitch();
                mWaitForWifiStateChange = false;
                break;
            default:
        }
    }

    private boolean isWifiHotsoptEnabled() {
        boolean isWifiHotspotEnable = false;
        if (mContext != null) {
            isWifiHotspotEnable = mContext.getResources().getBoolean(
                    R.bool.def_wifi_hotspot_enable);
        }
        return isWifiHotspotEnable;
    }

    private boolean isServiceRunning(Context context, String className) {
        boolean isRunning = false;
        if (context != null && (!TextUtils.isEmpty(className))) {
            ActivityManager activityManager = (ActivityManager)
                    context.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningServiceInfo> serviceList = activityManager
                    .getRunningServices(100);
            if (serviceList != null) {
                for (int i = 0; i < serviceList.size(); i++) {
                    if (serviceList.get(i).service.getClassName().equals(className)) {
                        isRunning = true;
                        break;
                    }
                }
            }
        }
        return isRunning;
    }
}
