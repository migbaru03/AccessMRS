package com.alphabetbloc.clinic.ui.user;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SyncResult;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.alphabetbloc.clinic.R;
import com.alphabetbloc.clinic.listeners.SyncDataListener;
import com.alphabetbloc.clinic.services.RefreshDataService;
import com.alphabetbloc.clinic.tasks.DownloadDataTask;
import com.alphabetbloc.clinic.tasks.SyncDataTask;
import com.alphabetbloc.clinic.tasks.UploadDataTask;
import com.alphabetbloc.clinic.utilities.FileUtils;

public class RefreshDataActivity extends Activity implements SyncDataListener {

	public final static int ASK_TO_DOWNLOAD = 1;
	public final static int DIRECT_TO_DOWNLOAD = 2;
	public final static String DIALOG = "showdialog";
	private static final String TAG = RefreshDataActivity.class.getSimpleName();
//	private Context mContext;
	private ProgressDialog mProgressDialog;
	private AlertDialog mAlertDialog;
	private DownloadDataTask mDownloadTask;
	private UploadDataTask mUploadTask;
	private SyncDataTask mSyncTask;
	private int showProgress;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTitle(getString(R.string.app_name) + " > " + getString(R.string.download_patients));
//		mContext = this;

		if (!FileUtils.storageReady()) {
			showCustomToast(getString(R.string.error, R.string.storage_error));
			setResult(RESULT_CANCELED);
			finish();
		}

		mSyncTask = (SyncDataTask) getLastNonConfigurationInstance();
		mUploadTask = (UploadDataTask) getLastNonConfigurationInstance();
		mDownloadTask = (DownloadDataTask) getLastNonConfigurationInstance();

		showProgress = getIntent().getIntExtra(DIALOG, ASK_TO_DOWNLOAD);

