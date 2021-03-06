/*
 * Project:  Simple Collector
 * Purpose:  Mobile application for simple data collection.
 * Author:   NikitaFeodonit, nfeodonit@yandex.com
 * ****************************************************************************
 * Copyright (c) 2017-2018 NextGIS, info@nextgis.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextgis.simple_collector;

import android.accounts.Account;
import android.accounts.AccountManagerFuture;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
import com.google.android.gms.analytics.ExceptionReporter;
import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.StandardExceptionParser;
import com.google.android.gms.analytics.Tracker;
import com.nextgis.maplib.api.ILayer;
import com.nextgis.maplib.datasource.Feature;
import com.nextgis.maplib.map.LayerGroup;
import com.nextgis.maplib.map.MapBase;
import com.nextgis.maplib.map.MapDrawable;
import com.nextgis.maplib.map.NGWVectorLayer;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.FileUtil;
import com.nextgis.maplib.util.NetworkUtil;
import com.nextgis.maplib.util.SettingsConstants;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.maplibui.fragment.NGWLoginFragment;
import com.nextgis.maplibui.fragment.NGWSettingsFragment;
import com.nextgis.maplibui.mapui.TrackLayerUI;
import com.nextgis.maplibui.util.SettingsConstantsUI;
import com.nextgis.simple_collector.activity.SettingsActivity;
import com.nextgis.simple_collector.map.WtcLayerFactory;
import com.nextgis.simple_collector.service.InitService;
import com.nextgis.simple_collector.util.AppConstants;
import com.nextgis.simple_collector.util.AppSettingsConstants;
import io.sentry.Sentry;
import io.sentry.android.AndroidSentryClientFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


/**
 * Main application class
 * The initial layers create here.
 */
