/*
* Copyright (C) 2021 The OmniROM Project
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*
*/
package com.android.internal.util.superior;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

public class OmniJawsClient {
    private static final String TAG = "OmniJawsClient";
    private static final boolean DEBUG = false;
    public static final String SERVICE_PACKAGE = "org.omnirom.omnijaws";
    public static final Uri WEATHER_URI
            = Uri.parse("content://org.omnirom.omnijaws.provider/weather");
    public static final Uri SETTINGS_URI
            = Uri.parse("content://org.omnirom.omnijaws.provider/settings");
    public static final Uri CONTROL_URI
            = Uri.parse("content://org.omnirom.omnijaws.provider/control");

    private static final String ICON_PACKAGE_DEFAULT = "org.omnirom.omnijaws";
    private static final String ICON_PREFIX_DEFAULT = "google_new_light";
    private static final String EXTRA_ERROR = "error";
    public static final int EXTRA_ERROR_NETWORK = 0;
    public static final int EXTRA_ERROR_LOCATION = 1;
    public static final int EXTRA_ERROR_DISABLED = 2;
    
    private boolean mWeatherReceiverRegistered;

    public static final String[] WEATHER_PROJECTION = new String[]{
            "city",
            "wind_speed",
            "wind_direction",
            "condition_code",
            "temperature",
            "humidity",
            "condition",
            "forecast_low",
            "forecast_high",
            "forecast_condition",
            "forecast_condition_code",
            "time_stamp",
            "forecast_date",
            "pin_wheel",
            "forecast_summary"
    };

    public static final String[] SETTINGS_PROJECTION = new String[] {
            "enabled",
            "units",
            "provider",
            "setup",
            "icon_pack"
    };

    private static final String WEATHER_UPDATE = SERVICE_PACKAGE + ".WEATHER_UPDATE";
    private static final String WEATHER_ERROR = SERVICE_PACKAGE + ".WEATHER_ERROR";

    private static final DecimalFormat sNoDigitsFormat = new DecimalFormat("0");

    public static class WeatherInfo {
        public String city;
        public String windSpeed;
        public String windDirection;
        public int conditionCode;
        public String temp;
        public String humidity;
        public String condition;
        public Long timeStamp;
        public List<DayForecast> forecasts;
        public String tempUnits;
        public String windUnits;
        public String provider;
        public String pinWheel;
        public String iconPack;

        public String toString() {
            return city + ":" + new Date(timeStamp) + ": " + windSpeed + ":" + windDirection + ":" +conditionCode + ":" + temp + ":" + humidity + ":" + condition + ":" + tempUnits + ":" + windUnits + ": " + forecasts + ": " + iconPack;
        }

        public String getLastUpdateTime() {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            return sdf.format(new Date(timeStamp));
        }
    }

    public static class DayForecast {
        public String low;
        public String high;
        public int conditionCode;
        public String condition;
        public String date;
        public String conditionSummary;

        public String toString() {
            return "[" + low + ":" + high + ":" +conditionCode + ":" + condition + ":" + date + ":" + conditionSummary + "]";
        }
    }

    public static interface OmniJawsObserver {
        public void weatherUpdated();
        public void weatherError(int errorReason);
        default public void updateSettings() {};
    }

