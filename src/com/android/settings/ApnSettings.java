/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.settings;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources.NotFoundException;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemProperties;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.provider.Telephony;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.UiccController;

import java.util.ArrayList;

public class ApnSettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {
    static final String TAG = "ApnSettings";

    public static final String EXTRA_POSITION = "position";
    public static final String RESTORE_CARRIERS_URI =
        "content://telephony/carriers/restore";
    public static final String PREFERRED_APN_URI =
        "content://telephony/carriers/preferapn";
    public static final String OPERATOR_NUMERIC_EXTRA = "operator";

    public static final String APN_ID = "apn_id";

    private static final String APN_TYPE_DM = "dm";

    private static final int ID_INDEX = 0;
    private static final int NAME_INDEX = 1;
    private static final int APN_INDEX = 2;
    private static final int TYPES_INDEX = 3;
    private static final int RO_INDEX = 4;
    private static final int LOCALIZED_NAME_INDEX = 5;

    private static final int MENU_NEW = Menu.FIRST;
    private static final int MENU_RESTORE = Menu.FIRST + 1;

    private static final int EVENT_RESTORE_DEFAULTAPN_START = 1;
    private static final int EVENT_RESTORE_DEFAULTAPN_COMPLETE = 2;

    private static final int DIALOG_RESTORE_DEFAULTAPN = 1001;

    private static final Uri DEFAULTAPN_URI = Uri.parse(RESTORE_CARRIERS_URI);
    private static final Uri PREFERAPN_URI = Uri.parse(PREFERRED_APN_URI);

    private static boolean mRestoreDefaultApnMode;

    private RestoreApnUiHandler mRestoreApnUiHandler;
    private RestoreApnProcessHandler mRestoreApnProcessHandler;
    private HandlerThread mRestoreDefaultApnThread;

    private UserManager mUm;
    private long mSubId;

    private String mSelectedKey;

    private boolean mUseNvOperatorForEhrpd = SystemProperties.getBoolean(
            "persist.radio.use_nv_for_ehrpd", false);

    private IntentFilter mMobileStateFilter;

    private boolean mUnavailable;

