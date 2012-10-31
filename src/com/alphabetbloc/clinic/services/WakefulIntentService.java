/***
  Copyright (c) 2009-11 CommonsWare, LLC
  
  Licensed under the Apache License, Version 2.0 (the "License"); you may
  not use this file except in compliance with the License. You may obtain
  a copy of the License at
    http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */

package com.alphabetbloc.clinic.services;

import com.alphabetbloc.clinic.receivers.DeleteDecryptedDataReceiver;
import com.alphabetbloc.clinic.receivers.EncryptDataReceiver;
import com.alphabetbloc.clinic.receivers.RefreshDataReceiver;
import com.alphabetbloc.clinic.receivers.WipeDataReceiver;
import com.alphabetbloc.clinic.utilities.App;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.util.Log;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * 
 */
abstract public class WakefulIntentService extends IntentService {
	abstract protected void doWakefulWork(Intent intent);

	public static final String NAME = "com.commonsware.cwac.wakeful.WakefulIntentService";
	// static final String LAST_ALARM = "lastAlarm";
	public static final String REFRESH_DATA = "refresh.data";
	public static final String ENCRYPT_DATA = "encrypt.data";
	public static final String DELETE_DECRYPTED_DATA = "delete.decrypted.data";
	public static final String WIPE_DATA = "wipe.all.data";

	private static volatile PowerManager.WakeLock lockStatic = null;

	synchronized private static PowerManager.WakeLock getLock(Context context) {
		if (lockStatic == null) {
			PowerManager mgr = (PowerManager) context.getSystemService(Context.POWER_SERVICE);

			lockStatic = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, NAME);
			lockStatic.setReferenceCounted(true);
		}

		return (lockStatic);
	}

	public static void sendWakefulWork(Context ctxt, Intent i) {
		getLock(ctxt.getApplicationContext()).acquire();
		ctxt.startService(i);
	}

	public static void sendWakefulWork(Context ctxt, Class<?> clsService) {
		sendWakefulWork(ctxt, new Intent(ctxt, clsService));
	}

	public static void scheduleAlarms(AlarmListener listener, String receiver, Context ctxt) {
		scheduleAlarms(listener, receiver, ctxt, true);
	}

	public static void scheduleAlarms(AlarmListener listener, String receiver, Context ctxt, boolean force) {
		SharedPreferences prefs = ctxt.getSharedPreferences(NAME, 0);
		long lastAlarm = prefs.getLong(receiver, 0);

		if (lastAlarm == 0 || force || (System.currentTimeMillis() > lastAlarm && System.currentTimeMillis() - lastAlarm > listener.getMaxAge())) {
			AlarmManager mgr = (AlarmManager) ctxt.getSystemService(Context.ALARM_SERVICE);

			Intent i = null;
			// TODO! Delete this IF STATEMENT is syncadapter works!
			if (receiver.equals(REFRESH_DATA)) {
				i = new Intent(App.getApp(), RefreshDataReceiver.class);
			} else if (receiver.equals(ENCRYPT_DATA)) {
				i = new Intent(App.getApp(), EncryptDataReceiver.class);
			} else if (receiver.equals(DELETE_DECRYPTED_DATA)) {
				i = new Intent(App.getApp(), DeleteDecryptedDataReceiver.class);
			} else if (receiver.equals(WIPE_DATA)) {
				i = new Intent(App.getApp(), WipeDataReceiver.class);
			}
			PendingIntent pi = PendingIntent.getBroadcast(App.getApp(), 0, i, 0);

			listener.scheduleAlarms(mgr, pi, App.getApp());
		}
	}

	public static void cancelAlarms(String receiver, Context ctxt) {
		Intent i = null;
		AlarmManager mgr = (AlarmManager) ctxt.getSystemService(Context.ALARM_SERVICE);
		if (receiver.equals(REFRESH_DATA)) {
			i = new Intent(App.getApp(), RefreshDataReceiver.class);
		} else if (receiver.equals(ENCRYPT_DATA)) {
			i = new Intent(App.getApp(), EncryptDataReceiver.class);
		} else if (receiver.equals(DELETE_DECRYPTED_DATA)) {
			i = new Intent(App.getApp(), DeleteDecryptedDataReceiver.class);
		} else if (receiver.equals(WIPE_DATA)) {
			i = new Intent(App.getApp(), WipeDataReceiver.class);
		}

		PendingIntent pi = PendingIntent.getBroadcast(App.getApp(), 0, i, 0);
		try {
	        mgr.cancel(pi);
	        Log.e("WakefulIntentService", "Successfully cancelled an alarm with receiver=" + receiver);
	    } catch (Exception e) {
	        Log.e("WakefulIntentService", "AlarmManager update was not canceled. " + e.toString());
	    }
	}

	public WakefulIntentService(String name) {
		super(name);
		setIntentRedelivery(true);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		PowerManager.WakeLock lock = getLock(this.getApplicationContext());

		if (!lock.isHeld() || (flags & START_FLAG_REDELIVERY) != 0) {
			lock.acquire();
		}

		super.onStartCommand(intent, flags, startId);

		return (START_REDELIVER_INTENT);
	}

	@Override
	final protected void onHandleIntent(Intent intent) {
		try {
			doWakefulWork(intent);
		} finally {
			PowerManager.WakeLock lock = getLock(this.getApplicationContext());

			if (lock.isHeld()) {
				lock.release();
			}
		}
	}

	public interface AlarmListener {
		void scheduleAlarms(AlarmManager mgr, PendingIntent pi, Context ctxt);

		void sendWakefulWork(Context ctxt);

		long getMaxAge();
	}
}
