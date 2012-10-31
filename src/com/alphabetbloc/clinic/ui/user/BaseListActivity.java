package com.alphabetbloc.clinic.ui.user;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SyncStatusObserver;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Window;

import com.alphabetbloc.clinic.R;
import com.alphabetbloc.clinic.services.RefreshDataService;
import com.alphabetbloc.clinic.services.SyncManager;
import com.alphabetbloc.clinic.ui.admin.PreferencesActivity;
import com.alphabetbloc.clinic.utilities.FileUtils;
import com.alphabetbloc.clinic.utilities.UiUtils;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * 
 */
public abstract class BaseListActivity extends ListActivity implements SyncStatusObserver {

	// Swiping Parameters
	protected static final int SWIPE_MIN_DISTANCE = 120;
	protected static final int SWIPE_MAX_OFF_PATH = 250;
	protected static final int SWIPE_THRESHOLD_VELOCITY = 200;

	// Menu ID's
	private static final int MENU_REFRESH = Menu.FIRST;
	private static final int MENU_USER_PREFERENCES = Menu.FIRST + 1;
	private static final int MENU_ADMIN_PREFERENCES = Menu.FIRST + 2;
	private static final String TAG = BaseListActivity.class.getSimpleName();

	private static ProgressDialog mSyncActiveDialog;
	private static AlertDialog mRequestSyncDialog;
	private static Object mSyncObserverHandle;
	private Context mToastCtx;
	private static boolean mPaused;
	private ScheduledExecutorService mExecutor = Executors.newScheduledThreadPool(5);

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		if (!FileUtils.storageReady()) {
			UiUtils.toastAlert(this, getString(R.string.error_storage_title), getString(R.string.error_storage));
			setResult(RESULT_CANCELED);
			finish();
		}
	}

	@Override
	public void onStatusChanged(int which) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (!RefreshDataService.isSyncActive) {
					// Sync is not yet active, so we must be starting a sync
					Log.d(TAG, "SyncStatusChanged: starting a Sync");
					if (!SyncManager.sStartSync)
						showRequestSyncDialog();

				} else {
					// we are just completing a sync (whether success or not)
					Log.d(TAG, "SyncStatusChanged: completing sync");
					// dismiss dialog
					if (mSyncActiveDialog != null) {
						mSyncActiveDialog.dismiss();
						mSyncActiveDialog = null;
					}

					refreshView();

				}
			}
		});

	}

	protected abstract void refreshView();

	private void showProgressDialog() {
		SyncManager.sSyncStep = 0;
		SyncManager.sLoopProgress = 0;
		SyncManager.sLoopCount = 0;
		mSyncActiveDialog = new ProgressDialog(this);
		mSyncActiveDialog.setIcon(android.R.drawable.ic_dialog_info);
		mSyncActiveDialog.setTitle(getString(R.string.sync_in_progress_title));
		mSyncActiveDialog.setMessage(getString(R.string.sync_in_progress));
		mSyncActiveDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		mSyncActiveDialog.setCancelable(false);
		mSyncActiveDialog.setProgress(0);
		mSyncActiveDialog.show();
	}

	private void showRequestSyncDialog() {
		DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				switch (which) {
				case DialogInterface.BUTTON_POSITIVE:
					dialog.dismiss();
					SyncManager.sStartSync = true;
					updateSyncProgress();
					break;

				case DialogInterface.BUTTON_NEGATIVE:
					SyncManager.sEndSync = true;
					break;
				}
			}
		};

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setIcon(android.R.drawable.ic_dialog_info);
		builder.setTitle(getString(R.string.refresh_clients_title));
		builder.setMessage(getString(R.string.refresh_clients_text));
		builder.setPositiveButton(getString(R.string.refresh), dialogClickListener);
		builder.setNegativeButton(getString(R.string.cancel), dialogClickListener);
		mRequestSyncDialog = builder.create();
		mRequestSyncDialog.show();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		menu.add(0, MENU_REFRESH, MENU_REFRESH, getString(R.string.download_patients)).setIcon(R.drawable.ic_menu_refresh);
		menu.add(0, MENU_USER_PREFERENCES, MENU_USER_PREFERENCES, getString(R.string.pref_settings)).setIcon(android.R.drawable.ic_menu_preferences);
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		boolean showMenu = prefs.getBoolean(getString(R.string.key_show_settings_menu), false);
		if (showMenu)
			menu.add(0, MENU_ADMIN_PREFERENCES, MENU_ADMIN_PREFERENCES, getString(R.string.pref_admin_settings)).setIcon(android.R.drawable.ic_lock_lock);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		savePosition();

		switch (item.getItemId()) {
		case MENU_USER_PREFERENCES:
			Intent user = new Intent(this, PreferencesActivity.class);
			user.putExtra(PreferencesActivity.ADMIN_PREFERENCE, false);
			startActivity(user);
			return true;
		case MENU_ADMIN_PREFERENCES:
			Intent admin = new Intent(this, PreferencesActivity.class);
			admin.putExtra(PreferencesActivity.ADMIN_PREFERENCE, true);
			startActivity(admin);
			return true;
		case MENU_REFRESH:
			SyncManager.syncData();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onResume() {
		mPaused = false;
		super.onResume();
		IntentFilter filter = new IntentFilter(SyncManager.SYNC_MESSAGE);
		LocalBroadcastManager.getInstance(this).registerReceiver(onSyncNotice, filter);
		mSyncObserverHandle = ContentResolver.addStatusChangeListener(ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE, this);

		if (RefreshDataService.isSyncActive)
			updateSyncProgress();
	}

	private void updateSyncProgress() {
		SyncManager.sEndSync = false;

		if (mSyncActiveDialog == null)
			showProgressDialog();

		mExecutor.schedule(new Runnable() {
			public void run() {

				if (!SyncManager.sEndSync && !mPaused) {
					mExecutor.schedule(this, 800, TimeUnit.MILLISECONDS);
					BaseListActivity.this.runOnUiThread(new Runnable() {

						@Override
						public void run() {
							int loop = (SyncManager.sLoopProgress == SyncManager.sLoopCount) ? 0 : ((int) Math.round(((float) SyncManager.sLoopProgress / (float) SyncManager.sLoopCount) * 20F));
							mSyncActiveDialog.setProgress((SyncManager.sSyncStep * 10) + loop);
							mSyncActiveDialog.setMessage(SyncManager.sSyncTitle);
						}
					});

				}
			}
		}, 0, TimeUnit.MILLISECONDS);

	}

	protected class myGestureListener extends SimpleOnGestureListener {

		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
			try {
				if (Math.abs(e1.getY() - e2.getY()) > SWIPE_MAX_OFF_PATH)
					return false;
				if (e2.getX() - e1.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
					finish();
				}
			} catch (Exception e) {
				// nothing
			}
			return false;
		}

		@Override
		public boolean onSingleTapUp(MotionEvent e) {
			return false;
		}

		@Override
		public boolean onDown(MotionEvent e) {
			return false;
		}

	}

	protected BroadcastReceiver onSyncNotice = new BroadcastReceiver() {
		public void onReceive(Context ctxt, Intent i) {
			savePosition();
			boolean newSync = i.getBooleanExtra(SyncManager.START_NEW_SYNC, false);
			if (newSync) {
				// we are starting a new sync automatically
				updateSyncProgress();
			} else {
				// we have ongoing sync, with new sync message
				boolean error = i.getBooleanExtra(SyncManager.TOAST_ERROR, false);
				String toast = i.getStringExtra(SyncManager.TOAST_MESSAGE);
				UiUtils.toastSyncMessage(mToastCtx, toast, error);
			}
		}
	};

	@Override
	protected void onPause() {
		mPaused = true;
		super.onPause();

		ContentResolver.removeStatusChangeListener(mSyncObserverHandle);
		LocalBroadcastManager.getInstance(this).unregisterReceiver(onSyncNotice);
	}

	protected void savePosition() {
		// TODO Fill in this method if you want to save the position of the item
		// in the scroll list..
	}

}
