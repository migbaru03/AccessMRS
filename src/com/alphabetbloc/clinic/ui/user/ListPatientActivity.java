package com.alphabetbloc.clinic.ui.user;

import java.util.ArrayList;

import org.odk.clinic.android.openmrs.Patient;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SyncStatusObserver;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.alphabetbloc.clinic.R;
import com.alphabetbloc.clinic.adapters.PatientAdapter;
import com.alphabetbloc.clinic.providers.DbProvider;
import com.alphabetbloc.clinic.services.RefreshDataService;
import com.alphabetbloc.clinic.ui.admin.PreferencesActivity;
import com.alphabetbloc.clinic.utilities.App;
import com.alphabetbloc.clinic.utilities.FileUtils;

public class ListPatientActivity extends ListActivity implements SyncStatusObserver{

	// Menu ID's
	private static final int MENU_PREFERENCES = Menu.FIRST;
	public static final int DOWNLOAD_PATIENT = 1;
	public static final int BARCODE_CAPTURE = 2;
	public static final int FILL_BLANK_FORM = 3;
	private static final int SWIPE_MIN_DISTANCE = 120;
	private static final int SWIPE_MAX_OFF_PATH = 250;
	private static final int SWIPE_THRESHOLD_VELOCITY = 200;
	public static final int PROGRESS_DIALOG = 1;
	
	public static int mListType;
	private EditText mSearchText;
	private TextWatcher mFilterTextWatcher;
	private ArrayAdapter<Patient> mPatientAdapter;
	private ArrayList<Patient> mPatients = new ArrayList<Patient>();
	private Context mContext;
	private String mSearchPatientStr = null;
	private String mSearchPatientId = null;
	private RelativeLayout mSearchBar;
	private ImageButton mAddClientButton;
	private Button mSimilarClientButton;
	private Button mCancelClientButton;
	protected GestureDetector mClientDetector;
	protected OnTouchListener mClientListener;
	protected GestureDetector mSwipeDetector;
	protected OnTouchListener mSwipeListener;
	private ListView mClientListView;
	private int mIndex = 0;
	private int mTop = 0;
	private static Object mSyncObserverHandle;
	private static ProgressDialog mProgressDialog;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mContext = this;

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.find_patient);
		setTitle(getString(R.string.app_name) + " > " + getString(R.string.find_patient));

		if (!FileUtils.storageReady()) {
			showCustomToast(getString(R.string.error, getString(R.string.storage_error)));
			finish();
		}
		// get intents
		mListType = getIntent().getIntExtra(DashboardActivity.LIST_TYPE, 1);
		if (mListType == DashboardActivity.LIST_SIMILAR_CLIENTS) {
			mSearchPatientStr = getIntent().getStringExtra("search_name_string");
			mSearchPatientId = getIntent().getStringExtra("search_id_string");
		}

		mFilterTextWatcher = new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				if (mPatientAdapter != null) {
					mPatientAdapter.getFilter().filter(s);
				}
			}

			@Override
			public void afterTextChanged(Editable s) {

			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {

			}
		};

		mSearchText = (EditText) findViewById(R.id.search_text);
		mSearchText.addTextChangedListener(mFilterTextWatcher);

		/*
		 * mBarcodeButton = (ImageButton) findViewById(R.id.barcode_button);
		 * mBarcodeButton.setOnClickListener(new OnClickListener() { public void
		 * onClick(View v) { Intent i = new
		 * Intent("com.google.zxing.client.android.SCAN"); try {
		 * startActivityForResult(i, BARCODE_CAPTURE); } catch
		 * (ActivityNotFoundException e) { Toast t =
		 * Toast.makeText(getApplicationContext(), getString(R.string.error,
		 * getString(R.string.barcode_error)), Toast.LENGTH_SHORT);
		 * t.setGravity(Gravity.CENTER_VERTICAL, 0, 0); t.show(); } } });
		 */

		mAddClientButton = (ImageButton) findViewById(R.id.add_client);
		mAddClientButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {

				Intent i = new Intent(mContext, CreatePatientActivity.class);
				startActivity(i);

			}
		});

		// Verify Similar Clients on Add New Client
