/*******************************************************************************
 * Copyright 2014 Federico Iosue (federico.iosue@gmail.com)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package it.feio.android.omninotes.async;

import it.feio.android.omninotes.R;
import it.feio.android.omninotes.models.ONStyle;
import it.feio.android.omninotes.utils.Constants;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

import de.keyboardsurfer.android.widget.crouton.Crouton;

public class UpdaterTask extends AsyncTask<String, Void, Void> {

	private final WeakReference<Activity> mActivity;
	String url;
	private String packageName;
	private boolean promptUpdate = false;

	public UpdaterTask(Activity mActivity) {
		this.mActivity = new WeakReference<Activity>(mActivity);
	}

	@Override
	protected void onPreExecute() {
		String packageName = mActivity.get().getApplicationContext().getPackageName();
		url = Constants.PS_METADATA_FETCHER_URL + Constants.PLAY_STORE_URL
				+ packageName;
		super.onPreExecute();
	}

	@Override
	protected Void doInBackground(String... params) {

		String appData = getAppData();
		try {
			// Creation of json object
			JSONObject json = new JSONObject(appData);

			promptUpdate = isVersionUpdated(json.getString("softwareVersion"));

			// Getting from preferences last update check
			SharedPreferences prefs = PreferenceManager
					.getDefaultSharedPreferences(mActivity.get());

			long now = System.currentTimeMillis();
			if (promptUpdate
					&& now > prefs.getLong(Constants.PREF_LAST_UPDATE_CHECK, 0)
							+ Constants.UPDATE_MIN_FREQUENCY) {
				promptUpdate = true;
				prefs.edit().putLong(Constants.PREF_LAST_UPDATE_CHECK, now)
						.commit();
			} else {
				promptUpdate = false;
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	private void promptUpdate() {
		
		// Check if activity is still finishing
		if (mActivity.get() == null || mActivity.get().isFinishing()) {
			return;
		}
		
		// Confirm dialog creation
		final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
				mActivity.get());
		alertDialogBuilder
				.setCancelable(false)
				.setMessage(R.string.new_update_available)
				.setPositiveButton(R.string.update,
						new DialogInterface.OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog, int id) {
								if(!isGooglePlayAvailable()) {
									Crouton.makeText(mActivity.get(), R.string.feature_not_available_on_this_device, ONStyle.ALERT).show();
									return;
								}
								mActivity.get().startActivity(new Intent(
										Intent.ACTION_VIEW, Uri
												.parse("market://details?id="
														+ packageName)));
								dialog.dismiss();
							}
						})
				.setNegativeButton(R.string.not_now,
						new DialogInterface.OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog, int id) {
								dialog.dismiss();
							}
						});
		AlertDialog alertDialog = alertDialogBuilder.create();
		alertDialog.show();
	}

	
	@Override
	protected void onPostExecute(Void result) {		
		if (promptUpdate)
			promptUpdate();
	}

	
	/**
	 * Fecth application data from internet
	 * @return
	 */
	public String getAppData() {
		StringBuilder sb = new StringBuilder();
		
		if (mActivity.get() != null) {
			packageName = mActivity.get().getApplicationContext().getPackageName();

			try {
				// get URL content
				URL url = new URL(Constants.PS_METADATA_FETCHER_URL
						+ Constants.PLAY_STORE_URL + packageName);
				URLConnection conn = url.openConnection();
	
				// open the stream and put it into BufferedReader
				BufferedReader br = new BufferedReader(new InputStreamReader(
						conn.getInputStream()));
	
				String inputLine;
	
				while ((inputLine = br.readLine()) != null) {
					sb.append(inputLine);
				}
	
			} catch (MalformedURLException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	
		return sb.toString();
	}

	/**
	 * Checks parsing "android:versionName" if app has been updated
	 * 
	 * @throws NameNotFoundException
	 */
	private boolean isVersionUpdated(String playStoreVersion)
			throws NameNotFoundException {
		
		boolean result = false;

		// Retrieval of installed app version
		PackageInfo pInfo = mActivity.get().getPackageManager().getPackageInfo(
				mActivity.get().getPackageName(), 0);
		String installedVersion = pInfo.versionName;

		// Parsing version string to obtain major.minor.point (excluding eventually beta)
		String[] playStoreVersionArray = playStoreVersion.split("b")[0].split("\\.");
		String[] installedVersionArray = installedVersion.split("b")[0].split("\\.");	
		
		// Versions strings are converted into integer
		String playStoreVersionString = playStoreVersionArray[0];
		String installedVersionString = installedVersionArray[0];
		for (int i=1; i < playStoreVersionArray.length; i++) {
			playStoreVersionString += String.format("%02d", Integer.parseInt(playStoreVersionArray[i]));
			installedVersionString += String.format("%02d", Integer.parseInt(installedVersionArray[i]));
		}
		
		// And then compared
		if (  Integer.parseInt(playStoreVersionString) > Integer.parseInt(installedVersionString) ) {
			result = true;
		}
		
		// And then compared again to check if we're out of Beta
		else if (  Integer.parseInt(playStoreVersionString) == Integer.parseInt(installedVersionString) 
					&& playStoreVersion.split("b").length == 1 && installedVersion.split("b").length == 2) {
			result = true;
		}
		
		return result;
	}
	
	
	
	/**
	 * Checks Google Play availability
	 * @return
	 */
	private boolean isGooglePlayAvailable() {
        boolean googlePlayStoreInstalled;
        int val= GooglePlayServicesUtil.isGooglePlayServicesAvailable(mActivity.get());
        googlePlayStoreInstalled = val == ConnectionResult.SUCCESS;
        return googlePlayStoreInstalled;
    }
}
