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
package it.feio.android.omninotes.models;

import it.feio.android.omninotes.R;
import it.feio.android.omninotes.async.ThumbnailLoaderTask;
import it.feio.android.omninotes.db.DbHelper;
import it.feio.android.omninotes.utils.Constants;

import java.util.List;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;


public class NoteAdapter extends ArrayAdapter<Note> {
	
	private final String GHOST_CHAR = "*";	
	private final int THUMBNAIL_SIZE = 200;	

	private final Activity mActivity;
	private final List<Note> values;
	private SparseBooleanArray selectedItems = new SparseBooleanArray();
	private boolean expandedView;
	private int layout;
	private LayoutInflater inflater;
	

	public NoteAdapter(Activity activity, int layout, List<Note> values) {
		super(activity, R.layout.note_layout_expanded, values);
		this.mActivity = activity;
		this.values = values;		
		this.layout = layout;
		
		expandedView = layout == R.layout.note_layout_expanded;
		inflater = (LayoutInflater) mActivity.getSystemService(mActivity.LAYOUT_INFLATER_SERVICE);
	}

	@SuppressLint("NewApi")
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		
		Note note = values.get(position);
		
		NoteAdapterViewHolder holder;
	    if (convertView == null) {
	    	convertView = inflater.inflate(layout, parent, false);
	    	
	    	holder = new NoteAdapterViewHolder();
	    	
	    	holder.tagMarker = convertView.findViewById(R.id.tag_marker);
	    	holder.cardLayout = convertView.findViewById(R.id.card_layout);

	    	holder.title = (TextView) convertView.findViewById(R.id.note_title);
	    	holder.content = (TextView) convertView.findViewById(R.id.note_content);
	    	holder.date = (TextView) convertView.findViewById(R.id.note_date);
	    	
	    	holder.archiveIcon = (ImageView) convertView.findViewById(R.id.archivedIcon);
	    	holder.locationIcon = (ImageView) convertView.findViewById(R.id.locationIcon);
	    	holder.alarmIcon = (ImageView) convertView.findViewById(R.id.alarmIcon);
	    	holder.lockedIcon = (ImageView) convertView.findViewById(R.id.lockedIcon);

	    	holder.attachmentThumbnail = (ImageView) convertView.findViewById(R.id.attachmentThumbnail);	    	
	    	
	    	convertView.setTag(holder);
	    } else {
	        holder = (NoteAdapterViewHolder) convertView.getTag();
	    }
		
		// Defining title and content texts	
		String titleText, contentText;
		if (note.getTitle().length() > 0) {
			titleText = note.getTitle();
			contentText = note.getContent();
		} else {
			String[] arr = note.getContent().split(System.getProperty("line.separator"));
			titleText = arr[0];
			contentText = arr.length > 1 ? arr[1] : "";
		}
		
		// Masking title and content string if note is locked
		if (note.isLocked()) {
			// This checks if a part of content is used as title and should be partially masked 
			if (!note.getTitle().equals(titleText)) {	
				titleText = titleText.substring(0, 2) + titleText.substring(2).replaceAll(".", GHOST_CHAR);
			}
			contentText = contentText.replaceAll(".", GHOST_CHAR);
		}

		// Setting note title	
		holder.title.setText(titleText);
		
		// Setting note content	
		holder.content.setText(contentText);
		holder.content.setVisibility(View.VISIBLE);
		

		// Evaluates the archived state...
		holder.archiveIcon.setVisibility(note.isArchived() ? View.VISIBLE : View.GONE);
		// ... the location
		holder.locationIcon.setVisibility(note.getLongitude() != null && note.getLongitude() != 0 ? View.VISIBLE : View.GONE);
		// ... the presence of an alarm
		holder.alarmIcon.setVisibility(note.getAlarm() != null ? View.VISIBLE : View.GONE);
		// ... the locked with password state	
		holder.lockedIcon.setVisibility(note.isLocked() ? View.VISIBLE : View.GONE);
		
		// Choosing which date must be shown depending on sorting criteria
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mActivity);
		String sort_column = prefs.getString(Constants.PREF_SORTING_COLUMN, "");
