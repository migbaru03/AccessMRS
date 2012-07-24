package org.odk.clinic.android.activities;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.kxml2.io.KXmlParser;
import org.kxml2.io.KXmlSerializer;
import org.kxml2.kdom.Document;
import org.kxml2.kdom.Element;
import org.odk.clinic.android.R;
import org.odk.clinic.android.adapters.FormAdapter;
import org.odk.clinic.android.adapters.MergeAdapter;
import org.odk.clinic.android.database.ClinicAdapter;
import org.odk.clinic.android.openmrs.ActivityLog;
import org.odk.clinic.android.openmrs.Constants;
import org.odk.clinic.android.openmrs.Form;
import org.odk.clinic.android.openmrs.FormInstance;
import org.odk.clinic.android.openmrs.Observation;
import org.odk.clinic.android.openmrs.Patient;
import org.odk.clinic.android.utilities.App;
import org.odk.clinic.android.utilities.FileUtils;
import org.odk.collect.android.provider.FormsProviderAPI.FormsColumns;
import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;

import android.app.ListActivity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * @author Louis Fazen (louis.fazen@gmail.com)
 */

// TODO: delete all the classes and other things I created for info_dialog...!
// TODO: delete the unused methods still sitting in view patient activity
// TODO: delete obs being loaded in this class (only need a few)
public class FormPriorityList extends ListActivity {

	// public static final int FILL_BLANK_FORM = 3;
	public static final int FILL_FORM = 3;
	public static final int VIEW_FORM_ONLY = 4;
	public static final int FILL_PRIORITY_FORM = 5;

	private ArrayList<Form> mTotalForms = new ArrayList<Form>();
	private static Patient mPatient;
	private static String mProviderId;
	private static HashMap<String, String> mInstanceValues = new HashMap<String, String>();
	private static final DateFormat COLLECT_INSTANCE_NAME_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
	private static ArrayList<Observation> mObservations = new ArrayList<Observation>();
	private static Element mFormNode;
	private Context mContext;
	private ArrayList<Integer> mSelectedFormIds = new ArrayList<Integer>();
	private ActivityLog mActivityLog;
	private Resources res;
	private MergeAdapter adapter = null;
	private FormAdapter completedAdapter = null;
	private FormAdapter savedAdapter = null;
	private FormAdapter priorityAdapter = null;
	private FormAdapter nonPriorityAdapter = null;
	private FormAdapter allFormsAdapter = null;
	public static final String EDIT_FORM = "edit_form";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mContext = this;
		res = this.getResources();
		setContentView(R.layout.example_cw_main);

		if (!FileUtils.storageReady()) {
			Toast.makeText(this, getString(R.string.error, R.string.storage_error), Toast.LENGTH_SHORT).show();
			finish();
		}

		getDownloadedForms();

		// We should always have a patient here, so getPatient
		String patientIdStr = getIntent().getStringExtra(Constants.KEY_PATIENT_ID);
		Integer patientId = Integer.valueOf(patientIdStr);
		mPatient = getPatient(patientId);

		Log.e("louis.fazen", "FormPriorityList.onCreate =" + patientId);
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		mProviderId = settings.getString(PreferencesActivity.KEY_PROVIDER, "0");

		// set Header View
		TextView textView = (TextView) findViewById(R.id.identifier_text);
		if (textView != null) {
			textView.setText(mPatient.getIdentifier());
		}

		textView = (TextView) findViewById(R.id.name_text);
		if (textView != null) {
			StringBuilder nameBuilder = new StringBuilder();
			nameBuilder.append(mPatient.getGivenName());
			nameBuilder.append(' ');
			nameBuilder.append(mPatient.getMiddleName());
			nameBuilder.append(' ');
			nameBuilder.append(mPatient.getFamilyName());
			textView.setText(nameBuilder.toString());
		}

		textView = (TextView) findViewById(R.id.birthdate_text);
		if (textView != null) {
			textView.setText(mPatient.getBirthdate());

		}

