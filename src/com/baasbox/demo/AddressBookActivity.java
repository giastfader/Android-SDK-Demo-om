package com.baasbox.demo;

import java.util.ArrayList;
import java.util.List;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.Bundle;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.TextView;

import com.baasbox.android.BAASBoxClientException;
import com.baasbox.android.BAASBoxException;
import com.baasbox.android.BAASBoxResult;
import com.baasbox.android.BAASBoxServerException;
import com.baasbox.demo.util.AlertUtils;

public class AddressBookActivity extends ListActivity implements
		ActionMode.Callback {

	private static final int MENUITEM_REFRESH = 1;
	private static final int MENUITEM_ADD = 2;
	private static final int MENUITEM_DELETE = 3;

	private ListTask listTask;
	private AddTask addTask;
	private ArrayAdapter<Person> adapter;
	private MenuItem refreshMenuItem;
	private int selectedItem = -1;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.adapter = new Adapter(this);
		this.setListAdapter(adapter);

		this.getListView().setLongClickable(true);
		this.getListView().setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
		this.getListView().setOnItemLongClickListener(
				new OnItemLongClickListener() {
					@Override
					public boolean onItemLongClick(AdapterView<?> adapter,
							View view, int position, long id) {
						if (selectedItem != -1) {
							return false;
						}

						selectedItem = position;
						startActionMode(AddressBookActivity.this);
						view.setSelected(true);
						return true;
					}
				});
	}

	@Override
	protected void onResume() {
		super.onResume();
		refresh();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		refreshMenuItem = menu.add(Menu.NONE, MENUITEM_REFRESH, Menu.NONE,
				"Refresh");
		refreshMenuItem.setIcon(R.drawable.ic_menu_refresh);
		refreshMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		if (listTask != null && listTask.getStatus() == Status.RUNNING)
			refreshMenuItem.setActionView(R.layout.view_menuitem_refresh);

		MenuItem add = menu.add(Menu.NONE, MENUITEM_ADD, Menu.NONE, "Add");
		add.setIcon(R.drawable.ic_menu_add);
		add.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			onBackPressed();
			break;
		case MENUITEM_ADD:
			onClickAddPerson();
			break;
		case MENUITEM_REFRESH:
			refresh();
			break;
		default:
			return super.onOptionsItemSelected(item);
		}

		return false;
	}

	@Override
	public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
		switch (item.getItemId()) {
		case MENUITEM_DELETE:
			delete(selectedItem);
			mode.finish();
			break;
		default:
			return super.onContextItemSelected(item);
		}

		return true;
	}

	@Override
	public boolean onCreateActionMode(ActionMode mode, Menu menu) {
		Person person = adapter.getItem(selectedItem);
		mode.setTitle(person.name);

		MenuItem delete = menu.add(ContextMenu.NONE, MENUITEM_DELETE,
				ContextMenu.NONE, "Delete");
		delete.setIcon(R.drawable.ic_menu_delete);
		delete.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

		return true;
	}

	@Override
	public void onDestroyActionMode(ActionMode mode) {
		selectedItem = -1;
	}

	@Override
	public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
		return false;
	}

	protected void delete(int position) {
		Person person = adapter.getItem(position);
		adapter.remove(person);
		new DeleteTask().execute(person);
	}

	private void refresh() {
		listTask = new ListTask();
		listTask.execute();
	}

	private void onClickAddPerson() {
		View layout = getLayoutInflater().inflate(R.layout.dialog_add, null);
		final EditText nameText = (EditText) layout.findViewById(R.id.name);
		final EditText phoneText = (EditText) layout.findViewById(R.id.phone);
		final EditText noteText = (EditText) layout.findViewById(R.id.note);
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setView(layout);
		builder.setNegativeButton("Cancel", null);
		builder.setPositiveButton("Add", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				String name = nameText.getText().toString().trim();
				String phone = phoneText.getText().toString().trim();
				String note = noteText.getText().toString();
				if (name.length() > 0 && phone.length() > 0)
					addPerson(name, phone,note);
			}
		});

		builder.create().show();
	}

	protected void addPerson(String name, String phone, String note) {
		addTask = new AddTask();
		addTask.execute(name, phone,note);
	}

	public void onPersonAdded(BAASBoxResult<Person> result) {
		try {
			adapter.add(result.get());
			adapter.notifyDataSetChanged();
		} catch (BAASBoxClientException e) {
			AlertUtils.showErrorAlert(this, e);
		} catch (BAASBoxServerException e) {
			AlertUtils.showErrorAlert(this, e);
		} catch (BAASBoxException e) {
			AlertUtils.showErrorAlert(this, e);
		}
	}

	protected void onListReceived(BAASBoxResult<List<Person>> result) {
		try {
			 List<Person> list = result.get();
			adapter.clear();

			for (Person p: list)
				adapter.add(p);

			adapter.notifyDataSetChanged();
		} catch (BAASBoxClientException e) {
			AlertUtils.showErrorAlert(this, e);
		} catch (BAASBoxServerException e) {
			AlertUtils.showErrorAlert(this, e);
		} catch (BAASBoxException e) {
			AlertUtils.showErrorAlert(this, e);
		} 

	}
	
	protected void onPersonDeleted(BAASBoxResult<Void> result) {
		try {
			result.get();
		} catch (BAASBoxClientException e) {
			AlertUtils.showErrorAlert(this, e);
		} catch (BAASBoxServerException e) {
			AlertUtils.showErrorAlert(this, e);
		} catch (BAASBoxException e) {
			AlertUtils.showErrorAlert(this, e);
		}
	}

	public class ListTask extends
			AsyncTask<Void, Void, BAASBoxResult<List<Person>>> {

		@Override
		protected void onPreExecute() {
			if (refreshMenuItem != null)
				refreshMenuItem.setActionView(R.layout.view_menuitem_refresh);
		}

		@Override
		protected BAASBoxResult<List<Person>> doInBackground(Void... params) {
			
		//	return  new Person().getAll(App.bbox);
		//alternative notation/implementation	
			return App.bbox.getAll(Person.class);
		}

		@Override
		protected void onPostExecute(BAASBoxResult<List<Person>> result) {
			if (refreshMenuItem != null)
				refreshMenuItem.setActionView(null);
			onListReceived(result);
		}
	}

	public class AddTask extends
			AsyncTask<String, Void, BAASBoxResult<Person>> {

		@Override
		protected BAASBoxResult<Person> doInBackground(String... params) {
			Person person = new Person();
			person.name = params[2];
			person.phone = params[1];
			person.notes = params[0];
			return person.save(App.bbox);
		}

		@Override
		protected void onPostExecute(BAASBoxResult<Person> result) {
			onPersonAdded(result);
		}
	}

	public class DeleteTask extends
			AsyncTask<Person, Void, BAASBoxResult<Void>> {
		
		@Override
		protected BAASBoxResult<Void> doInBackground(Person... param) {
		//delete old way	
		//	return App.bbox.deleteDocument("address-book", param[0].id);
			return param[0].delete(App.bbox);
		}
		
		@Override
		protected void onPostExecute(BAASBoxResult<Void> result) {
			onPersonDeleted(result);
		}
	}

	public class Adapter extends ArrayAdapter<Person> {

		public Adapter(Context context) {
			super(context, android.R.layout.simple_list_item_2,
					new ArrayList<Person>());
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			View view = convertView;

			if (view == null) {
				LayoutInflater inflater = (LayoutInflater) getContext()
						.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				view = inflater.inflate(android.R.layout.simple_list_item_2,
						null);

				Tag tag = new Tag();
				tag.text1 = (TextView) view.findViewById(android.R.id.text1);
				tag.text2 = (TextView) view.findViewById(android.R.id.text2);
				view.setTag(tag);
			}

			Tag tag = (Tag) view.getTag();
			Person entry = getItem(position);
			tag.text1.setText(entry.name);
			tag.text2.setText(entry.phone);

			return view;
		}

	}

	protected static class Tag {

		public TextView text1;
		public TextView text2;

	}

}
