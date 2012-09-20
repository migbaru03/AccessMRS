package org.odk.clinic.android.activities;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.odk.clinic.android.R;
import org.odk.clinic.android.adapters.CertificateAdapter;
import org.odk.clinic.android.adapters.MergeAdapter;
import org.odk.clinic.android.openmrs.Certificate;
import org.odk.clinic.android.utilities.FileUtils;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Environment;
import android.widget.Toast;

/**
 * @author Louis Fazen (louis.fazen@gmail.com)
 */

public class ClientAuthenticationActivity extends ManageSSLActivity {
	private Context mContext;
	private static final String TAG = ClientAuthenticationActivity.class.getSimpleName();

	@Override
	protected void onResume() {
		mContext = this;
		mLocalStoreFileName = "mykeystore.bks";
		mLocalStoreResourceId = R.raw.mykeystore;
		mStoreString = "key";
		mStoreTitleString = "Key";
		mImportFormat = "BKS";
		mLocalStoreFile = new File(getFilesDir(), mLocalStoreFileName);
		if(!mLocalStoreFile.exists())
			FileUtils.setupDefaultSslStore(R.raw.mytruststore);
		super.onResume();
	}

	@Override
	protected void showStoreItems() {
		new AsyncTask<Void, Void, Void>() {
			private MergeAdapter mAdapter;

			@Override
			protected Void doInBackground(Void... params) {
				// Local Keys:
				mAdapter = new MergeAdapter();
				System.setProperty("javax.net.ssl.trustStore", mLocalStoreFile.getAbsolutePath());
				CertificateAdapter localAdapter = new CertificateAdapter(mContext, R.layout.key_item, getStoreFiles(), true);
				mAdapter.addView(buildSectionLabel(R.layout.certificate_label, "Local Keys"));
				mAdapter.addAdapter(localAdapter);
				return null;
			}

			@Override
			protected void onPostExecute(Void result) {
				super.onPostExecute(result);
				setListAdapter(mAdapter);
			}

		}.execute();

	}

	@Override
	protected ArrayList<Certificate> getStoreFiles() {
		ArrayList<Certificate> androidKeys = new ArrayList<Certificate>();
		List<File> allFiles = FileUtils.findAllFiles(getFilesDir().getAbsolutePath(), "keystore.bks");
		try {
			Certificate c;
			for (File file : allFiles) {
				c = new Certificate();
				String keyS = file.getName();
				c.setAlias(keyS);
				c.setO(keyS.substring(0, keyS.lastIndexOf(".")));
				c.setName(keyS.substring(0, keyS.lastIndexOf(".")));
				Date date = new Date();
				date.setTime(file.lastModified());
				String dateString = new SimpleDateFormat("EEE, MMM dd, yyyy 'at' HH:mm").format(date);
				c.setDate(dateString);
				androidKeys.add(c);
			}

		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		return androidKeys;
	}

	@Override
	protected void remove(String name) {
		File file = new File(getFilesDir(), name);
		if (file.delete())
			Toast.makeText(mContext, "Deleted 1 keystore file", Toast.LENGTH_SHORT);
		refreshView();
	}

	@Override
	protected void addFromExternalStroage() {
		String[] certs = listKeyStoreFiles();
		// int certsAdded = 0;
		if (certs.length > 1) {
			Toast.makeText(mContext, "You can only have one keystore file", Toast.LENGTH_SHORT);
		} else {

			for (String certFilename : certs) {
				File keyFile = new File(Environment.getExternalStorageDirectory(), certFilename);

				try {
					FileInputStream in = new FileInputStream(keyFile);
					FileOutputStream out = new FileOutputStream(mLocalStoreFile);
					byte[] buff = new byte[1024];
					int read = 0;

					try {
						while ((read = in.read(buff)) > 0) {
							out.write(buff, 0, read);
						}
					} finally {
						in.close();

						out.flush();
						out.close();
					}
					//delete so we don't leave keys hanging around...!
					keyFile.delete();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}

				// certsAdded++;
			}
		}

		Toast.makeText(mContext, "Replaced the current keystore file", Toast.LENGTH_SHORT);
		refreshView();
	}

	private static String[] listKeyStoreFiles() {
		File externalStorage = Environment.getExternalStorageDirectory();
		FilenameFilter ff = new FilenameFilter() {

			@Override
			public boolean accept(File dir, String filename) {
				if (filename.equals("mykeystore.bks"))
					return true;
				else
					return false;
			}
		};

		return externalStorage.list(ff);
	}
}