		// get the task if we've changed orientations.
		if (mSyncTask == null && mUploadTask == null && mDownloadTask == null) {
			showDialog(showProgress);
			if (showProgress == DIRECT_TO_DOWNLOAD)
				syncData();
		}

	}

	@Override
	protected void onResume() {
		super.onResume();

		if (mSyncTask != null) {
			mSyncTask.setSyncListener(this);
		}
		if (mUploadTask != null) {
			mUploadTask.setSyncListener(this);
		}
		if (mDownloadTask != null) {
			mDownloadTask.setSyncListener(this);
		}

		if (mProgressDialog != null && !mProgressDialog.isShowing()) {
			mProgressDialog.show();
		}

	}

	private void syncData() {
		if (mSyncTask != null)
			return;
		mSyncTask = new SyncDataTask();
		mSyncTask.setSyncListener(this);
		mSyncTask.execute(new SyncResult());
	}

	@Override
	public void sslSetupComplete(String result, SyncResult syncResult) {
		mUploadTask = new UploadDataTask();
		mUploadTask.setSyncListener(this);
		mUploadTask.execute(syncResult);
		mDownloadTask = new DownloadDataTask();
		mDownloadTask.setSyncListener(this);
		mDownloadTask.execute(syncResult);
	}

	@Override
	public void uploadComplete(String result) {
		Log.e(TAG, "Upload Complete");
		if (result != null)
			showCustomToast(result);
		mUploadTask = null;
	}

	@Override
	public void downloadComplete(String result) {
		Log.e(TAG, "Download Complete");
		if (result != null)
			showCustomToast(getString(R.string.error, result));
		mDownloadTask = null;
	}

	@Override
	public void syncComplete(String result, SyncResult syncResult) {
		if (mProgressDialog != null) {
			mProgressDialog.dismiss();
		}
		stopRefreshDataActivity(true);
	}

	// DIALOG SECTION
	@Override
	protected Dialog onCreateDialog(int id) {
		if (mProgressDialog != null && mProgressDialog.isShowing()) {
			mProgressDialog.dismiss();
		}
		if (mAlertDialog != null && mAlertDialog.isShowing()) {
			mAlertDialog.dismiss();
		}

		// if you are seeing this, then you either
		// 1. received an alarm or have plugged in while using clinic
		switch (id) {
		case ASK_TO_DOWNLOAD:
			mAlertDialog = createAskDialog();
			return mAlertDialog;
			// 2. or pressed update manually
		case DIRECT_TO_DOWNLOAD:
			mProgressDialog = createDownloadDialog();
			return mProgressDialog;
		default:
			mAlertDialog = createAskDialog();
			return mAlertDialog;
		}
	}

	private AlertDialog createAskDialog() {
		DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				switch (which) {
				case DialogInterface.BUTTON_POSITIVE:
					dialog.dismiss();
					showDialog(DIRECT_TO_DOWNLOAD);
					syncData();
					break;

				case DialogInterface.BUTTON_NEGATIVE:
					stopRefreshDataActivity(false);
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
		return builder.create();
	}

	private ProgressDialog createDownloadDialog() {
		ProgressDialog pD = new ProgressDialog(this);
		DialogInterface.OnClickListener loadingButtonListener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
				if (mSyncTask != null) {
					mSyncTask.setSyncListener(null);
					mSyncTask.cancel(true);
					mSyncTask = null;
				}
				if (mDownloadTask != null) {
					mDownloadTask.setSyncListener(null);
					mDownloadTask.cancel(true);
					mDownloadTask = null;
				}
				if (mUploadTask != null) {
					mUploadTask.setSyncListener(null);
					mUploadTask.cancel(true);
					mUploadTask = null;
				}
				stopRefreshDataActivity(true);
			}
		};

		pD.setIcon(android.R.drawable.ic_dialog_info);
		pD.setTitle(getString(R.string.uploading_patients));
		pD.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		pD.setIndeterminate(false);
		pD.setCancelable(false);
		pD.setButton(getString(R.string.cancel), loadingButtonListener);
		return pD;
	}

	@Override
	public void progressUpdate(String message, int progress, int max) {
		if (mProgressDialog != null) {
			mProgressDialog.setMax(max);
			mProgressDialog.setProgress(progress);
			mProgressDialog.setTitle(getString(R.string.downloading, message));
		}
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		if (mSyncTask != null && mSyncTask.getStatus() != AsyncTask.Status.FINISHED)
			return mSyncTask;
		if (mUploadTask != null && mUploadTask.getStatus() != AsyncTask.Status.FINISHED)
			return mUploadTask;
		if (mDownloadTask != null && mDownloadTask.getStatus() != AsyncTask.Status.FINISHED)
			return mDownloadTask;
		return null;
	}

	private void stopRefreshDataActivity(boolean reloadDashboard) {

		Intent stopintent = new Intent(getApplicationContext(), RefreshDataService.class);
		stopService(stopintent);
		// reschedule alarms (b/c either user is hitting cancel or recent sync)
		// TODO! check if this should be false!
//		WakefulIntentService.scheduleAlarms(new RefreshDataListener(), WakefulIntentService.REFRESH_DATA, mContext, true);

		if (reloadDashboard) {
			Intent startintent = new Intent(getApplicationContext(), DashboardActivity.class);
			startintent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(startintent);
		}

		finish();
	}

	@Override
	protected void onDestroy() {
		Log.e("louis.fazen", "RefreshDataActivity.onDestroy is called");

		if (mProgressDialog != null && mProgressDialog.isShowing()) {
			mProgressDialog.dismiss();
		}
		if (mAlertDialog != null && mAlertDialog.isShowing()) {
			mAlertDialog.dismiss();
		}

		if (mSyncTask != null) {
			mSyncTask.cancel(true);
			mSyncTask = null;
		}
		if (mUploadTask != null) {
			mUploadTask.cancel(true);
			mUploadTask = null;
		}
		if (mDownloadTask != null) {
			mDownloadTask.cancel(true);
			mDownloadTask = null;
		}
		super.onDestroy();
	}

	@Override
	protected void onPause() {
		super.onPause();

		if (mProgressDialog != null && mProgressDialog.isShowing()) {
			mProgressDialog.dismiss();
		}

		if (mAlertDialog != null && mAlertDialog.isShowing()) {
			mAlertDialog.dismiss();
		}

	}

	private void showCustomToast(String message) {

		LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View view = inflater.inflate(R.layout.toast_view, null);

		TextView tv = (TextView) view.findViewById(R.id.message);
		tv.setText(message);

		Toast t = new Toast(this);
		t.setView(view);
		t.setDuration(Toast.LENGTH_LONG);
		t.setGravity(Gravity.BOTTOM, 0, -20);
		t.show();

	}

}