//		String time_format = prefs.getBoolean("settings_hours_format", true) ? Constants.DATE_FORMAT_SHORT
//				: Constants.DATE_FORMAT_SHORT_12;
		// Creation
		if (sort_column.equals(DbHelper.KEY_CREATION)) {
			holder.date.setText(mActivity.getString(R.string.creation) + " " + note.getCreationShort(mActivity));
		}
		// Reminder
		else if (sort_column.equals(DbHelper.KEY_ALARM)) {
			String alarmShort = note.getAlarmShort(mActivity);
			
			if (alarmShort.length() == 0) {
				holder.date.setText(R.string.no_reminder_set);
			} else {
				holder.date.setText(mActivity.getString(R.string.alarm_set_on) + " "
					+ note.getAlarmShort(mActivity));
			}
		}
		// Others
		else {
			holder.date.setText(mActivity.getString(R.string.last_update) + " "
					+ note.getLastModificationShort(mActivity));
		}

		// Highlighted if is part of multiselection of notes. Remember to search for child with card ui
		if (selectedItems.get(position)) {
			holder.cardLayout.setBackgroundColor(mActivity.getResources().getColor(
					R.color.list_bg_selected));
		} else {
			restoreDrawable(note, holder.cardLayout, holder);
		}
		

		// Attachment thumbnail
		if (expandedView) {
			// If note is locked or without attachments nothing is shown
			if (note.isLocked() || note.getAttachmentsList().size() == 0) {
				holder.attachmentThumbnail.setImageResource(0);
				holder.attachmentThumbnail.setVisibility(View.GONE);
			}
			// Otherwise...
			else {
				for (Attachment mAttachment : note.getAttachmentsList()) {
						ThumbnailLoaderTask task = new ThumbnailLoaderTask(mActivity, holder.attachmentThumbnail, THUMBNAIL_SIZE);
						if (Build.VERSION.SDK_INT >= 11) {
							task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, mAttachment);
						} else {
							task.execute(mAttachment);
						}
						holder.attachmentThumbnail.setVisibility(View.VISIBLE);
						break;
				}
			}
		}
		
		return convertView;
	}

	public SparseBooleanArray getSelectedItems() {
		return selectedItems;
	}

	public void addSelectedItem(Integer selectedItem) {
		this.selectedItems.put(selectedItem, true);
	}

	public void removeSelectedItem(Integer selectedItem) {
		this.selectedItems.delete(selectedItem);
	}

	public void clearSelectedItems() {
		this.selectedItems.clear();
	}
	

	
	public void restoreDrawable(Note note, View v) {
		restoreDrawable(note, v, null);
	}
	
	
	public void restoreDrawable(Note note, View v, NoteAdapterViewHolder holder) {
		final int paddingBottom = v.getPaddingBottom(), paddingLeft = v.getPaddingLeft();
	    final int paddingRight = v.getPaddingRight(), paddingTop = v.getPaddingTop();
	    v.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);
	    colorNote(note, v, holder);
	}
	

	@SuppressWarnings("unused")
	private void colorNote(Note note, View v) {
		colorNote(note, v, null);
	}

	/**
	 * Color of tag marker if note is tagged a function is active in preferences
	 * @param note
	 * @param rowView
	 */
	private void colorNote(Note note, View v, NoteAdapterViewHolder holder) {

		// Checking preference
		if (PreferenceManager.getDefaultSharedPreferences(mActivity).getBoolean("settings_enable_tag_marker", true)) {

			// Resetting transparent color to the view
			v.setBackgroundColor(Color.parseColor("#00000000"));

			// If tag is set the color will be applied on the appropriate target
			if (note.getTag() != null && note.getTag().getColor() != null) {
				if (PreferenceManager.getDefaultSharedPreferences(mActivity).getBoolean(
						"settings_enable_tag_marker_full", false)) {
					v.setBackgroundColor(Integer.parseInt(note.getTag().getColor()));
				} else {
					if (holder != null) {
						holder.tagMarker.setBackgroundColor(Integer.parseInt(note.getTag().getColor()));
					} else {
						v.findViewById(R.id.tag_marker).setBackgroundColor(Integer.parseInt(note.getTag().getColor()));
					}
				}
			}
		}
	}

}
