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

package com.alphabetbloc.clinic.listeners;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.os.SystemClock;

import com.alphabetbloc.clinic.services.DeleteDecryptedFilesService;
import com.alphabetbloc.clinic.services.WakefulIntentService;
/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 *
 */
public class DeleteDecryptedDataListener implements WakefulIntentService.AlarmListener {

	public void scheduleAlarms(AlarmManager mgr, PendingIntent pi, Context ctxt) {
		
		//TODO! CHANGE ALARM INTERVAL!!
		mgr.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 60000, AlarmManager.INTERVAL_FIFTEEN_MINUTES/8, pi);
	}

	public void sendWakefulWork(Context ctxt) {
		WakefulIntentService.sendWakefulWork(ctxt, DeleteDecryptedFilesService.class);
	}

	public long getMaxAge() {
		return (AlarmManager.INTERVAL_HALF_HOUR * 2);
	}
}
