package org.odk.clinic.android.unused;

import java.util.ArrayList;

import org.odk.clinic.android.openmrs.Observation;
import org.odk.clinic.android.openmrs.Patient;

import android.app.ListActivity;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.alphabetbloc.clinic.R;
import com.alphabetbloc.clinic.providers.DbProvider;
import com.alphabetbloc.clinic.providers.DbProvider;
import com.alphabetbloc.clinic.ui.user.ViewDataActivity;
import com.alphabetbloc.clinic.utilities.FileUtils;

public class ObservationTimelineActivity extends ListActivity {

	private Patient mPatient;
	private String mObservationFieldName;
	
	private ArrayAdapter<Observation> mEncounterAdapter;
	private ArrayList<Observation> mEncounters = new ArrayList<Observation>();
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.observation_timeline);
		
		if (!FileUtils.storageReady()) {
			showCustomToast(getString(R.string.error, R.string.storage_error));
			finish();
		}

		String patientIdStr = getIntent().getStringExtra(ViewDataActivity.KEY_PATIENT_ID);
		Integer patientId = Integer.valueOf(patientIdStr);
		mPatient = getPatient(patientId);
		if (mPatient == null) {
			showCustomToast(getString(R.string.error, R.string.no_patient));
			finish();
		}
		
		mObservationFieldName = getIntent().getStringExtra(ViewDataActivity.KEY_OBSERVATION_FIELD_NAME);
		
		setTitle(getString(R.string.app_name) + " > "
				+ getString(R.string.view_patient_detail));
		
		TextView textView = (TextView) findViewById(R.id.title_text);
		if (textView != null) {
			textView.setText(mObservationFieldName);
		}
	}
	
	private Patient getPatient(Integer patientId) {

		Patient p = null;

		Cursor c = DbProvider.openDb().fetchPatient(patientId);

		if (c != null && c.getCount() > 0) {
			int patientIdIndex = c
					.getColumnIndex(DbProvider.KEY_PATIENT_ID);
			int identifierIndex = c
					.getColumnIndex(DbProvider.KEY_IDENTIFIER);
			int givenNameIndex = c
					.getColumnIndex(DbProvider.KEY_GIVEN_NAME);
			int familyNameIndex = c
					.getColumnIndex(DbProvider.KEY_FAMILY_NAME);
			int middleNameIndex = c
					.getColumnIndex(DbProvider.KEY_MIDDLE_NAME);
			int birthDateIndex = c
					.getColumnIndex(DbProvider.KEY_BIRTH_DATE);
			int genderIndex = c.getColumnIndex(DbProvider.KEY_GENDER);
			
			p = new Patient();
			p.setPatientId(c.getInt(patientIdIndex));
			p.setIdentifier(c.getString(identifierIndex));
			p.setGivenName(c.getString(givenNameIndex));
			p.setFamilyName(c.getString(familyNameIndex));
			p.setMiddleName(c.getString(middleNameIndex));
			p.setBirthDate(c.getString(birthDateIndex));
			p.setGender(c.getString(genderIndex));
		}

		if (c != null) {
			c.close();
		}

		return p;
	}
	
	private void getObservations(Integer patientId, String fieldName) {
		
		Cursor c = DbProvider.openDb().fetchPatientObservation(patientId, fieldName);
		
		if (c != null && c.getCount() >= 0) {
			
			mEncounters.clear();

			int valueTextIndex = c.getColumnIndex(DbProvider.KEY_VALUE_TEXT);
			int valueIntIndex = c.getColumnIndex(DbProvider.KEY_VALUE_INT);
			int valueDateIndex = c.getColumnIndex(DbProvider.KEY_VALUE_DATE);
			int valueNumericIndex = c.getColumnIndex(DbProvider.KEY_VALUE_NUMERIC);
			int encounterDateIndex = c.getColumnIndex(DbProvider.KEY_ENCOUNTER_DATE);
			int dataTypeIndex = c.getColumnIndex(DbProvider.KEY_DATA_TYPE);

			Observation obs;
			do {
				obs = new Observation();
				obs.setFieldName(fieldName);
					obs.setEncounterDate(c
							.getString(encounterDateIndex));

				int dataType = c.getInt(dataTypeIndex);
				obs.setDataType((byte) dataType);
				switch (dataType) {
				case DbProvider.TYPE_INT:
					obs.setValueInt(c.getInt(valueIntIndex));
					break;
				case DbProvider.TYPE_DOUBLE:
					obs.setValueNumeric(c.getDouble(valueNumericIndex));
					break;
				case DbProvider.TYPE_DATE:
						obs.setValueDate(c
								.getString(valueDateIndex));

					break;
				default:
					obs.setValueText(c.getString(valueTextIndex));
				}

				mEncounters.add(obs);

			} while(c.moveToNext());
		}

		refreshView();
		
		if (c != null) {
			c.close();
		}
	}
	
	private void refreshView() {

		mEncounterAdapter = new EncounterAdapter(this, R.layout.encounter_list_item,
				mEncounters);
		setListAdapter(mEncounterAdapter);

	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	@Override
	protected void onResume() {
		super.onResume();
		
		if (mPatient != null && mObservationFieldName != null) {
			getObservations(mPatient.getPatientId(), mObservationFieldName);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();

	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
	}
	
	private void showCustomToast(String message) {
		LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View view = inflater.inflate(R.layout.toast_view, null);

		// set the text in the view
		TextView tv = (TextView) view.findViewById(R.id.message);
		tv.setText(message);

		Toast t = new Toast(this);
		t.setView(view);
		t.setDuration(Toast.LENGTH_LONG);
		t.setGravity(Gravity.CENTER, 0, 0);
		t.show();
	}
}
