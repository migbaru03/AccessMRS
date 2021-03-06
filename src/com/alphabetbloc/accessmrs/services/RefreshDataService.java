/*
 * Copyright (C) 2012 Louis Fazen
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.alphabetbloc.accessmrs.services;

import java.util.Iterator;
import java.util.List;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.alphabetbloc.accessmrs.providers.Db;
import com.alphabetbloc.accessmrs.ui.admin.LauncherActivity;
import com.alphabetbloc.accessmrs.ui.user.DashboardActivity;
import com.alphabetbloc.accessmrs.utilities.App;
import com.alphabetbloc.accessmrs.utilities.LauncherUtil;
import com.alphabetbloc.accessmrs.R;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com) This checks the user activity
 *         before refreshing the patient list as background service or
 *         foreground activity.
 */
public class RefreshDataService extends Service {
	public static final String REFRESH_BROADCAST = "com.alphabetbloc.accessmrs.services.SignalStrengthService";
	private static final int NOTIFICATION = 1;
	private static final String TAG = RefreshDataService.class.getSimpleName();
	private static final Object sSyncAdapterLock = new Object();
	private static SyncAdapter sSyncAdapter = null;
	private static NotificationManager mNM;
	public static boolean isSyncActive = false;

	@Override
	public void onCreate() {
		if (App.DEBUG) Log.i(TAG, "Sync is now Active! Creating a new service");
		Thread.currentThread().setName(TAG);

		// 1. Dont Sync if Setup is Incomplete
		if (!LauncherUtil.isSetupComplete()) {
			launchAccessMrsSetup();
			SyncManager.sCancelSync.set(true);
			if (App.DEBUG) Log.v(TAG, "AccessMRS is Not Setup. Cancelling sync until AccessMRS setup is complete.");

			// 2. Don't Sync if actively entering data
		} else if (isUserEnteringData()) {
			SyncManager.sCancelSync.set(true);
			if (App.DEBUG) Log.v(TAG, "User is entering data, so canceling the sync");

			// 3. Dont AutoSync if Manual Sync just completed...
		} else if (!SyncManager.sStartSync.get() && isLastSyncRecent()) {
			SyncManager.sCancelSync.set(true);
			if (App.DEBUG) Log.v(TAG, "Sync was recently completed. Not performing sync at this time.");
		}

		if (!SyncManager.sCancelSync.get()) {
			// Starting a Sync Now
			showNotification();

			if (!SyncManager.sStartSync.get()) {
				// Sync is generated by Alarm, so ask user to sync
				Intent broadcast = new Intent(SyncManager.SYNC_MESSAGE);
				broadcast.putExtra(SyncManager.REQUEST_NEW_SYNC, true);
				LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
			}
		}

		isSyncActive = true;
		synchronized (sSyncAdapterLock) {
			if (sSyncAdapter == null) {
				sSyncAdapter = new SyncAdapter(getApplicationContext(), true);
			}
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return sSyncAdapter.getSyncAdapterBinder();
	}

	@Override
	public void onDestroy() {
		isSyncActive = false;
		if (App.DEBUG) Log.v(TAG, "syncNotActive, Shutting down the Service");
		if (mNM != null)
			mNM.cancel(NOTIFICATION);
		super.onDestroy();
	}

	private void launchAccessMrsSetup() {
		if (!LauncherActivity.sLaunching) {
			if (App.DEBUG) Log.v(TAG, "AccessMRS is Not Setup... and not currently active... so RefreshDataService is requesting setup");
			Intent i = new Intent(App.getApp(), LauncherActivity.class);
			i.putExtra(LauncherActivity.LAUNCH_DASHBOARD, false);
			i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(i);
		}

		// else it is already undergoing setup
	}

	private boolean isUserEnteringData() {
		RunningAppProcessInfo currentApp = null;
		String accessFormsPackage = "com.alphabetbloc.accessforms";

		boolean enteringData = false;
		ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		List<RunningAppProcessInfo> l = am.getRunningAppProcesses();
		Iterator<RunningAppProcessInfo> i = l.iterator();

		while (i.hasNext()) {
			currentApp = i.next();
			if (currentApp.processName.equalsIgnoreCase(accessFormsPackage)) {
				switch (currentApp.importance) {
//				case RunningAppProcessInfo.IMPORTANCE_FOREGROUND:
				case RunningAppProcessInfo.IMPORTANCE_VISIBLE:
					enteringData = true;
					break;
				default:
					enteringData = false;
					break;
				}
			}
		}

		return enteringData;
	}

	private boolean isLastSyncRecent() {
		boolean recentSync = false;
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(App.getApp());
		String minRefresh = prefs.getString(getString(R.string.key_min_refresh_seconds), getString(R.string.default_min_refresh_seconds));
		long minRefreshMs = Long.valueOf(minRefresh) * 1000L;
		long delta = (System.currentTimeMillis() - Db.open().fetchMostRecentDownload());
		if (App.DEBUG) Log.v(TAG, "Sync History. minRefreshMs=" + minRefreshMs + " delta=" + delta);
		if (delta < minRefreshMs)
			recentSync = true;
		return recentSync;
	}

	private void showNotification() {

		mNM = (NotificationManager) App.getApp().getSystemService(NOTIFICATION_SERVICE);
		CharSequence text = App.getApp().getText(R.string.ss_service_started);
		Notification notification = new Notification(R.drawable.icon, text, System.currentTimeMillis());
		PendingIntent contentIntent = PendingIntent.getActivity(App.getApp(), 0, new Intent(App.getApp(), DashboardActivity.class), Intent.FLAG_ACTIVITY_NEW_TASK);
		notification.setLatestEventInfo(App.getApp(), App.getApp().getText(R.string.ss_service_label), text, contentIntent);
		mNM.notify(NOTIFICATION, notification);

	}
}
