/*
 * Copyright (C) 2023-2024 crDroid Android Project
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
package com.android.systemui.superior;

import android.content.Context;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.util.Log;

import java.util.Calendar;

import com.android.internal.util.superior.OmniJawsClient;
import com.android.systemui.res.R;

public class CurrentWeatherView extends FrameLayout implements OmniJawsClient.OmniJawsObserver {

    static final String TAG = "SystemUI:CurrentWeatherView";

    private ImageView mWindInfoImage;
    private ImageView mPinwheelImage;
    private ImageView mHumidityInfoImage;
    private ImageView mForecastInfoImage;
    private OmniJawsClient mWeatherClient;
    private OmniJawsClient.WeatherInfo mWeatherInfo;
    private OmniJawsClient.DayForecast mDayForecast;
    private TextView mWeatherWindSpeedInfo;
    private TextView mWeatherWindDirectionInfo;
    private TextView mWeatherHumidityInfo;
    private TextView mWeatherDaily;
    private TextView mWeatherDailySummary;
    private TextView mWeatherDailyCondition;

    private SettingsObserver mSettingsObserver;

    private boolean mShowWeatherText;
    private boolean mShowWindInfo;
    private boolean mShowHumidityInfo;

    private Context mContext;

    public CurrentWeatherView(Context context) {
        this(context, null);
    }

    public CurrentWeatherView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CurrentWeatherView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        if (mWeatherClient == null) {
            mWeatherClient = new OmniJawsClient(context);
        }
    }

    public void enableUpdates() {
        if (mWeatherClient != null) {
            mWeatherClient.addObserver(this);
            queryAndUpdateWeather();
        }
    }

    public void disableUpdates() {
        if (mWeatherClient != null) {
            mWeatherClient.removeObserver(this);
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mWindInfoImage  = (ImageView) findViewById(R.id.wind_info_image);
        mPinwheelImage  = (ImageView) findViewById(R.id.pinwheel_image);
        mHumidityInfoImage = (ImageView) findViewById(R.id.humidity_info_image);
        mForecastInfoImage = (ImageView) findViewById(R.id.forecast_info_image);
        mWeatherWindSpeedInfo = (TextView) findViewById(R.id.weather_wind_speed_info);
        mWeatherWindDirectionInfo = (TextView) findViewById(R.id.weather_wind_direction_info);
        mWeatherHumidityInfo = (TextView) findViewById(R.id.weather_humidity_info);
        mWeatherDaily = (TextView) findViewById(R.id.weather_daily);
        mWeatherDailySummary = (TextView) findViewById(R.id.weather_daily_summary);
        mWeatherDailyCondition = (TextView) findViewById(R.id.weather_daily_condition);
        if (mSettingsObserver == null) {
            mSettingsObserver = new SettingsObserver(new Handler());
            mSettingsObserver.observe();
        }
    }

    private void setVisibilityGone() {
        mWeatherDaily.setVisibility(View.GONE);
        mForecastInfoImage.setVisibility(View.GONE);
        mWeatherDailySummary.setVisibility(View.GONE);
        mWeatherDailyCondition.setVisibility(View.GONE);
        mWindInfoImage.setVisibility(View.GONE);
        mPinwheelImage.setVisibility(View.GONE);
        mHumidityInfoImage.setVisibility(View.GONE);
        mWeatherWindSpeedInfo.setVisibility(View.GONE);
        mWeatherWindDirectionInfo.setVisibility(View.GONE);
        mWeatherHumidityInfo.setVisibility(View.GONE);
    }

    @Override
    public void weatherError(int errorReason) {
        // since this is shown in ambient and lock screen
        // it would look bad to show every error since the
        // screen-on revovery of the service had no chance
        // to run fast enough
        // so only show the disabled state
        if (errorReason == OmniJawsClient.EXTRA_ERROR_DISABLED) {
            mWeatherInfo = null;
            mDayForecast = null;
            setVisibilityGone();
        }
    }

    @Override
    public void weatherUpdated() {
        queryAndUpdateWeather();
    }

    @Override
    public void updateSettings() {
        queryAndUpdateWeather();
    }

    private void queryAndUpdateWeather() {
        try {
            if (mWeatherClient == null || !mWeatherClient.isOmniJawsEnabled()) {
                setVisibilityGone();
                return;
            }
            mWeatherClient.queryWeather();
            mWeatherInfo = mWeatherClient.getWeatherInfo();
            if (mWeatherInfo != null) {
                Drawable windImage = mWeatherClient.getResOmni("ic_wind_symbol");
                mWindInfoImage.setImageDrawable(windImage);
                mWindInfoImage.setVisibility(mShowWindInfo ? View.VISIBLE : View.GONE);
                Drawable pinWheel = mWeatherClient.getResOmni("ic_wind_direction_symbol");
                mPinwheelImage.setImageDrawable(pinWheel);
                mPinwheelImage.setVisibility(mShowWindInfo ? View.VISIBLE : View.GONE);
                Drawable humidityImage = mWeatherClient.getResOmni("ic_humidity_symbol");
                mHumidityInfoImage.setImageDrawable(humidityImage);
                mHumidityInfoImage.setVisibility(mShowHumidityInfo ? View.VISIBLE : View.GONE);
                mWeatherWindSpeedInfo.setText(mWeatherInfo.windSpeed + " " + mWeatherInfo.windUnits);
                mWeatherWindSpeedInfo.setVisibility(mShowWindInfo ? View.VISIBLE : View.GONE);
                mWeatherWindDirectionInfo.setText(mWeatherInfo.pinWheel);
                mWeatherWindDirectionInfo.setVisibility(mShowWindInfo ? View.VISIBLE : View.GONE);
                mWeatherHumidityInfo.setText(mWeatherInfo.humidity);
                mWeatherHumidityInfo.setVisibility(mShowHumidityInfo ? View.VISIBLE : View.GONE);
                Calendar calendar = Calendar.getInstance();
                int currentHour = calendar.get(Calendar.HOUR_OF_DAY);
                if (currentHour > 5 && currentHour < 10) {
                    mDayForecast = mWeatherInfo.forecasts.get(0);
                    if (mDayForecast != null) {
                        mWeatherDaily.setText("Today · " + mDayForecast.high + "\u00B0" + "/" + mDayForecast.low + "\u00B0");
                        mWeatherDaily.setVisibility(View.VISIBLE);
                        Drawable forecastImage = mWeatherClient.getWeatherConditionImage(mDayForecast.conditionCode);
                        mForecastInfoImage.setImageDrawable(forecastImage);
                        mForecastInfoImage.setVisibility(View.VISIBLE);
                        String dailyCondition = mDayForecast.condition;
                        if (dailyCondition != null && !dailyCondition.isEmpty()) {
                            String[] words = dailyCondition.split(" ");
                            StringBuilder formattedBuilder = new StringBuilder();
                            for (String word : words) {
                                // Capitalize the first letter and append the rest of the word
                                formattedBuilder.append(Character.toUpperCase(word.charAt(0)))
                                .append(word.substring(1))
                                .append(" ");
                            }
                            // Remove the trailing space
                            dailyCondition = formattedBuilder.toString().trim();
                        }
                        mWeatherDailyCondition.setText(" · " + dailyCondition);
                        mWeatherDailyCondition.setVisibility(View.VISIBLE);
                        String dailySummary = mDayForecast.conditionSummary;
                        mWeatherDailySummary.setText(dailySummary);
                        mWeatherDailySummary.setVisibility(View.VISIBLE);
                    }
                } else {
                    mWeatherDaily.setVisibility(View.GONE);
                    mForecastInfoImage.setVisibility(View.GONE);
                    mWeatherDailySummary.setVisibility(View.GONE);
                    mWeatherDailyCondition.setVisibility(View.GONE);
                }
            }
        } catch(Exception e) {
            // Do nothing
        }
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            mContext.getContentResolver().registerContentObserver(Settings.System.getUriFor(
                    Settings.System.LOCKSCREEN_WEATHER_TEXT), false, this,
                    UserHandle.USER_ALL);
            mContext.getContentResolver().registerContentObserver(Settings.System.getUriFor(
                    Settings.System.LOCKSCREEN_WEATHER_WIND_INFO), false, this,
                    UserHandle.USER_ALL);
            mContext.getContentResolver().registerContentObserver(Settings.System.getUriFor(
                    Settings.System.LOCKSCREEN_WEATHER_HUMIDITY_INFO), false, this,
                    UserHandle.USER_ALL);
            updateWeatherSettings();
        }

        void unobserve() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }

        void updateWeatherSettings() {
            mShowWeatherText = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.LOCKSCREEN_WEATHER_TEXT,
                    1, UserHandle.USER_CURRENT) != 0;
            mShowWindInfo = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.LOCKSCREEN_WEATHER_WIND_INFO,
                    1, UserHandle.USER_CURRENT) != 0;
            mShowHumidityInfo = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.LOCKSCREEN_WEATHER_HUMIDITY_INFO,
                    1, UserHandle.USER_CURRENT) != 0;
            mWindInfoImage.setVisibility(mShowWindInfo ? View.VISIBLE : View.GONE);
            mWeatherWindSpeedInfo.setVisibility(mShowWindInfo ? View.VISIBLE : View.GONE);
            mPinwheelImage.setVisibility(mShowWindInfo ? View.VISIBLE : View.GONE);
            mWeatherWindDirectionInfo.setVisibility(mShowWindInfo ? View.VISIBLE : View.GONE);
            mHumidityInfoImage.setVisibility(mShowHumidityInfo ? View.VISIBLE : View.GONE);
            mWeatherHumidityInfo.setVisibility(mShowHumidityInfo ? View.VISIBLE : View.GONE);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateWeatherSettings();
        }
    }
}
