package com.alphabetbloc.clinic.ui.admin;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.alphabetbloc.clinic.R;
import com.alphabetbloc.clinic.listeners.SyncDataListener;
import com.alphabetbloc.clinic.tasks.CheckConnectivityTask;
import com.alphabetbloc.clinic.utilities.App;
import com.alphabetbloc.clinic.utilities.EncryptionUtil;
import com.alphabetbloc.clinic.utilities.NetworkUtils;
import com.alphabetbloc.clinic.utilities.UiUtils;

/**
 * 
 * @author Louis Fazen (louis.fazen@gmail.com)
 * 
 */
public class SetupAccountActivity extends Activity implements SyncDataListener {

	// setAccountAuthenticatorResult(android.os.Bundle);
	private static final String TAG = SetupAccountActivity.class.getSimpleName();

	// Intents
	public static final String USE_CONFIG_FILE = "use_config_file_defaults";
	public static final String LAUNCHED_FROM_ACCT_MGR = "launched_from_account_manager";

	// views
	protected static final int REQUEST_CREDENTIAL_CHANGE = 1;
	protected static final int REQUEST_CREDENTIAL_SETUP = 2;
	protected static final int CREDENTIAL_ENTRY_ERROR = 3;
	protected static final int LOADING = 4;
	protected static final int FINISHED = 5;

	// buttons
	protected static final int VERIFY_ENTRY = 1;
	protected static final int ASK_NEW_ENTRY = 2;
	protected static final int ENTRY_ERROR = 3;

	private TextView mInstructionText;
	private EditText mUserText;
	private EditText mPwdText;
	private String mCurrentUser;
	private String mCurrentPwd;
	private int mStep;
	private Button mSubmitButton;
	private boolean mImportFromConfig;
	private String mNewUser;
	private String mNewPwd;
	private Button mOfflineSetupButton;
	private ImageView mCenterImage;
	private Context mContext;