public class MainApplication
        extends GISApplication
        implements NGWLoginFragment.OnAddAccountListener,
                   NGWSettingsFragment.OnDeleteAccountListener
{
    public static final String LAYER_TRACKS = "tracks";

    private Tracker mTracker;

    protected NetworkUtil mNet;

    protected OnAccountAddedListener mOnAccountAddedListener;
    protected OnAccountDeletedListener mOnAccountDeletedListener;
    protected OnReloadMapListener mOnReloadMapListener;

    protected boolean mIsAccountCreated = false;
    protected boolean mIsAccountDeleted = false;
    protected boolean mIsMapReloaded    = false;

    protected NGWVectorLayer mZmuDataLayer;

    @Override
    public void onCreate()
    {
        // See sentry docs: https://docs.sentry.io/clients/java/
        Sentry.init(
                BuildConfig.SENTRY_DSN, new AndroidSentryClientFactory(getApplicationContext()));
        Sentry.capture("Simple Collector Sentry is init.");

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        GoogleAnalytics.getInstance(this)
                .setAppOptOut(
                        !mSharedPreferences.getBoolean(AppSettingsConstants.KEY_PREF_GA, true));
        GoogleAnalytics.getInstance(this).setDryRun(Constants.DEBUG_MODE);
        getTracker();
        setExceptionHandler();

        super.onCreate();

        mNet = new NetworkUtil(this);

        BroadcastReceiver initSyncStatusReceiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(
                    Context context,
                    Intent intent)
            {
                if (isRanAsService()) {
                    return;
                }

                int state =
                        intent.getIntExtra(AppConstants.KEY_STATE, AppConstants.STEP_STATE_WAIT);

                switch (state) {
                    case AppConstants.STEP_STATE_ERROR:
                    case AppConstants.STEP_STATE_CANCEL: {
                        try {
                            Thread.sleep(4000);
                        } catch (InterruptedException e) {
                            if (Constants.DEBUG_MODE) {
                                e.printStackTrace();
                                Sentry.capture(e);
                            }
                        }
                        cancelAccountCreation();
                        break;
                    }
                    case AppConstants.STEP_STATE_FINISH:
                        reloadMap();
                        break;
                }
            }
        };

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(AppConstants.INIT_SYNC_BROADCAST_MESSAGE);
        registerReceiver(initSyncStatusReceiver, intentFilter);
    }

    public void cancelAccountCreation()
    {
        Account account = getAccount();

        if (null != account) {
            ContentResolver.removePeriodicSync(account, getAuthority(), Bundle.EMPTY);
            ContentResolver.setSyncAutomatically(account, getAuthority(), false);
            ContentResolver.cancelSync(account, getAuthority());

            AccountManagerFuture<Boolean> future = removeAccount(account);

//            while (!future.isDone()) {
//                // wait until the removing is complete
//                try {
//                    Thread.sleep(100);
//                } catch (InterruptedException e) {
//                    if (Constants.DEBUG_MODE) {
//                        e.printStackTrace();
//                        Sentry.capture(e);
//                    }
//                }
//            }

            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                if (Constants.DEBUG_MODE) {
                    e.printStackTrace();
                    Sentry.capture(e);
                }
            }
        }

        //delete map
        MapBase map = getMap();
        map.delete();
        FileUtil.deleteRecursive(map.getPath());
        mMap = null;

        reloadMap();
    }

    private void setExceptionHandler()
    {
        ExceptionReporter handler =
                new ExceptionReporter(getTracker(), Thread.getDefaultUncaughtExceptionHandler(),
                        this);
        StandardExceptionParser exceptionParser =
                new StandardExceptionParser(getApplicationContext(), null)
                {
                    @Override
                    public String getDescription(
                            String threadName,
                            Throwable t)
                    {
                        return "{" + threadName + "} " + Log.getStackTraceString(t);
                    }
                };

        handler.setExceptionParser(exceptionParser);
        Thread.setDefaultUncaughtExceptionHandler(handler);
    }

    public synchronized Tracker getTracker()
    {
        if (mTracker == null) {
            mTracker = GoogleAnalytics.getInstance(this).newTracker(R.xml.app_tracker);
        }

        return mTracker;
    }

    @Override
    public void sendScreen(String name)
    {
        mTracker.setScreenName(name);
        mTracker.send(new HitBuilders.ScreenViewBuilder().build());
    }

    @Override
    public void sendEvent(
            String category,
            String action,
            String label)
    {
        HitBuilders.EventBuilder event = new HitBuilders.EventBuilder().setCategory(category)
                .setAction(action)
                .setLabel(label);

        getTracker().send(event.build());
    }

    @Override
    public String getAccountsType()
    {
        return Constants.NGW_ACCOUNT_TYPE;
    }

    @Override
    public String getAuthority()
    {
        return AppSettingsConstants.AUTHORITY;
    }

    @Override
    protected void onFirstRun()
    {
    }

    @Override
    public void showSettings(String settings)
    {
        if (TextUtils.isEmpty(settings)) {
            settings = SettingsConstantsUI.ACTION_PREFS_GENERAL;
        }

        switch (settings) {
            case SettingsConstantsUI.ACTION_PREFS_GENERAL:
            case SettingsConstantsUI.ACTION_PREFS_LOCATION:
            case SettingsConstantsUI.ACTION_PREFS_TRACKING:
                break;
            default:
                return;
        }

        Intent intent = new Intent(this, SettingsActivity.class);
        intent.setAction(settings);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public MapBase getMap()
    {
        if (null != mMap) {
            return mMap;
        }

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        File defaultPath = getExternalFilesDir(SettingsConstants.KEY_PREF_MAP);
        if (defaultPath == null) {
            defaultPath = new File(getFilesDir(), SettingsConstants.KEY_PREF_MAP);
        }

        String mapPath = mSharedPreferences.getString(SettingsConstants.KEY_PREF_MAP_PATH,
                defaultPath.getPath());
        String mapName =
                mSharedPreferences.getString(SettingsConstantsUI.KEY_PREF_MAP_NAME, "default");

        File mapFullPath = new File(mapPath, mapName + Constants.MAP_EXT);

        final Bitmap bkBitmap = getMapBackground();
        mMap = new MapDrawable(bkBitmap, this, mapFullPath, new WtcLayerFactory());
        mMap.setName(mapName);
        mMap.load();

//        checkTracksLayerExist();

        return mMap;
    }

    protected void checkTracksLayerExist()
    {
        List<ILayer> tracks = new ArrayList<>();
        LayerGroup.getLayersByType(mMap, Constants.LAYERTYPE_TRACKS, tracks);
        if (tracks.isEmpty()) {
            String trackLayerName = getString(R.string.tracks);
            TrackLayerUI trackLayer = new TrackLayerUI(getApplicationContext(),
                    mMap.createLayerStorage(LAYER_TRACKS));
            trackLayer.setName(trackLayerName);
            trackLayer.setVisible(true);
            mMap.addLayer(trackLayer);
            mMap.save();
        }
    }

    public boolean isRanAsService()
    {
        return getCurrentProcessName().matches(".*:(sc_tracks|init|sync)$");
    }

    public String getCurrentProcessName()
    {
        int pid = android.os.Process.myPid();
        ActivityManager manager = (ActivityManager) this.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningAppProcessInfo processInfo : manager.getRunningAppProcesses()) {
            if (processInfo.pid == pid) {
                return processInfo.processName;
            }
        }
        return "";
    }

    public boolean isInitServiceRunning()
    {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(
                Integer.MAX_VALUE)) {

            if (InitService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }

        return false;
    }

    public NGWVectorLayer getZmuDataLayer()
    {
        if (null != mZmuDataLayer) {
            return mZmuDataLayer;
        }

        MapBase map = getMap();
        mZmuDataLayer = (NGWVectorLayer) map.getLayerByPathName(AppConstants.KEY_LAYER_DATA);

        return mZmuDataLayer;
    }

    public void clearAllTemps()
    {
        NGWVectorLayer documentsLayer = getZmuDataLayer();

        if (null != documentsLayer) {
            documentsLayer.deleteAllTemps();
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor edit = prefs.edit();
        edit.remove(AppSettingsConstants.KEY_PREF_TEMP_FEATURE_ID);
        edit.apply();
    }

    public Feature getTempFeature()
    {
        clearAllTemps();

        NGWVectorLayer dataLayer = getZmuDataLayer();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Feature feature = null;

        long featureId =
                prefs.getLong(AppSettingsConstants.KEY_PREF_TEMP_FEATURE_ID, Constants.NOT_FOUND);

        if (Constants.NOT_FOUND != featureId) {
            feature = dataLayer.getFeatureWithAttaches(featureId);

            if (null == feature) {
                SharedPreferences.Editor edit = prefs.edit();
                edit.remove(AppSettingsConstants.KEY_PREF_TEMP_FEATURE_ID);
                edit.apply();

                featureId = (long) Constants.NOT_FOUND;
            }
        }

        if (Constants.NOT_FOUND == featureId) {
            feature = dataLayer.getNewTempFeature();

            if (null == feature) {
                return null;
            }

            SharedPreferences.Editor edit = prefs.edit();
            edit.putLong(AppSettingsConstants.KEY_PREF_TEMP_FEATURE_ID, feature.getId());
            edit.apply();
        }

        if (!dataLayer.hasFeatureTempFlag(feature.getId())) {
            return null;
        } else {
            return feature;
        }
    }

    public boolean isNetworkAvailable()
    {
        return mNet.isNetworkAvailable();
    }

    public Account getAccount()
    {
        return getAccount(getString(R.string.account_name));
    }

    public void reloadMap()
    {
        MapBase map = getMap();
        map.load();

        mIsMapReloaded = true;

        if (null != mOnReloadMapListener) {
            mIsMapReloaded = false;
            mOnReloadMapListener.onReloadMap();
        }
    }

    public boolean runSync()
    {
        if (!isNetworkAvailable()) {
            return false;
        }

        Account account = getAccount();
        if (null == account) {
            return false;
        }

        Bundle settingsBundle = new Bundle();
        settingsBundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        settingsBundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);

        ContentResolver.requestSync(account, getAuthority(), settingsBundle);
        return true;
    }

    public boolean isAccountAdded()
    {
        boolean isCreated = mIsAccountCreated;
        mIsAccountCreated = false;
        return isCreated;
    }

    public boolean isAccountDeleted()
    {
        boolean isDeleted = mIsAccountDeleted;
        mIsAccountDeleted = false;
        return isDeleted;
    }

    public boolean isMapReloaded()
    {
        boolean isReloaded = mIsMapReloaded;
        mIsMapReloaded = false;
        return isReloaded;
    }

    @Override
    public void onAddAccount(
            Account account,
            String token,
            boolean accountAdded)
    {
        if (accountAdded) {
            mIsAccountCreated = true;

            // Free any map data here, delete all layers from map.
            getMap().delete();

            if (null != account) {
                // Set sync with server.
                ContentResolver.setSyncAutomatically(account, getAuthority(), true);
                ContentResolver.addPeriodicSync(account, getAuthority(), Bundle.EMPTY,
                        com.nextgis.maplib.util.Constants.DEFAULT_SYNC_PERIOD);
            }

            // Goto step 2.
            if (null != mOnAccountAddedListener) {
                mIsAccountCreated = false;
                mOnAccountAddedListener.onAccountAdded();
            }

        } else {
            Toast.makeText(this, R.string.error_init, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDeleteAccount(Account account)
    {
        mIsAccountDeleted = true;
        getMap().load(); // Reload map without listener.

        if (null != mOnAccountDeletedListener) {
            mIsAccountDeleted = false;
            mOnAccountDeletedListener.onAccountDeleted();
        }
    }

    public void setOnAccountAddedListener(OnAccountAddedListener onAccountAddedListener)
    {
        mOnAccountAddedListener = onAccountAddedListener;
    }

    public interface OnAccountAddedListener
    {
        void onAccountAdded();
    }

    public void setOnAccountDeletedListener(OnAccountDeletedListener onAccountDeletedListener)
    {
        mOnAccountDeletedListener = onAccountDeletedListener;
    }

    public interface OnAccountDeletedListener
    {
        void onAccountDeleted();
    }

    public void setOnReloadMapListener(OnReloadMapListener onReloadMapListener)
    {
        mOnReloadMapListener = onReloadMapListener;
    }

    public interface OnReloadMapListener
    {
        void onReloadMap();
    }
}