//		mSearchBar = (RelativeLayout) findViewById(R.id.searchholder);
		mSearchBar = (RelativeLayout) findViewById(R.id.search_holder);
		mSimilarClientButton = (Button) findViewById(R.id.similar_client_button);
		mSimilarClientButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				setResult(RESULT_OK);
				finish();
			}
		});
		mSimilarClientButton.setVisibility(View.GONE);

		mCancelClientButton = (Button) findViewById(R.id.cancel_client_button);
		mCancelClientButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				setResult(RESULT_CANCELED);
				finish();
			}
		});
		mCancelClientButton.setVisibility(View.GONE);

		mClientDetector = new GestureDetector(new onClientClick());
		mClientListener = new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				return mClientDetector.onTouchEvent(event);
			}
		};

		mSwipeDetector = new GestureDetector(new onHeadingClick());
		mSwipeListener = new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				return mSwipeDetector.onTouchEvent(event);
			}
		};
	}

	// TODO!: consider changing this whole thing to a viewpager... may be much
	// simpler, and also add animation
	class onClientClick extends SimpleOnGestureListener {

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
			int pos = mClientListView.pointToPosition((int) e.getX(), (int) e.getY());
			if (pos != -1) {
				savePosition();
				Patient p = (Patient) mPatientAdapter.getItem(pos);
				String patientIdStr = p.getPatientId().toString();
				Intent ip = new Intent(getApplicationContext(), ViewPatientActivity.class);
				ip.putExtra(ViewDataActivity.KEY_PATIENT_ID, patientIdStr);
				startActivity(ip);
			}
			return false;
		}

		@Override
		public boolean onDown(MotionEvent e) {
			return false;
		}

	}

	class onHeadingClick extends SimpleOnGestureListener {

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

	private void savePosition() {
		mIndex = mClientListView.getFirstVisiblePosition();
		View v = mClientListView.getChildAt(0);
		mTop = (v == null) ? 0 : v.getTop();
	}

	@Override
	protected void onResume() {
		super.onResume();
		mSearchText.setText(mSearchText.getText().toString());
		IntentFilter filter = new IntentFilter(RefreshDataService.REFRESH_BROADCAST);
		LocalBroadcastManager.getInstance(this).registerReceiver(onNotice, filter);
		mSyncObserverHandle = ContentResolver.addStatusChangeListener(ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE, this);
		if (mProgressDialog != null && !mProgressDialog.isShowing()) {
			mProgressDialog.show();
		}
		
		// NB: get immediate view position
		if (mClientListView != null)
			mClientListView.setSelectionFromTop(mIndex, mTop);

		//then refresh the view
		findPatients();
	}

	// VIEW:
	private void findPatients() {

		if (mSearchPatientStr == null && mSearchPatientId == null) {
			mPatients.clear();
			getPatients(null, null);
		} else {
			mPatients.clear();
			if (mSearchPatientStr != null) {

				getPatients(mSearchPatientStr, null);
			}
			if (mSearchPatientId != null && mSearchPatientId.length() > 3) {

				getPatients(null, mSearchPatientId);
			}
		}

		refreshView();
	}

	private void getPatients(String searchString, String patientId) {

		DbProvider ca = DbProvider.openDb();

		Cursor c = null;
		if (mSearchPatientStr != null || mSearchPatientId != null) {

			c = ca.fetchPatients(searchString, patientId, mListType);
		} else {
			c = ca.fetchAllPatients(mListType);
		}

		if (c != null && c.getCount() >= 0) {

			int patientIdIndex = c.getColumnIndex(DbProvider.KEY_PATIENT_ID);
			int identifierIndex = c.getColumnIndex(DbProvider.KEY_IDENTIFIER);
			int givenNameIndex = c.getColumnIndex(DbProvider.KEY_GIVEN_NAME);
			int familyNameIndex = c.getColumnIndex(DbProvider.KEY_FAMILY_NAME);
			int middleNameIndex = c.getColumnIndex(DbProvider.KEY_MIDDLE_NAME);
			int birthDateIndex = c.getColumnIndex(DbProvider.KEY_BIRTH_DATE);
			int genderIndex = c.getColumnIndex(DbProvider.KEY_GENDER);
			int priorityIndex = c.getColumnIndexOrThrow(DbProvider.KEY_PRIORITY_FORM_NUMBER);
			int priorityFormIndex = c.getColumnIndexOrThrow(DbProvider.KEY_PRIORITY_FORM_NAMES);
			int savedIndex = c.getColumnIndexOrThrow(DbProvider.KEY_SAVED_FORM_NUMBER);
			int savedFormIndex = c.getColumnIndexOrThrow(DbProvider.KEY_SAVED_FORM_NAMES);

			if (c.getCount() > 0) {

				Patient p;
				do {
					p = new Patient();
					p.setPatientId(c.getInt(patientIdIndex));
					p.setIdentifier(c.getString(identifierIndex));
					p.setGivenName(c.getString(givenNameIndex));
					p.setFamilyName(c.getString(familyNameIndex));
					p.setMiddleName(c.getString(middleNameIndex));
					p.setBirthDate(c.getString(birthDateIndex));
					p.setGender(c.getString(genderIndex));
					p.setPriorityNumber(c.getInt(priorityIndex));
					p.setPriorityForms(c.getString(priorityFormIndex));
					p.setSavedNumber(c.getInt(savedIndex));
					p.setSavedForms(c.getString(savedFormIndex));
					p.setUuid(c.getString(savedFormIndex));

					if (c.getInt(priorityIndex) > 0) {
						p.setPriority(true);
					} else {
						p.setPriority(false);
					}

					if (c.getInt(savedIndex) > 0) {
						p.setSaved(true);
					} else {
						p.setSaved(false);
					}

					mPatients.add(p);

				} while (c.moveToNext());
			}

		}

		if (c != null) {
			c.close();
		}
	}

	private void refreshView() {

		RelativeLayout listLayout = (RelativeLayout) findViewById(R.id.list_type);
		TextView listText = (TextView) findViewById(R.id.name_text);
		ImageView listIcon = (ImageView) findViewById(R.id.section_image);

		switch (mListType) {
		case DashboardActivity.LIST_SUGGESTED:
			listLayout.setBackgroundResource(R.color.priority);
			listIcon.setBackgroundResource(R.drawable.ic_priority);
			listText.setText(R.string.suggested_clients_section);
			break;
		case DashboardActivity.LIST_INCOMPLETE:
			listLayout.setBackgroundResource(R.color.saved);
			listIcon.setBackgroundResource(R.drawable.ic_saved);
			listText.setText(R.string.incomplete_clients_section);
			break;
		case DashboardActivity.LIST_COMPLETE:
			listLayout.setBackgroundResource(R.color.completed);
			listIcon.setBackgroundResource(R.drawable.ic_completed);
			listText.setText(R.string.completed_clients_section);
			break;
		case DashboardActivity.LIST_SIMILAR_CLIENTS:
			listLayout.setBackgroundResource(R.color.priority);
			listIcon.setBackgroundResource(R.drawable.ic_priority);
			String similarClient;
			if (mPatients.size() < 2) {
				similarClient = getString(R.string.similar_client_section);
			} else {
				similarClient = getString(R.string.similar_clients_section);
			}
			listText.setText(String.valueOf(mPatients.size()) + " " + similarClient);
			mSearchText.setVisibility(View.GONE);
			mAddClientButton.setVisibility(View.GONE);
			mSearchBar.setVisibility(View.GONE);
			mSimilarClientButton.setVisibility(View.VISIBLE);
			mCancelClientButton.setVisibility(View.VISIBLE);

			break;
		case DashboardActivity.LIST_ALL:
			listLayout.setBackgroundResource(R.color.dark_gray);
			listIcon.setBackgroundResource(R.drawable.ic_additional);
			listText.setText(R.string.all_clients_section);
			break;
		}

		mPatientAdapter = new PatientAdapter(this, R.layout.patient_list_item, mPatients);
		// setListAdapter(mPatientAdapter);

		mClientListView = getListView();
		mClientListView.setAdapter(mPatientAdapter);

		mClientListView.setOnTouchListener(mClientListener);
		listLayout.setOnTouchListener(mSwipeListener);
		
		//get the same item position as before (but now should have updated numbers)
		if (mIndex > 0 || mTop > 0){
			mClientListView.setSelectionFromTop(mIndex, mTop);
			mIndex = 0;
			mTop = 0;
		}
	}

	// BUTTONS

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		SharedPreferences settings = getSharedPreferences("ChwSettings", MODE_PRIVATE);
		if (settings.getBoolean("IsMenuEnabled", true) == false) {
			return false;
		} else {
			menu.add(0, MENU_PREFERENCES, 0, getString(R.string.pref_settings)).setIcon(android.R.drawable.ic_menu_preferences);
			return true;
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case MENU_PREFERENCES:
			savePosition();
			Intent ip = new Intent(getApplicationContext(), PreferencesActivity.class);
			startActivity(ip);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	private BroadcastReceiver onNotice = new BroadcastReceiver() {
		public void onReceive(Context ctxt, Intent i) {
			savePosition();
			Intent intent = new Intent(mContext, RefreshDataActivity.class);
			intent.putExtra(RefreshDataActivity.DIALOG, RefreshDataActivity.ASK_TO_DOWNLOAD);
			startActivity(intent);

		}
	};

	@Override
	public void onStatusChanged(int which) {

		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				checkSyncActivity();
			}
		});

	}

	public boolean checkSyncActivity() {
		boolean syncActive = false;
		AccountManager accountManager = AccountManager.get(App.getApp());
		Account[] accounts = accountManager.getAccountsByType(App.getApp().getString(R.string.app_account_type));

		if (accounts.length <= 0)
			return false;

		syncActive = ContentResolver.isSyncActive(accounts[0], getString(R.string.app_provider_authority));

		if (syncActive) {

			showDialog(PROGRESS_DIALOG);

		} else {

			if (mProgressDialog != null) {
				mProgressDialog.dismiss();
			}
		}

		return syncActive;
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		if (mProgressDialog != null && mProgressDialog.isShowing()) {
			mProgressDialog.dismiss();
		}

		mProgressDialog = new ProgressDialog(this);
		mProgressDialog.setIcon(android.R.drawable.ic_dialog_info);
		mProgressDialog.setTitle(getString(R.string.sync_in_progress_title));
		mProgressDialog.setMessage(getString(R.string.sync_in_progress));
		mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		mProgressDialog.setIndeterminate(true);
		mProgressDialog.setCancelable(false);
		return mProgressDialog;
	}

	
	// LIFECYCLE
	@Override
	protected void onPause() {
		super.onPause();
		ContentResolver.removeStatusChangeListener(mSyncObserverHandle);
		LocalBroadcastManager.getInstance(this).unregisterReceiver(onNotice);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
	}

	@Override
	protected void onDestroy() {

		super.onDestroy();
		mSearchText.removeTextChangedListener(mFilterTextWatcher);

	}

	private void showCustomToast(String message) {
		LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View view = inflater.inflate(R.layout.toast_view, null);

		// set the text in the view
		TextView tv = (TextView) view.findViewById(R.id.message);
		tv.setText(message);

		Toast t = new Toast(this);
		t.setView(view);
		t.setDuration(Toast.LENGTH_SHORT);
		t.setGravity(Gravity.CENTER, 0, 0);
		t.show();
	}

}