	@Override
	protected void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.account_setup);
		mContext = this;
		mImportFromConfig = getIntent().getBooleanExtra(USE_CONFIG_FILE, false);
		
		// dynamic views
		mInstructionText = (TextView) findViewById(R.id.instruction);
		mSubmitButton = (Button) findViewById(R.id.submit_button);
		mSubmitButton.setText(getString(R.string.submit));
		mSubmitButton.setOnClickListener(mSubmitListener);
		mUserText = (EditText) findViewById(R.id.edittext_username);
		mPwdText = (EditText) findViewById(R.id.edittext_password);
		mCenterImage = (ImageView) findViewById(R.id.center_image);
		mOfflineSetupButton = (Button) findViewById(R.id.offline_setup_button);
		mOfflineSetupButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				addAccount(mNewUser, mNewPwd);
				setResult(RESULT_OK);
				finish();
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		boolean firstRun = prefs.getBoolean(getString(R.string.key_first_run), true);

		if (mImportFromConfig)
			importFromConfigFile();
		else if (firstRun)
			createView(REQUEST_CREDENTIAL_SETUP);
		else if (NetworkUtils.getServerUsername() != null)
			createView(REQUEST_CREDENTIAL_CHANGE);
		else
			createView(REQUEST_CREDENTIAL_SETUP);
	}

	private void importFromConfigFile() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		String username = prefs.getString(getString(R.string.key_username), getString(R.string.default_username));
		String password = prefs.getString(getString(R.string.key_password), getString(R.string.default_password));
		addAccount(username, password);
	}

	private void createView(int view) {
		// if not loading, set appropriate buttons/text
		switch (view) {
		// changing credentials
		case REQUEST_CREDENTIAL_CHANGE:
			mStep = VERIFY_ENTRY;
			mCurrentUser = NetworkUtils.getServerUsername();
			mCurrentPwd = NetworkUtils.getServerPassword();
			mUserText.setText(mCurrentUser);
			mInstructionText.setText(R.string.auth_server_verify_account);
			mOfflineSetupButton.setVisibility(View.GONE);
			mCenterImage.setVisibility(View.GONE);
			break;

		// setting up new credentials
		case REQUEST_CREDENTIAL_SETUP:
			mStep = ASK_NEW_ENTRY;
			mInstructionText.setText(R.string.auth_server_account_setup);
			mOfflineSetupButton.setVisibility(View.GONE);
			mCenterImage.setVisibility(View.GONE);
			break;

		case CREDENTIAL_ENTRY_ERROR:
			mStep = ASK_NEW_ENTRY;
			((ProgressBar) findViewById(R.id.progress_wheel)).setVisibility(View.GONE);
			mSubmitButton.setVisibility(View.VISIBLE);
			mOfflineSetupButton.setVisibility(View.VISIBLE);
			mOfflineSetupButton.setText(R.string.auth_dont_verify);
			mSubmitButton.setText(R.string.auth_try_again);
			mUserText.setVisibility(View.VISIBLE);
			mPwdText.setVisibility(View.VISIBLE);
			mCenterImage.setVisibility(View.INVISIBLE);
			mInstructionText.setText(getString(R.string.auth_server_error_login));
			break;

		case LOADING:
			((ProgressBar) findViewById(R.id.progress_wheel)).setVisibility(View.VISIBLE);
			mCenterImage.setVisibility(View.GONE);
			mSubmitButton.setVisibility(View.GONE);
			mUserText.setVisibility(View.GONE);
			mPwdText.setVisibility(View.GONE);
			mOfflineSetupButton.setVisibility(View.GONE);
			mInstructionText.setText(getString(R.string.auth_verifying_server_account));
			break;

		case FINISHED:
			mStep = FINISHED;
			mSubmitButton.setVisibility(View.VISIBLE);
			mCenterImage.setVisibility(View.GONE);
			mOfflineSetupButton.setVisibility(View.GONE);
			mSubmitButton.setText(getString(R.string.finish));
			((ProgressBar) findViewById(R.id.progress_wheel)).setVisibility(View.GONE);
			mInstructionText.setText(getString(R.string.auth_server_setup_complete));
			break;

		default:
			break;
		}
	}

	private OnClickListener mSubmitListener = new OnClickListener() {

		@Override
		public void onClick(View v) {

			String userEntry = mUserText.getText().toString();
			String pwdEntry = mPwdText.getText().toString();
			mPwdText.setText("");

			if ((userEntry.equals("") || pwdEntry.equals("")) && (mStep != FINISHED))
				mStep = ENTRY_ERROR;

			switch (mStep) {
			case VERIFY_ENTRY:
				if (userEntry.equals(mCurrentUser) && pwdEntry.equals(mCurrentPwd))
					createView(REQUEST_CREDENTIAL_SETUP);
				else
					UiUtils.toastAlert(mContext, getString(R.string.auth_error_title), getString(R.string.auth_server_verify_error));
				break;

			case ASK_NEW_ENTRY:
				if (isAcceptable(userEntry))
					checkServerCredentials(userEntry, pwdEntry);
				else
					//TODO! does this work, or do you need String.format()?
					UiUtils.toastAlert(mContext, getString(R.string.auth_error_title), getString((R.string.auth_invalid_username), mUserText.getText().toString()));
				break;
			case FINISHED:
				setResult(RESULT_OK);
				finish();
				break;
			case ENTRY_ERROR:
			default:
				UiUtils.toastAlert(mContext, getString(R.string.auth_error_title), getString(R.string.auth_empty_entry));
				break;
			}
		}
	};

	private void checkServerCredentials(String username, String password) {
		createView(LOADING);

		mNewUser = username;
		mNewPwd = password;

		CheckConnectivityTask verifyWithServer = new CheckConnectivityTask();
		verifyWithServer.setServerCredentials(username, password);
		verifyWithServer.setSyncListener(this);
		verifyWithServer.execute(new SyncResult());
	}

	private void addAccount(String username, String password) {

		AccountManager am = AccountManager.get(this);

		// if old account exists, delete and replace with new account
		Account[] accounts = am.getAccountsByType(getString(R.string.app_account_type));
		Log.e(TAG, "about to remove old accounts number=" + accounts.length + " and add new account with u=" + username + " p=" + password);
		for (Account a : accounts) {
			am.removeAccount(a, null, null);
		}

		final Account account = new Account(username, getString(R.string.app_account_type));
		String encPwd = EncryptionUtil.encryptString(password);

		// TODO! is this necessary... does Android ever delete credentials?
		// saving it here just in case?
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(App.getApp());
		prefs.edit().putString(getString(R.string.key_username), username).commit();
		prefs.edit().putString(getString(R.string.key_password), encPwd).commit();

		boolean accountCreated = am.addAccountExplicitly(account, encPwd, null);
		if (accountCreated) {
			Log.e(TAG, "account was created");

			Log.e(TAG, "extras != null");

			String authority = getString(R.string.app_provider_authority);
			// Bundle params = new Bundle();
			// params.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, false);
			// params.putBoolean(ContentResolver.SYNC_EXTRAS_DO_NOT_RETRY,
			// false);
			// // params.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, false);
			// params.putBoolean(ContentResolver.SYNC_EXTRAS_FORCE, true);

			// Will set up sync (if global settings background data & auto-sync
			// are true)
			ContentResolver.setIsSyncable(account, authority, 1);
			ContentResolver.setSyncAutomatically(account, authority, true);

			String interval = prefs.getString(getString(R.string.key_max_refresh_seconds), getString(R.string.default_max_refresh_seconds));
			ContentResolver.addPeriodicSync(account, authority, new Bundle(), Integer.valueOf(interval));

			// params.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
			// params.putBoolean(ContentResolver.SYNC_EXTRAS_FORCE, true);
			// ContentResolver.requestSync(account, authority, params);
			// TODO! do i always need to do this or just if launched from
			// AcctMgr?
			// Pass the new account back to the account mgr
			Bundle extras = getIntent().getExtras();
			boolean launchedFromAccountMgr = extras.getBoolean(LAUNCHED_FROM_ACCT_MGR);
			if (extras != null && launchedFromAccountMgr) {
				Log.e(TAG, "launched from the account manager...");
				AccountAuthenticatorResponse response = extras.getParcelable(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE);
				Bundle result = new Bundle();
				result.putString(AccountManager.KEY_ACCOUNT_NAME, username);
				result.putString(AccountManager.KEY_ACCOUNT_TYPE, getString(R.string.app_account_type));
				response.onResult(result);
			} else {
				Log.e(TAG, "not launched from the acocunt manager");
				Log.e(TAG, "about to set the result to OK and finish");
				setResult(RESULT_OK);
			}

			// finish();
		}
	}

	private static boolean isAcceptable(String userEntry) {

		// CHANGED: accepting all characters now, but could have this be just
		// numbers etc.
		// long l = Long.valueOf(userEntry);
		// if (l < 0 || l > Integer.MAX_VALUE)
		// return false;

		return true;
	}

	@Override
	public void syncComplete(String result, SyncResult syncResult) {
		Log.e(TAG, "syncComplete...! but result=" + result);
		if (Boolean.valueOf(result)) {
			addAccount(mNewUser, mNewPwd);
			createView(FINISHED);

		} else {
			createView(CREDENTIAL_ENTRY_ERROR);
		}
	}

	@Override
	public void sslSetupComplete(String result, SyncResult syncResult) {
		// do nothing
	}

	@Override
	public void uploadComplete(String result) {
		// do nothing

	}

	@Override
	public void downloadComplete(String result) {
		// do nothing

	}

	@Override
	public void progressUpdate(String message, int progress, int max) {
		// do nothing

	}

}