    private class WeatherUpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(final Context context, Intent intent) {
            String action = intent.getAction();
            for (OmniJawsObserver observer : mObserver) {
                if (action.equals(WEATHER_UPDATE)) {
                    observer.weatherUpdated();
                } else if (action.equals(WEATHER_ERROR)) {
                    int errorReason = intent.getIntExtra(EXTRA_ERROR, 0);
                    observer.weatherError(errorReason);
                }
            }
        }
    }

    private Context mContext;
    private WeatherInfo mCachedInfo;
    private Resources mRes;
    private String mPackageName;
    private String mIconPrefix;
    private String mSettingIconPackage;
    private boolean mMetric;
    private List<OmniJawsObserver> mObserver;
    private WeatherUpdateReceiver mReceiver;

    public OmniJawsClient(Context context) {
        mContext = context;
        mObserver = new ArrayList<OmniJawsObserver>();
    }

    public Intent getSettingsIntent() {
        if (isOmniJawsServiceInstalled()) {
            Intent settings = new Intent(Intent.ACTION_MAIN)
                    .setClassName(SERVICE_PACKAGE, SERVICE_PACKAGE + ".SettingsActivity");
            return settings;
        }
        return null;
    }

    public Intent getWeatherActivityIntent() {
        if (isOmniJawsServiceInstalled()) {
            Intent settings = new Intent(Intent.ACTION_MAIN)
                    .setClassName(SERVICE_PACKAGE, SERVICE_PACKAGE + ".WeatherActivity");
            return settings;
        }
        return null;
    }

    public WeatherInfo getWeatherInfo() {
        return mCachedInfo;
    }

    private static String getFormattedValue(float value) {
        if (Float.isNaN(value)) {
            return "-";
        }
        String formatted = sNoDigitsFormat.format(value);
        if (formatted.equals("-0")) {
            formatted = "0";
        }
        return formatted;
    }

    public void queryWeather() {
        if (!isOmniJawsEnabled()) {
            Log.w(TAG, "queryWeather while disabled");
            mCachedInfo = null;
            return;
        }
        try {
            mCachedInfo = null;
            Cursor c = mContext.getContentResolver().query(WEATHER_URI, WEATHER_PROJECTION,
                    null, null, null);
            if (c != null) {
                try {
                    int count = c.getCount();
                    if (count > 0) {
                        mCachedInfo = new WeatherInfo();
                        List<DayForecast> forecastList = new ArrayList<DayForecast>();
                        int i = 0;
                        for (i = 0; i < count; i++) {
                            c.moveToPosition(i);
                            if (i == 0) {
                                mCachedInfo.city = c.getString(0);
                                mCachedInfo.windSpeed = getFormattedValue(c.getFloat(1));
                                mCachedInfo.windDirection = String.valueOf(c.getInt(2)) + "\u00b0";
                                mCachedInfo.conditionCode = c.getInt(3);
                                mCachedInfo.temp = getFormattedValue(c.getFloat(4));
                                mCachedInfo.humidity = c.getString(5);
                                mCachedInfo.condition = c.getString(6);
                                mCachedInfo.timeStamp = Long.valueOf(c.getString(11));
                                mCachedInfo.pinWheel = c.getString(13);
                            } else {
                                DayForecast day = new DayForecast();
                                day.low = getFormattedValue(c.getFloat(7));
                                day.high = getFormattedValue(c.getFloat(8));
                                day.condition = c.getString(9);
                                day.conditionCode = c.getInt(10);
                                day.date = c.getString(12);
                                day.conditionSummary = c.getString(14);
                                forecastList.add(day);
                            }
                        }
                        mCachedInfo.forecasts = forecastList;
                    }
                } finally {
                    c.close();
                }
            }
            c = mContext.getContentResolver().query(SETTINGS_URI, SETTINGS_PROJECTION,
                        null, null, null);
            if (c != null) {
                try {
                    int count = c.getCount();
                    if (count == 1) {
                        c.moveToPosition(0);
                        mMetric = c.getInt(1) == 0;
                        if (mCachedInfo != null) {
                            mCachedInfo.tempUnits = getTemperatureUnit();
                            mCachedInfo.windUnits = getWindUnit();
                            mCachedInfo.provider = c.getString(2);
                            mCachedInfo.iconPack = c.getString(4);
                        }
                    }
                } finally {
                    c.close();
                }
            }

            if (DEBUG) Log.d(TAG, "queryWeather " + mCachedInfo);
            updateSettings();
        } catch (Exception e) {
            Log.e(TAG, "queryWeather", e);
        }
    }

    private void loadDefaultIconsPackage() {
        mPackageName = ICON_PACKAGE_DEFAULT;
        mIconPrefix = ICON_PREFIX_DEFAULT;
        mSettingIconPackage = mPackageName + "." + mIconPrefix;
        if (DEBUG) Log.d(TAG, "Load default icon pack " + mSettingIconPackage + " " + mPackageName + " " + mIconPrefix);
        try {
            PackageManager packageManager = mContext.getPackageManager();
            mRes = packageManager.getResourcesForApplication(mPackageName);
        } catch (Exception e) {
            mRes = null;
        }
        if (mRes == null) {
            Log.w(TAG, "No default package found");
        }
    }

    private Drawable getDefaultConditionImage() {
        String packageName = ICON_PACKAGE_DEFAULT;
        String iconPrefix = ICON_PREFIX_DEFAULT;

        try {
            PackageManager packageManager = mContext.getPackageManager();
            Resources res = packageManager.getResourcesForApplication(packageName);
            if (res != null) {
                int resId = res.getIdentifier(iconPrefix + "_na", "drawable", packageName);
                Drawable d = res.getDrawable(resId);
                if (d != null) {
                    return d;
                }
            }
        } catch (Exception e) {
        }
        // absolute absolute fallback
        Log.w(TAG, "No default package found");
        return new ColorDrawable(Color.RED);
    }

    private void loadCustomIconPackage() {
        if (DEBUG) Log.d(TAG, "Load custom icon pack " + mSettingIconPackage);
        int idx = mSettingIconPackage.lastIndexOf(".");
        mPackageName = mSettingIconPackage.substring(0, idx);
        mIconPrefix = mSettingIconPackage.substring(idx + 1);
        if (DEBUG) Log.d(TAG, "Load custom icon pack " + mPackageName + " " + mIconPrefix);
        try {
            PackageManager packageManager = mContext.getPackageManager();
            mRes = packageManager.getResourcesForApplication(mPackageName);
        } catch (Exception e) {
            mRes = null;
        }
        if (mRes == null) {
            Log.w(TAG, "Icon pack loading failed - loading default");
            loadDefaultIconsPackage();
        }
    }

    public Drawable getWeatherConditionImage(int conditionCode) {
        try {
            int resId = mRes.getIdentifier(mIconPrefix + "_" + conditionCode, "drawable", mPackageName);
            Drawable d = mRes.getDrawable(resId);
            if (d != null) {
                return d;
            }
            Log.w(TAG, "Failed to get condition image for " + conditionCode + " use default");
            resId = mRes.getIdentifier(mIconPrefix + "_na", "drawable", mPackageName);
            d = mRes.getDrawable(resId);
            if (d != null) {
                return d;
            }
        } catch(Exception e) {
            Log.e(TAG, "getWeatherConditionImage", e);
        }
        Log.w(TAG, "Failed to get condition image for " + conditionCode);
        return getDefaultConditionImage();
    }

    public Drawable getResOmni(String iconOmni) {
        try {
            int resId = mRes.getIdentifier(iconOmni, "drawable", mPackageName);
            Drawable d = mRes.getDrawable(resId);
            if (d != null) {
                return d;
            }
        } catch(Exception e) {
            Log.e(TAG, "getResOmni", e);
        }
        Log.w(TAG, "Failed to get resource image for" + iconOmni);
        // absolute absolute fallback
        Log.w(TAG, "No default package found");
        return new ColorDrawable(Color.RED);
    }

    public boolean isOmniJawsServiceInstalled() {
        return isAvailableApp(SERVICE_PACKAGE);
    }

    public boolean isOmniJawsEnabled() {
        if (!isOmniJawsServiceInstalled()) {
            return false;
        }
        boolean enabled = false;
        try {
            final Cursor c = mContext.getContentResolver().query(SETTINGS_URI, SETTINGS_PROJECTION,
                    null, null, null);
            if (c != null) {
                try {
                    int count = c.getCount();
                    if (count == 1) {
                        c.moveToPosition(0);
                        enabled = c.getInt(0) == 1;
                    }
                } finally {
                    c.close();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "isOmniJawsEnabled", e);
        }
        return enabled;
    }

    private String getTemperatureUnit() {
        return "\u00b0" + (mMetric ? "C" : "F");
    }

    private String getWindUnit() {
        return mMetric ? "km/h":"mph";
    }

    private void updateSettings() {
        final String iconPack = mCachedInfo != null ? mCachedInfo.iconPack : null;
        if (iconPack == null || TextUtils.isEmpty(iconPack)) {
            loadDefaultIconsPackage();
        } else if (mSettingIconPackage == null || !iconPack.equals(mSettingIconPackage)) {
            mSettingIconPackage = iconPack;
            loadCustomIconPackage();
        }
    }

    private boolean isAvailableApp(String packageName) {
        final PackageManager pm = mContext.getPackageManager();
        try {
            pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
            int enabled = pm.getApplicationEnabledSetting(packageName);
            return enabled != PackageManager.COMPONENT_ENABLED_STATE_DISABLED &&
                    enabled != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;
        } catch (NameNotFoundException e) {
            return false;
        }
    }

    public void addObserver(OmniJawsObserver observer) {
        if (mObserver.contains(observer)) {
            removeObserver(observer);
        }
        mObserver.add(observer);
        registerReceiverIfNeeded();
    }

    public void removeObserver(OmniJawsObserver observer) {
        if (mObserver.contains(observer)) {
            mObserver.remove(observer);
            if (mObserver.isEmpty()) {
                unregisterReceiver();
            }
        }
    }

    private void registerReceiverIfNeeded() {
        if (mObserver.size() == 1 && !mWeatherReceiverRegistered) {
            if (mReceiver != null) {
                try {
                    mContext.unregisterReceiver(mReceiver);
                } catch (Exception e) {
                }
            }
            mReceiver = new WeatherUpdateReceiver();
            IntentFilter filter = new IntentFilter();
            filter.addAction(WEATHER_UPDATE);
            filter.addAction(WEATHER_ERROR);
            if (DEBUG) Log.d(TAG, "registerReceiver");
            mContext.registerReceiver(mReceiver, filter, Context.RECEIVER_EXPORTED);
            mWeatherReceiverRegistered = true;
        }
    }
    
    private void unregisterReceiver() {
        if (!mWeatherReceiverRegistered) return;
        mContext.unregisterReceiver(mReceiver);
        mWeatherReceiverRegistered = false;
    }
}
