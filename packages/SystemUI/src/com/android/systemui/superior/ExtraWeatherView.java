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

public class ExtraWeatherView extends FrameLayout implements OmniJawsClient.OmniJawsObserver {

    static final String TAG = "SystemUI:ExtraWeatherView";

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

    private boolean mShowExtraInfo;
    private boolean mShowWeatherMaster;

    private Context mContext;

    public ExtraWeatherView(Context context) {
        this(context, null);
    }

    public ExtraWeatherView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ExtraWeatherView(Context context, AttributeSet attrs, int defStyle) {
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
        setViewsVisibility(View.GONE,
            mWeatherDaily,
            mForecastInfoImage,
            mWeatherDailySummary,
            mWeatherDailyCondition,
            mWindInfoImage,
            mPinwheelImage,
            mHumidityInfoImage,
            mWeatherWindSpeedInfo,
            mWeatherWindDirectionInfo,
            mWeatherHumidityInfo
        );
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
            if (mWeatherInfo == null) {
                setVisibilityGone();
                return;
            }
            int extraInfoVisibility = mShowExtraInfo ? View.VISIBLE : View.GONE;

            // Load drawables once
            Drawable windImage = mWeatherClient.getResOmni("ic_wind_symbol");
            Drawable pinWheelImage = mWeatherClient.getResOmni("ic_wind_direction_symbol");
            Drawable humidityImage = mWeatherClient.getResOmni("ic_humidity_symbol");

            // Set images and visibility
            mWindInfoImage.setImageDrawable(windImage);
            mPinwheelImage.setImageDrawable(pinWheelImage);
            mHumidityInfoImage.setImageDrawable(humidityImage);
            setViewsVisibility(extraInfoVisibility, mWindInfoImage, mPinwheelImage, mHumidityInfoImage);

            // Set text and visibility
            mWeatherWindSpeedInfo.setText(mWeatherInfo.windSpeed + " " + mWeatherInfo.windUnits);
            mWeatherWindDirectionInfo.setText(mWeatherInfo.pinWheel);
            mWeatherHumidityInfo.setText(mWeatherInfo.humidity);
            setViewsVisibility(extraInfoVisibility, mWeatherWindSpeedInfo, mWeatherWindDirectionInfo, mWeatherHumidityInfo);

            // Daily forecast (force show if master toggle is on)
            if (isCurrentHourInRange(6,10)) {
                mDayForecast = mWeatherInfo.forecasts.get(0);
                if (mDayForecast != null) {
                    mWeatherDaily.setText("Today · " + mDayForecast.high + "\u00B0" + "/" + mDayForecast.low + "\u00B0");
                    Drawable forecastImage = mWeatherClient.getWeatherConditionImage(mDayForecast.conditionCode);
                    mForecastInfoImage.setImageDrawable(forecastImage);
                    String dailyCondition = mDayForecast.condition;
                    if (dailyCondition != null && !dailyCondition.isEmpty()) {
                        dailyCondition = capitalizeWords(dailyCondition);
                    }
                    mWeatherDailyCondition.setText(" · " + dailyCondition);
                    String dailySummary = mDayForecast.conditionSummary;
                    mWeatherDailySummary.setText(dailySummary);
                    setViewsVisibility(View.VISIBLE, mWeatherDaily, mForecastInfoImage, mWeatherDailyCondition, mWeatherDailySummary);
                }
            } else {
                setViewsVisibility(View.GONE, mWeatherDaily, mForecastInfoImage, mWeatherDailySummary, mWeatherDailyCondition);
            }
        } catch(Exception e) {
            // Do nothing
        }
    }

    private String capitalizeWords(String input) {
        if (input == null || input.isEmpty()) return input;
        String[] words = input.split("\\s+");
        StringBuilder capitalized = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                capitalized.append(Character.toUpperCase(word.charAt(0)))
                           .append(word.substring(1).toLowerCase())
                           .append(" ");
            }
        }
        return capitalized.toString().trim();
    }

    private boolean isCurrentHourInRange(int startHour, int endHour) {
        Calendar calendar = Calendar.getInstance();
        int currentHour = calendar.get(Calendar.HOUR_OF_DAY);
        return currentHour >= startHour && currentHour < endHour;
    }

    private void setViewsVisibility(int visibility, View... views) {
        if (views == null) return;
        for (View view : views) {
            if (view != null) {
                view.setVisibility(visibility);
            }
        }
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            mContext.getContentResolver().registerContentObserver(Settings.System.getUriFor(
                Settings.System.LOCKSCREEN_WEATHER_ENABLED), false, this,
                UserHandle.USER_ALL);
            mContext.getContentResolver().registerContentObserver(Settings.System.getUriFor(
                    Settings.System.LOCKSCREEN_WEATHER_EXTRA_INFO), false, this,
                    UserHandle.USER_ALL);
            updateWeatherSettings();
        }

        void unobserve() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }

    void updateWeatherSettings() {
        mShowWeatherMaster = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.LOCKSCREEN_WEATHER_ENABLED,
                0, UserHandle.USER_CURRENT) != 0;
        mShowExtraInfo = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.LOCKSCREEN_WEATHER_EXTRA_INFO,
                1, UserHandle.USER_CURRENT) != 0;
        mWeatherInfo = mWeatherClient.getWeatherInfo();
        if (mWeatherInfo != null) {
            int extraInfoVisibility = mShowExtraInfo && mShowWeatherMaster ? View.VISIBLE : View.GONE;
            int forecastInfoVisibility = mShowWeatherMaster && isCurrentHourInRange(6,10) ? View.VISIBLE : View.GONE;

            setViewsVisibility(extraInfoVisibility,
                mWindInfoImage,
                mWeatherWindSpeedInfo,
                mPinwheelImage,
                mWeatherWindDirectionInfo,
                mHumidityInfoImage,
                mWeatherHumidityInfo
            );
            setViewsVisibility(forecastInfoVisibility,
                mWeatherDaily,
                mForecastInfoImage,
                mWeatherDailySummary,
                mWeatherDailyCondition
            );
        }
    }

        @Override
        public void onChange(boolean selfChange) {
            updateWeatherSettings();
        }
    }
}