		ImageView imageView = (ImageView) findViewById(R.id.gender_image);
		if (imageView != null) {
			if (mPatient.getGender().equals("M")) {
				imageView.setImageResource(R.drawable.male_gray);
			} else if (mPatient.getGender().equals("F")) {
				imageView.setImageResource(R.drawable.female_gray);
			}
		}
	}

	protected void onListItemClick(ListView listView, View view, int position, long id) {

		ListAdapter selectedAdapter = adapter.getAdapter(position);
		Form f = (Form) getListAdapter().getItem(position);
		//
		// String s = f.getFormId().toString();
		// String s2 = String.valueOf(f.getFormId());
		// Log.e("louis.fazen", "string 1" + s);
		// Log.e("louis.fazen", "string 2" + s2);
		String type = null;
		int priority = FILL_FORM;
		if (mSelectedFormIds.contains(f.getFormId())) {
			priority = FILL_PRIORITY_FORM;
		}

		if (selectedAdapter == savedAdapter) {
			launchSavedFormEntry(f.getInstanceId(), priority);
			type = "Saved";
		} else if (selectedAdapter == completedAdapter) {
			launchFormViewOnly(f.getPath(), f.getFormId().toString());
			type = "Completed-Unsent";
		} else {
			launchFormEntry(f.getFormId().toString(), f.getName(), priority);
			type = "New Form";
		}

		SharedPreferences settings = getSharedPreferences("ChwSettings", MODE_PRIVATE);
		if (settings.getBoolean("IsLoggingEnabled", true)) {
			startActivityLog(f.getFormId().toString(), type);
		}

	}

	// method excerpted from the ViewPatientActivity (delete from there?)
	private void getDownloadedForms() {

		ClinicAdapter ca = new ClinicAdapter();

		ca.open();
		Cursor c = ca.fetchAllForms();

		if (c != null && c.getCount() >= 0) {

			mTotalForms.clear();
			int formIdIndex = c.getColumnIndex(ClinicAdapter.KEY_FORM_ID);
			int nameIndex = c.getColumnIndex(ClinicAdapter.KEY_NAME);
			int pathIndex = c.getColumnIndex(ClinicAdapter.KEY_PATH);

			if (c.getCount() > 0) {
				Form form;
				do {
					if (!c.isNull(formIdIndex)) {
						form = new Form();
						form.setFormId(c.getInt(formIdIndex));
						form.setName(c.getString(nameIndex));
						form.setPath(c.getString(pathIndex));
						mTotalForms.add(form);
					}
				} while (c.moveToNext());
			}
		}

		if (c != null)
			c.close();

		ca.close();
	}

	// below this line is all excerpted from the ViewPatientActivity (delete
	// from there?)
	private static String parseDate(String s) {
		SimpleDateFormat inputFormat = new SimpleDateFormat("MMM dd, yyyy");
		SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd");
		try {
			return outputFormat.format(inputFormat.parse(s));
		} catch (ParseException e) {
			return "";
		}
	}

	private static void traverseInstanceNodes(Element element) {

		// extract 'WEIGHT (KG)' from '5089^WEIGHT (KG)^99DCT'
		Pattern pattern = Pattern.compile("\\^.*\\^");
		// Log.e("FormPriorityList", "Element number of children= " +
		// element.getChildCount());
		// loop through all the children of this element
		for (int i = 0; i < element.getChildCount(); i++) {

			Element childElement = element.getElement(i);
			if (childElement != null) {

				String childName = childElement.getName();

				// patient id
				if (childName.equalsIgnoreCase("patient.patient_id")) {
					childElement.clear();
					childElement.addChild(0, org.kxml2.kdom.Node.TEXT, mPatient.getPatientId().toString());
				}
				if (childName.equalsIgnoreCase("patient.birthdate")) {
					childElement.clear();
					childElement.addChild(0, org.kxml2.kdom.Node.TEXT, parseDate(mPatient.getBirthdate()));
				}
				if (childName.equalsIgnoreCase("patient.family_name")) {
					childElement.clear();
					childElement.addChild(0, org.kxml2.kdom.Node.TEXT, mPatient.getFamilyName().toString());
				}
				if (childName.equalsIgnoreCase("patient.given_name")) {
					childElement.clear();
					childElement.addChild(0, org.kxml2.kdom.Node.TEXT, mPatient.getGivenName().toString());
				}

				if (childName.equalsIgnoreCase("patient.middle_name")) {
					childElement.clear();
					childElement.addChild(0, org.kxml2.kdom.Node.TEXT, mPatient.getMiddleName().toString());

				}
				if (childName.equalsIgnoreCase("patient.sex")) {
					childElement.clear();
					childElement.addChild(0, org.kxml2.kdom.Node.TEXT, mPatient.getGender().toString());

				}
				if (childName.equalsIgnoreCase("patient.medical_record_number")) {
					childElement.clear();
					childElement.addChild(0, org.kxml2.kdom.Node.TEXT, mPatient.getIdentifier().toString());

				}

				// provider id
				if (childName.equalsIgnoreCase("encounter.provider_id")) {
					childElement.clear();
					childElement.addChild(0, org.kxml2.kdom.Node.TEXT, mProviderId.toString());
				}

				if (childName.equalsIgnoreCase("date") || childName.equalsIgnoreCase("time")) {
					childElement.clear();

				}

				// value node
				if (childName.equalsIgnoreCase("value")) {

					childElement.clear();

					// parent of value node
					Element parentElement = ((Element) childElement.getParent());
					String parentConcept = parentElement.getAttributeValue("", "openmrs_concept");

					// match the text inside ^^
					String match = null;
					Matcher matcher = pattern.matcher(parentConcept);
					while (matcher.find()) {
						match = matcher.group(0).substring(1, matcher.group(0).length() - 1);
					}

					// write value into value n
					String value = mInstanceValues.get(match);
					if (value != null) {
						childElement.addChild(0, org.kxml2.kdom.Node.TEXT, value.toString());
					}
				}

				if (childElement.getChildCount() > 0) {
					// Log.e("FormPriorityList", "childElement number= " +
					// childElement.getChildCount());
					traverseInstanceNodes(childElement);
				}
			}
		}

	}

	private static void prepareInstanceValues() {

		for (int i = 0; i < mObservations.size(); i++) {
			Observation o = mObservations.get(i);
			mInstanceValues.put(o.getFieldName(), o.toString());

		}
	}

	private static int createFormInstance(String formPath, String jrFormId, String formname) {
		// reading the form
		Document doc = new Document();
		KXmlParser formParser = new KXmlParser();
		try {
			formParser.setInput(new FileReader(formPath));
			doc.parse(formParser);
		} catch (Exception e) {
			e.printStackTrace();
		}

		findFormNode(doc.getRootElement());
		if (mFormNode != null) {
			prepareInstanceValues();
			traverseInstanceNodes(mFormNode);
		} else {
			return -1;
		}

		// writing the instance file
		File formFile = new File(formPath);
		String formFileName = formFile.getName();
		String instanceName = "";
		if (formFileName.endsWith(".xml")) {
			instanceName = formFileName.substring(0, formFileName.length() - 4);
		} else {
			instanceName = jrFormId;
		}
		instanceName = instanceName + "_" + COLLECT_INSTANCE_NAME_DATE_FORMAT.format(new Date());

		String instancePath = FileUtils.INSTANCES_PATH + instanceName;
		(new File(instancePath)).mkdirs();
		String instanceFilePath = instancePath + "/" + instanceName + ".xml";
		File instanceFile = new File(instanceFilePath);

		KXmlSerializer instanceSerializer = new KXmlSerializer();
		try {
			instanceFile.createNewFile();
			FileWriter instanceWriter = new FileWriter(instanceFile);
			instanceSerializer.setOutput(instanceWriter);
			mFormNode.write(instanceSerializer);
			instanceSerializer.endDocument();
			instanceSerializer.flush();
			instanceWriter.close();

			// register into content provider
			ContentValues insertValues = new ContentValues();
			insertValues.put(InstanceColumns.DISPLAY_NAME, mPatient.getGivenName() + " " + mPatient.getFamilyName());
			insertValues.put(InstanceColumns.INSTANCE_FILE_PATH, instanceFilePath);
			insertValues.put(InstanceColumns.JR_FORM_ID, jrFormId);

			// louis.fazen is adding new variables to Instances.Db
			insertValues.put(InstanceColumns.PATIENT_ID, mPatient.getPatientId());
			insertValues.put(InstanceColumns.FORM_NAME, formname);
			insertValues.put(InstanceColumns.STATUS, "initialized");

			Uri insertResult = App.getApp().getContentResolver().insert(InstanceColumns.CONTENT_URI, insertValues);
			return Integer.valueOf(insertResult.getLastPathSegment());

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return -1;

	}

	private static void findFormNode(Element element) {

		// loop through all the children of this element
		for (int i = 0; i < element.getChildCount(); i++) {

			Element childElement = element.getElement(i);
			if (childElement != null) {

				String childName = childElement.getName();
				if (childName.equalsIgnoreCase("form")) {
					mFormNode = childElement;
				}

				if (childElement.getChildCount() > 0) {
					findFormNode(childElement);
				}
			}
		}

	}

	private void launchFormEntry(String jrFormId, String formname, int priority) {
		String formPath = null;
		int id = -1;
		try {
			// TODO: louis.fazen: Yaw's query seems inefficient--could just
			// return one row
			Cursor mCursor = App.getApp().getContentResolver().query(FormsColumns.CONTENT_URI, null, null, null, null);
			mCursor.moveToPosition(-1);
			while (mCursor.moveToNext()) {

				int dbid = mCursor.getInt(mCursor.getColumnIndex(FormsColumns._ID));
				String dbjrFormId = mCursor.getString(mCursor.getColumnIndex(FormsColumns.JR_FORM_ID));
				formPath = mCursor.getString(mCursor.getColumnIndex(FormsColumns.FORM_FILE_PATH));

				if (jrFormId.equalsIgnoreCase(dbjrFormId)) {
					id = dbid;
					break;

				}
			}
			if (mCursor != null) {
				mCursor.close();
			}

			if (id != -1) {

				// create instance in Collect
				int instanceId = createFormInstance(formPath, jrFormId, formname);

				if (instanceId != -1) {
					Intent intent = new Intent();
					intent.setComponent(new ComponentName("org.odk.collect.android", "org.odk.collect.android.activities.FormEntryActivity"));
					// TODO: louis.fazen changed this from ACTION_VIEW????
					intent.setAction(Intent.ACTION_EDIT);
					intent.setData(Uri.parse(InstanceColumns.CONTENT_URI + "/" + instanceId));
					startActivityForResult(intent, priority);
				} else {
					Uri formUri = ContentUris.withAppendedId(FormsColumns.CONTENT_URI, id);
					startActivityForResult(new Intent(Intent.ACTION_EDIT, formUri), priority);
				}

			}

		} catch (ActivityNotFoundException e) {
			showCustomToast(getString(R.string.error, getString(R.string.odk_collect_error)));
		}
	}

	private void startActivityLog(String formId, String formType) {
		String patientId = String.valueOf(mPatient.getPatientId());

		mActivityLog = new ActivityLog();
		mActivityLog.setProviderId(mProviderId);
		mActivityLog.setFormId(formId);
		mActivityLog.setPatientId(patientId);
		mActivityLog.setActivityStartTime();
		mActivityLog.setFormType(formType);

		if (mSelectedFormIds.contains(Integer.valueOf(formId))) {
			mActivityLog.setFormPriority("true");
		} else {
			mActivityLog.setFormPriority("false");
		}

	}

	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		Log.e("lef-onActivityResult", "onActivityResult called with request=" + requestCode + " result=" + resultCode + " intent=" + intent);
		// TODO: add the preference section in here:
		SharedPreferences settings = getSharedPreferences("ChwSettings", MODE_PRIVATE);
		if (settings.getBoolean("IsLoggingEnabled", true)) {
			mActivityLog.setActivityStopTime();
			ClinicAdapter ca = new ClinicAdapter();
			ca.open();
			ca.createActivityLog(mActivityLog);
			ca.close();
		}
		
		if (resultCode == RESULT_CANCELED) {
			return;
		}
		// // TODO: louis.fazen added RESULT_OK based on:
		// Collect.FormEntryActivity.finishReturnInstance() line1654
		// Uri instance = Uri.withAppendedPath(InstanceColumns.CONTENT_URI, id);
		// setResult(RESULT_OK, new Intent().setData(instance));
		// BUT ListPatientActivity does not include it

		if (resultCode == RESULT_OK) {

			if ((requestCode == FILL_FORM || requestCode == FILL_PRIORITY_FORM) && intent != null) {

				Uri u = intent.getData();
				String dbjrFormId = null;
				String displayName = null;
				String filePath = null;
				String status = null;

				Cursor mCursor = App.getApp().getContentResolver().query(u, null, null, null, null);
				mCursor.moveToPosition(-1);
				while (mCursor.moveToNext()) {
					status = mCursor.getString(mCursor.getColumnIndex(InstanceColumns.STATUS));
					dbjrFormId = mCursor.getString(mCursor.getColumnIndex(InstanceColumns.JR_FORM_ID));
					displayName = mCursor.getString(mCursor.getColumnIndex(InstanceColumns.DISPLAY_NAME));
					filePath = mCursor.getString(mCursor.getColumnIndex(InstanceColumns.INSTANCE_FILE_PATH));
					Log.e("lef-onActivityResult", "mCursor status:" + status + " FormId:" + dbjrFormId + " displayName:" + displayName + " filepath:" + filePath);
				}
				if (mCursor != null) {
					mCursor.close();
				}
				Log.e("lef-onActivityResult", "mCursor status:" + status + " InstanceProviderAPI=" + InstanceProviderAPI.STATUS_COMPLETE);

				ClinicAdapter ca = new ClinicAdapter();
				ca.open();
				// update saved form numbers in the patients table (by Patient
				// ID)... i.e. the per person form numbers
				// this allows for faster queries with only the need to look at
				// patient table to get all the patient info
				// otherwise would need to query both patients table and the
				// instances table,
				// and the priority list in obs just to get all the various
				// sources of patient data
				ca.updateSavedFormNumbersByPatientId(mPatient.getPatientId().toString());
				ca.updateSavedFormsListByPatientId(mPatient.getPatientId().toString());

				if (status.equalsIgnoreCase(InstanceProviderAPI.STATUS_COMPLETE)) {
					Log.e("lef-onActivityResult", "mCursor status:" + status + "=" + InstanceProviderAPI.STATUS_COMPLETE);
					FormInstance fi = new FormInstance();
					fi.setPatientId(mPatient.getPatientId()); // TODO: why not
																// take this
																// from the
																// Collect
																// Database as
																// well?
					fi.setFormId(Integer.parseInt(dbjrFormId));
					fi.setPath(filePath);
					fi.setStatus(ClinicAdapter.STATUS_UNSUBMITTED);
					Date date = new Date();
					date.setTime(System.currentTimeMillis());
					String dateString = "Completed: " + (new SimpleDateFormat("EEE, MMM dd, yyyy 'at' HH:mm").format(date));
					fi.setCompletionSubtext(dateString);

					ca.createFormInstance(fi, displayName);
					if (requestCode == FILL_PRIORITY_FORM) {
						ca.updatePriorityFormsByPatientId(mPatient.getPatientId().toString(), dbjrFormId);
					}
				}

				ca.close();

			}
			return;
		}
		super.onActivityResult(requestCode, resultCode, intent);
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

	// end of code imported from ViewPatientActivity

	// TODO: change this to pass a Parceable object from ViewPatientActivity
	private Patient getPatient(Integer patientId) {
		
		Patient p = null;
		ClinicAdapter ca = new ClinicAdapter();

		ca.open();
		Cursor c = ca.fetchPatient(patientId);

		if (c != null && c.getCount() > 0) {
			int patientIdIndex = c.getColumnIndex(ClinicAdapter.KEY_PATIENT_ID);
			int identifierIndex = c.getColumnIndex(ClinicAdapter.KEY_IDENTIFIER);
			int givenNameIndex = c.getColumnIndex(ClinicAdapter.KEY_GIVEN_NAME);
			int familyNameIndex = c.getColumnIndex(ClinicAdapter.KEY_FAMILY_NAME);
			int middleNameIndex = c.getColumnIndex(ClinicAdapter.KEY_MIDDLE_NAME);
			int birthDateIndex = c.getColumnIndex(ClinicAdapter.KEY_BIRTH_DATE);
			int genderIndex = c.getColumnIndex(ClinicAdapter.KEY_GENDER);

			// TODO: louis.fazen Probably delete the following... not needed
			int priorityIndex = c.getColumnIndexOrThrow(ClinicAdapter.KEY_PRIORITY_FORM_NUMBER);
			int priorityFormIndex = c.getColumnIndexOrThrow(ClinicAdapter.KEY_PRIORITY_FORM_NAMES);

			p = new Patient();
			p.setPatientId(c.getInt(patientIdIndex));
			p.setIdentifier(c.getString(identifierIndex));
			p.setGivenName(c.getString(givenNameIndex));
			p.setFamilyName(c.getString(familyNameIndex));
			p.setMiddleName(c.getString(middleNameIndex));
			p.setBirthDate(c.getString(birthDateIndex));
			p.setGender(c.getString(genderIndex));

			// TODO: louis.fazen probably delete the following... not needed
			p.setPriorityNumber(c.getInt(priorityIndex));
			p.setPriorityForms(c.getString(priorityFormIndex));
			
			if (c.getInt(priorityIndex) > 0) {
				p.setPriority(true);
			}
			
			Log.e("louis.fazen", "FormPriorityList.getPatient =" + c.getInt(patientIdIndex));
		}

		if (c != null) {
			c.close();
		}
		ca.close();
		
		return p;
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (mPatient != null) {
			Log.e("louis.fazen", "FormPriorityList.onResume mPatient is Not null and Id=" + mPatient.getPatientId());
			getPriorityForms();
			refreshView();
		}
	}

	private void getPriorityForms() {
		int patientId = mPatient.getPatientId();
		mSelectedFormIds = new ArrayList<Integer>();
		Log.e("louis.fazen", "FormPriorityList.getPriorityForms is called");
		ClinicAdapter ca = new ClinicAdapter();

		ca.open();
		Cursor c = ca.fetchPriorityFormIdByPatientId(patientId);
		Log.e("louis.fazen", "FormPriorityList.getPriorityForms is c NUll?");
		if (c != null && c.getCount() > 0) {
			Log.e("louis.fazen", "FormPriorityList.getPriorityForms is c IS NOT NULL?");
			int valueIntIndex = c.getColumnIndex(ClinicAdapter.KEY_VALUE_INT);
			do {
				mSelectedFormIds.add(c.getInt(valueIntIndex));
				Log.e("louis.fazen", "getProrityForms Add Loop mSelectedFormIds.size(): " + mSelectedFormIds.size());
			} while (c.moveToNext());
		}

		if (c != null) {
			c.close();
		}
		ca.close();
		
	}

	private void refreshView() {

		// 1. COMPLETED FORMS: gather the saved forms from the Clinic.db
		ClinicAdapter ca = new ClinicAdapter();
		ca.open();
		boolean selectedIds = false;
		if (mSelectedFormIds != null && mSelectedFormIds.size() > 0) {
			selectedIds = true;
		}
		
		// Venn diagram where we have completed and selected subsets of Forms
		// CompletedSelectedFormIds is their intersection and always smaller
		// than both
		ArrayList<Integer> completedSelectedFormIds = new ArrayList<Integer>();
		ArrayList<Form> completedForms = new ArrayList<Form>();

		Cursor c = ca.fetchCompletedByPatientId(mPatient.getPatientId());
		if (c != null && c.getCount() > 0) {
			int formIdIndex = c.getColumnIndex(ClinicAdapter.KEY_FORM_ID);
			int instanceIdIndex = c.getColumnIndex(ClinicAdapter.KEY_ID);
			int displayNameIndex = c.getColumnIndex(ClinicAdapter.KEY_FORMINSTANCE_DISPLAY);
			int pathIndex = c.getColumnIndex(ClinicAdapter.KEY_PATH);
			int nameIndex = c.getColumnIndex(ClinicAdapter.KEY_FORM_NAME);
			int dateIndex = c.getColumnIndex(ClinicAdapter.KEY_FORMINSTANCE_SUBTEXT);

			if (c.getCount() > 0) {
				Form form;
				do {
					if (!c.isNull(instanceIdIndex)) {
						form = new Form();
						form.setFormId(c.getInt(formIdIndex));
						form.setInstanceId(c.getInt(instanceIdIndex));
						form.setDisplayName(c.getString(displayNameIndex));
						form.setName(c.getString(nameIndex));
						form.setPath(c.getString(pathIndex));
						form.setDisplaySubtext(c.getString(dateIndex));

						completedForms.add(form);
						if (selectedIds && mSelectedFormIds.contains(form.getFormId())) {
							completedSelectedFormIds.add(c.getInt(formIdIndex));
						}
						Log.e("louis.fazen", "refreshView.setPathIndex: " + c.getString(pathIndex));
					}
				} while (c.moveToNext());
			}

		}

		if (c != null) {
			c.close();
		}
		completedAdapter = new FormAdapter(this, R.layout.saved_instances, completedForms, false);

		// 2 and 3. SEPARATE PRIORITY & NONPRIORITY forms from the recent
		// download:
		Form[] allItems = new Form[mTotalForms.size()];
		Form[] starItems = new Form[mSelectedFormIds.size() - completedSelectedFormIds.size()];
		Form[] nonStarItems = new Form[(mTotalForms.size() - mSelectedFormIds.size()) + completedSelectedFormIds.size()];

		if (selectedIds) {
			int preferredCounter = 0;
			int nonPreferredCounter = 0;
			Log.e("louis.fazen", "priorityformloop...?");
			for (Form form : mTotalForms) {
				// Only non-completed priority forms are differentiated from
				// general form pool
				if (mSelectedFormIds.contains(form.getFormId()) && !completedSelectedFormIds.contains(form.getFormId())) {
					starItems[preferredCounter++] = form;
				} else
					// all other forms are added to the additional list
					// (including saved & completed forms)
					nonStarItems[nonPreferredCounter++] = form;
			}
			Log.e("louis.fazen", "preferredcounter: " + preferredCounter);
			Log.e("louis.fazen", "nonPreferredcounter: " + nonPreferredCounter);
		} else {
			Log.e("louis.fazen", "else loop");
			// if no priority forms, then no separation
			int formcounter = 0;
			for (Form form : mTotalForms) {
				allItems[formcounter++] = form;
			}
			Log.e("louis.fazen", "formcounter: " + formcounter);
		}

		// 4. SAVED FORMS: gather the saved forms from Collect Instances.Db
		String selection = InstanceColumns.STATUS + "=? and " + InstanceColumns.PATIENT_ID + "=?";
		String selectionArgs[] = { InstanceProviderAPI.STATUS_INCOMPLETE, String.valueOf(mPatient.getPatientId()) };
		Cursor csave = App.getApp().getContentResolver()
				.query(InstanceColumns.CONTENT_URI, new String[] { InstanceColumns._ID, InstanceColumns.JR_FORM_ID, InstanceColumns.DISPLAY_NAME, InstanceColumns.DISPLAY_SUBTEXT, InstanceColumns.FORM_NAME, InstanceColumns.INSTANCE_FILE_PATH }, selection, selectionArgs, null);
		ArrayList<Form> savedForms = new ArrayList<Form>();

		if (csave != null) {
			csave.moveToFirst();
		}

		if (csave != null && csave.getCount() >= 0) {
			int instanceIdIndex = csave.getColumnIndex(InstanceColumns._ID);
			int formIdIndex = csave.getColumnIndex(InstanceColumns.JR_FORM_ID);
			int subtextIndex = csave.getColumnIndex(InstanceColumns.DISPLAY_SUBTEXT);
			int displayNameIndex = csave.getColumnIndex(InstanceColumns.DISPLAY_NAME);
			int pathIndex = csave.getColumnIndex(InstanceColumns.INSTANCE_FILE_PATH);
			int nameIndex = csave.getColumnIndex(InstanceColumns.FORM_NAME);

			if (csave.getCount() > 0) {
				Form form;
				do {
					if (!csave.isNull(instanceIdIndex)) {
						form = new Form();
						form.setInstanceId(csave.getInt(instanceIdIndex));
						form.setFormId(csave.getInt(formIdIndex));
						form.setName(csave.getString(nameIndex));
						form.setPath(csave.getString(pathIndex));
						form.setDisplaySubtext(csave.getString(subtextIndex));
						form.setDisplayName(csave.getString(displayNameIndex));
						savedForms.add(form);
					}
				} while (csave.moveToNext());
			}
		}

		if (csave != null)
			csave.close();

		ca.close();
		savedAdapter = new FormAdapter(this, R.layout.saved_instances, savedForms, false);

		// end of gathering data... add everything to the view:
		adapter = new MergeAdapter();

		// priority...
		if (starItems.length > 0) {
			adapter.addView(buildSectionLabel(getString(R.string.priority_form_section)));
			ArrayList<Form> formList = new ArrayList<Form>(Arrays.asList(starItems));
			priorityAdapter = new FormAdapter(this, R.layout.default_form_item, formList, true);
			adapter.addAdapter(priorityAdapter);
		}

		// saved...
		if (!savedForms.isEmpty()) {
			adapter.addView(buildSectionLabel(getString(R.string.saved_form_section)));
			if (savedForms.size() < 4) {
				adapter.addAdapter(savedAdapter);
			} else {
				adapter.addView(formSummaryView(savedForms.size()));
			}
		}

		// completed...
		if (!completedForms.isEmpty()) {
			adapter.addView(buildSectionLabel(getString(R.string.completed_form_section)));
			adapter.addAdapter(completedAdapter);
		}

		// add non-priority forms if there is a priority forms section
		if (starItems.length > 0) {
			adapter.addView(buildSectionLabel(getString(R.string.nonpriority_form_section)));
			ArrayList<Form> formList = new ArrayList<Form>(Arrays.asList(nonStarItems));
			nonPriorityAdapter = new FormAdapter(this, R.layout.default_form_item, formList, false);
			adapter.addAdapter(nonPriorityAdapter);

		} else {
			// if no priority / non-priority divide, then add all forms
			adapter.addView(buildSectionLabel(getString(R.string.all_form_section)));
			ArrayList<Form> formList = new ArrayList<Form>(Arrays.asList(allItems));
			allFormsAdapter = new FormAdapter(this, R.layout.default_form_item, formList, false);
			adapter.addAdapter(allFormsAdapter);
		}

		// finally:
		setListAdapter(adapter);

		// Log.e("louis.fazen", "PriorityAdapter=" + priorityAdapter);
		// Log.e("louis.fazen", "SavedAdapter=" + savedAdapter);
		// Log.e("louis.fazen", "completedAdapter=" + completedAdapter);
		// Log.e("louis.fazen", "NonPriorityAdapter=" + nonPriorityAdapter);
		// Log.e("louis.fazen", "allFormsAdapter=" + allFormsAdapter);
	}

	// private FormAdapter buildFormsList(Form[] forms, boolean priority) {
	// ArrayList<Form> formList = new ArrayList<Form>(Arrays.asList(forms));
	// Collections.shuffle(formList);
	// FormAdapter formAdapter = new FormAdapter(this,
	// R.layout.default_form_item, formList, priority);
	// return (formAdapter);
	// }

	private View buildSectionLabel(String sectionlabel) {
		View v;
		LayoutInflater vi = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		v = vi.inflate(R.layout.section_label, null);
		TextView textView = (TextView) v.findViewById(R.id.name_text);
		ImageView sectionImage = (ImageView) v.findViewById(R.id.section_image);

		textView.setText(sectionlabel);

		if (sectionlabel == getString(R.string.priority_form_section)) {
			v.setBackgroundResource(R.color.priority);
			sectionImage.setImageResource(R.drawable.ic_priority);

		} else if (sectionlabel == getString(R.string.saved_form_section)) {
			v.setBackgroundResource(R.color.saved);
			sectionImage.setImageDrawable(res.getDrawable(R.drawable.ic_saved));

		} else if (sectionlabel == getString(R.string.completed_form_section)) {
			v.setBackgroundResource(R.color.completed);
			sectionImage.setImageDrawable(res.getDrawable(R.drawable.ic_completed));

		} else {
			v.setBackgroundResource(R.color.medium_gray);
			sectionImage.setImageDrawable(res.getDrawable(R.drawable.ic_additional));
		}

		return (v);
	}

	private View formSummaryView(int forms) {

		View formsSummary;
		LayoutInflater vi = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		formsSummary = vi.inflate(R.layout.priority_form_summary, null);
		formsSummary.setClickable(true);

		formsSummary.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {

				Intent i = new Intent(getApplicationContext(), ViewSavedForms.class);
				i.putExtra(Constants.KEY_PATIENT_ID, mPatient.getPatientId().toString());
				startActivity(i);
			}
		});

		ImageView priorityArrow = (ImageView) formsSummary.findViewById(R.id.arrow_image);
		ImageView priorityImage = (ImageView) formsSummary.findViewById(R.id.priority_image);
		RelativeLayout priorityBlock = (RelativeLayout) formsSummary.findViewById(R.id.priority_block);
		TextView priorityNumber = (TextView) formsSummary.findViewById(R.id.priority_number);
		TextView allFormTitle = (TextView) formsSummary.findViewById(R.id.all_form_title);
		TextView formNames = (TextView) formsSummary.findViewById(R.id.form_names);

		if (priorityArrow != null && formNames != null && allFormTitle != null) {
			priorityImage.setImageDrawable(res.getDrawable(R.drawable.ic_gray_block));
			priorityBlock.setPadding(0, 0, 0, 0);

			priorityArrow.setImageResource(R.drawable.arrow_gray);
			formNames.setVisibility(View.GONE);
			allFormTitle.setTextColor(res.getColor(R.color.dark_gray));

			allFormTitle.setText("View All Saved Forms");

			if (priorityNumber != null && priorityImage != null) {
				priorityNumber.setText(String.valueOf(forms));
				priorityImage.setVisibility(View.VISIBLE);
			}
		}

		return (formsSummary);
	}

	// NB... this method = method in AllFormList, but != ViewCompletedForms.launchFormViewOnly() 
	//	b/c it picks up data from a different DB when creating Form Object
	// View CompletedForms queries the Collect.Instances Db whereas this picks up instances from the Clinic.Instances Db
	private void launchFormViewOnly(String uriString, String formId) {
		Intent intent = new Intent();
		intent.setComponent(new ComponentName("org.odk.collect.android", "org.odk.collect.android.activities.FormEntryActivity"));

		// TODO: Could be improved... using ACTION_VIEW, etc.
		intent.setAction(Intent.ACTION_VIEW);
		intent.putExtra(EDIT_FORM, false);
		intent.putExtra("path_name", uriString);
		intent.putExtra("form_id", formId);
		startActivityForResult(intent, VIEW_FORM_ONLY);
	}
	
	

	private void launchSavedFormEntry(int instanceId, int priority) {
		Intent intent = new Intent();
		intent.setComponent(new ComponentName("org.odk.collect.android", "org.odk.collect.android.activities.FormEntryActivity"));
		intent.setAction(Intent.ACTION_EDIT);
		intent.setData(Uri.parse(InstanceColumns.CONTENT_URI + "/" + instanceId));
		startActivityForResult(intent, priority);
	}

}