    private final BroadcastReceiver mMobileStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(
                    TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED)) {
                PhoneConstants.DataState state = getMobileDataState(intent);
                switch (state) {
                case CONNECTED:
                    if (!mRestoreDefaultApnMode) {
                        fillList();
                    } else {
                        showDialog(DIALOG_RESTORE_DEFAULTAPN);
                    }
                    break;
                }
            }
        }
    };

    private static PhoneConstants.DataState getMobileDataState(Intent intent) {
        String str = intent.getStringExtra(PhoneConstants.STATE_KEY);
        if (str != null) {
            return Enum.valueOf(PhoneConstants.DataState.class, str);
        } else {
            return PhoneConstants.DataState.DISCONNECTED;
        }
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mUm = (UserManager) getSystemService(Context.USER_SERVICE);

        mMobileStateFilter = new IntentFilter(
                TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED);

        if (!mUm.hasUserRestriction(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)) {
            setHasOptionsMenu(true);
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        TextView empty = (TextView) getView().findViewById(android.R.id.empty);
        if (empty != null) {
            empty.setText(R.string.apn_settings_not_available);
            getListView().setEmptyView(empty);
        }

        if (mUm.hasUserRestriction(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)) {
            mUnavailable = true;
            setPreferenceScreen(new PreferenceScreen(getActivity(), null));
            return;
        }

        addPreferencesFromResource(R.xml.apn_settings);
        getListView().setItemsCanFocus(true);
        mSubId = getActivity().getIntent().getLongExtra(PhoneConstants.SUBSCRIPTION_KEY,
                SubscriptionManager.getDefaultDataSubId());
        Log.d(TAG, "onCreate received subId :" + mSubId);
        mMobileStateFilter = new IntentFilter(
                TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mUnavailable) {
            return;
        }

        getActivity().registerReceiver(mMobileStateReceiver, mMobileStateFilter);

        if (!mRestoreDefaultApnMode) {
            fillList();
        }
    }

    private Uri getUri(Uri uri) {
        return Uri.withAppendedPath(uri, "/subId/" + mSubId);
    }


    @Override
    public void onPause() {
        super.onPause();

        if (mUnavailable) {
            return;
        }

        getActivity().unregisterReceiver(mMobileStateReceiver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mRestoreDefaultApnThread != null) {
            mRestoreDefaultApnThread.quit();
        }
    }

    private void fillList() {
        boolean isSelectedKeyMatch = false;
        String where = getOperatorNumericSelection();
        // Hide nothing if property is false, default is true
        if (SystemProperties.getBoolean("persist.sys.hideapn", true)) {
            //remove the filtered items, no need to show in UI
            where += " and type <>\"" + PhoneConstants.APN_TYPE_IA + "\"";

            // Filer fota and dm for specail carrier
            if (getResources().getBoolean(R.bool.config_hide_dm_enabled)) {
                String operatorMccMnc = TelephonyManager.getDefault().getIccOperatorNumeric(mSubId);
                for (String plmn : getResources().getStringArray(R.array.hidedm_plmn_list)) {
                    if (plmn.equals(operatorMccMnc)) {
                        where += " and type <>\"" + PhoneConstants.APN_TYPE_FOTA + "\"";
                        where += " and type <>\"" + APN_TYPE_DM + "\"";
                        break;
                    }
                }
            }

            if (getResources().getBoolean(R.bool.config_hidesupl_enable)) {
                boolean needHideSupl = false;
                for (String plmn : getResources().getStringArray(R.array.hidesupl_plmn_list)) {
                    if (plmn.equals(TelephonyManager.getDefault()
                               .getSimOperator(mSubId))) {
                        needHideSupl = true;
                        break;
                    }
                }

                if (needHideSupl) {
                    where += " and type <>\"" + PhoneConstants.APN_TYPE_SUPL + "\"";
                }
            }

            //Hide mms if config is true
            if(getResources().getBoolean(R.bool.config_mms_enable)) {
                where += " and type <>\"" + PhoneConstants.APN_TYPE_MMS + "\"" ;
            }
        }

        where += " and carrier_enabled = 1";
        Log.d(TAG, "fillList: where= " + where);

        if (TextUtils.isEmpty(where)) {
            Log.d(TAG, "getOperatorNumericSelection is empty ");
            return;
        }

        Cursor cursor = getContentResolver().query(getUri(Telephony.Carriers.CONTENT_URI),
                new String[] {"_id", "name", "apn", "type", "read_only", "localized_name"}, where, null,
                Telephony.Carriers.DEFAULT_SORT_ORDER);

        if (cursor != null) {
            PreferenceGroup apnList = (PreferenceGroup) findPreference("apn_list");
            apnList.removeAll();

            ArrayList<Preference> mmsApnList = new ArrayList<Preference>();

            mSelectedKey = getSelectedApnKey();
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                String name = cursor.getString(NAME_INDEX);
                String apn = cursor.getString(APN_INDEX);
                String key = cursor.getString(ID_INDEX);
                String type = cursor.getString(TYPES_INDEX);
                boolean readOnly = (cursor.getInt(RO_INDEX) == 1);

                String localizedName = getLocalizedName(getActivity(), cursor, LOCALIZED_NAME_INDEX);
                if (!TextUtils.isEmpty(localizedName)) {
                    name = localizedName;
                }

                ApnPreference pref = new ApnPreference(getActivity());

                pref.setApnReadOnly(readOnly);
                pref.setKey(key);
                pref.setTitle(name);
                pref.setSummary(apn);
                pref.setPersistent(false);
                pref.setOnPreferenceChangeListener(this);

                boolean selectable = ((type == null) || !type.equals("mms"));
                pref.setSelectable(selectable);
                if (selectable) {
                    if ((mSelectedKey != null) && mSelectedKey.equals(key)) {
                        pref.setChecked();
                        isSelectedKeyMatch = true;
                        Log.d(TAG, "find select key = " + mSelectedKey);
                    }
                    apnList.addPreference(pref);
                } else {
                    mmsApnList.add(pref);
                }
                cursor.moveToNext();
            }
            //if find no selectedKey, set the first one as selected key
            if (!isSelectedKeyMatch && apnList.getPreferenceCount() > 0) {
                ApnPreference pref = (ApnPreference) apnList.getPreference(0);
                pref.setChecked();
                setSelectedApnKey(pref.getKey());
                Log.d(TAG, "find no select key = " + mSelectedKey);
                Log.d(TAG, "set key to  " +pref.getKey());
            }
            cursor.close();

            for (Preference preference : mmsApnList) {
                apnList.addPreference(preference);
            }
        }
    }

    public static String getLocalizedName(Context context, Cursor cursor, int index) {
        // If can find a localized name, replace the APN name with it
        String resName = cursor.getString(index);
        String localizedName = null;
        if (resName != null && !resName.isEmpty()) {
            int resId = context.getResources().getIdentifier(resName, "string",
                    context.getPackageName());
            try {
                localizedName = context.getResources().getString(resId);
                Log.d(TAG, "Replaced apn name with localized name");
            } catch (NotFoundException e) {
                Log.e(TAG, "Got execption while getting the localized apn name.", e);
            }
        }
        return localizedName;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (!mUnavailable) {
            menu.add(0, MENU_NEW, 0,
                    getResources().getString(R.string.menu_new))
                    .setIcon(android.R.drawable.ic_menu_add)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            menu.add(0, MENU_RESTORE, 0,
                    getResources().getString(R.string.menu_restore))
                    .setIcon(android.R.drawable.ic_menu_upload);
        }

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_NEW:
            addNewApn();
            return true;

        case MENU_RESTORE:
            restoreDefaultApn();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void addNewApn() {
        Intent intent = new Intent(Intent.ACTION_INSERT, getUri(Telephony.Carriers.CONTENT_URI));
        intent.putExtra(OPERATOR_NUMERIC_EXTRA, getOperatorNumeric()[0]);
        startActivity(intent);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        int pos = Integer.parseInt(preference.getKey());
        Uri url = ContentUris.withAppendedId(getUri(Telephony.Carriers.CONTENT_URI), pos);
        startActivity(new Intent(Intent.ACTION_EDIT, url));
        return true;
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        Log.d(TAG, "onPreferenceChange(): Preference - " + preference
                + ", newValue - " + newValue + ", newValue type - "
                + newValue.getClass());
        if (newValue instanceof String) {
            setSelectedApnKey((String) newValue);
        }

        return true;
    }

    private void setSelectedApnKey(String key) {
        mSelectedKey = key;
        ContentResolver resolver = getContentResolver();

        ContentValues values = new ContentValues();
        values.put(APN_ID, mSelectedKey);
        resolver.update(getUri(PREFERAPN_URI), values, null, null);
    }

    private String getSelectedApnKey() {
        String key = null;

        Cursor cursor = getContentResolver().query(getUri(PREFERAPN_URI), new String[] {"_id"},
                null, null, Telephony.Carriers.DEFAULT_SORT_ORDER);
        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            key = cursor.getString(ID_INDEX);
        }
        cursor.close();
        return key;
    }

    private boolean restoreDefaultApn() {
        showDialog(DIALOG_RESTORE_DEFAULTAPN);
        mRestoreDefaultApnMode = true;

        if (mRestoreApnUiHandler == null) {
            mRestoreApnUiHandler = new RestoreApnUiHandler();
        }

        if (mRestoreApnProcessHandler == null ||
            mRestoreDefaultApnThread == null) {
            mRestoreDefaultApnThread = new HandlerThread(
                    "Restore default APN Handler: Process Thread");
            mRestoreDefaultApnThread.start();
            mRestoreApnProcessHandler = new RestoreApnProcessHandler(
                    mRestoreDefaultApnThread.getLooper(), mRestoreApnUiHandler);
        }

        mRestoreApnProcessHandler
                .sendEmptyMessage(EVENT_RESTORE_DEFAULTAPN_START);
        return true;
    }

    private class RestoreApnUiHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_RESTORE_DEFAULTAPN_COMPLETE:
                    Activity activity = getActivity();
                    if (activity == null) {
                        mRestoreDefaultApnMode = false;
                        return;
                    }
                    Bundle result = Utils.call(getActivity(), "update_apn_by_area", null);
                    if (result == null || result.getBoolean("result", false)) {
                        Log.d(TAG, "failed to update APN");
                    }

                    fillList();
                    getPreferenceScreen().setEnabled(true);
                    mRestoreDefaultApnMode = false;
                    removeDialog(DIALOG_RESTORE_DEFAULTAPN);
                    Toast.makeText(
                        activity,
                        getResources().getString(
                                R.string.restore_default_apn_completed),
                        Toast.LENGTH_LONG).show();
                    break;
            }
        }
    }

    private class RestoreApnProcessHandler extends Handler {
        private Handler mRestoreApnUiHandler;

        public RestoreApnProcessHandler(Looper looper, Handler restoreApnUiHandler) {
            super(looper);
            this.mRestoreApnUiHandler = restoreApnUiHandler;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_RESTORE_DEFAULTAPN_START:
                    ContentResolver resolver = getContentResolver();
                    resolver.delete(getUri(DEFAULTAPN_URI), null, null);
                    mRestoreApnUiHandler
                        .sendEmptyMessage(EVENT_RESTORE_DEFAULTAPN_COMPLETE);
                    break;
            }
        }
    }

    @Override
    public Dialog onCreateDialog(int id) {
        if (id == DIALOG_RESTORE_DEFAULTAPN) {
            ProgressDialog dialog = new ProgressDialog(getActivity());
            dialog.setMessage(getResources().getString(R.string.restore_default_apn));
            dialog.setCancelable(false);
            return dialog;
        }
        return null;
    }

    private String getOperatorNumericSelection() {
        String[] mccmncs = getOperatorNumeric();
        String where;
        where = (mccmncs[0] != null) ? "numeric=\"" + mccmncs[0] + "\"" : "";
        where += (mccmncs[1] != null) ? " or numeric=\"" + mccmncs[1] + "\"" : "";
        Log.d(TAG, "getOperatorNumericSelection: " + where);
        return where;
    }

    private String[] getOperatorNumeric() {
        ArrayList<String> result = new ArrayList<String>();
        if (mUseNvOperatorForEhrpd) {
            String mccMncForEhrpd = SystemProperties.get("ro.cdma.home.operator.numeric", null);
            if (mccMncForEhrpd != null && mccMncForEhrpd.length() > 0) {
                result.add(mccMncForEhrpd);
            }
        }

        String iccOperatorNumeric = TelephonyManager.getDefault().getIccOperatorNumeric(mSubId);
        Log.d(TAG, "getOperatorNumeric: sub= " + mSubId + " mcc-mnc= " + iccOperatorNumeric);
        if (iccOperatorNumeric != null && iccOperatorNumeric.length() > 0) {
            result.add(iccOperatorNumeric);
        }
        return result.toArray(new String[2]);
    }

}
