
package com.example.android.notepad;

import com.example.android.notepad.NotePad;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;
import android.text.TextUtils;
import android.graphics.Color;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Collections;



public class NotesList extends ListActivity {

    // For logging and debugging
    private static final String TAG = "NotesList";

    private Cursor mCursor;

    /**
     * The columns needed by the cursor adapter
     */
    private static final String[] PROJECTION = new String[] {
            NotePad.Notes._ID, // 0
            NotePad.Notes.COLUMN_NAME_TITLE, // 1
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, // 2
    };

    /** The index of the title column */
    private static final int COLUMN_INDEX_TITLE = 1;
    /** The index of the modification date column */
    private static final int COLUMN_INDEX_MODIFIED = 2;

    private static final int ACTIVITY_REQUEST_CODE_EDIT = 1;

    private NotesAdapter mAdapter;
    private NoteMetadataStore mMetadataStore;
    private long mLastClickedId = AdapterView.INVALID_ROW_ID;
    private boolean mMultiSelectMode = false;
    private final Set<Long> mSelectedIds = new HashSet<Long>();
    private final SimpleDateFormat mDateFormat =
            new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.CHINA);
    private final Map<String, Boolean> mCategoryExpanded = new HashMap<String, Boolean>();
    private String mCurrentKeyword = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);

        Intent intent = getIntent();

        if (intent.getData() == null) {
            intent.setData(NotePad.Notes.CONTENT_URI);
        }

        getListView().setOnCreateContextMenuListener(this);
        getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                enterMultiSelect();
                toggleSelection(id);
                return true;
            }
        });

        mMetadataStore = new NoteMetadataStore(this);

        loadNotes(null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate menu from XML resource
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_options_menu, menu);

        // Generate any additional actions that can be performed on the
        // overall list.  In a normal install, there are no additional
        // actions found here, but this allows other applications to extend
        // our menu with their own actions.
        Intent intent = new Intent(null, getIntent().getData());
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);

        MenuItem searchItem = menu.findItem(R.id.menu_search);
        if (searchItem != null) {
            SearchView searchView = (SearchView) searchItem.getActionView();
            searchView.setQueryHint(getString(R.string.search_hint));
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    loadNotes(query);
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    loadNotes(newText);
                    return true;
                }
            });
            searchView.setOnCloseListener(new SearchView.OnCloseListener() {
                @Override
                public boolean onClose() {
                    loadNotes(null);
                    return false;
                }
            });
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        ClipboardManager clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);

        MenuItem mPasteItem = menu.findItem(R.id.menu_paste);

        if (clipboard.hasPrimaryClip()) {
            mPasteItem.setEnabled(true);
        } else {
            mPasteItem.setEnabled(false);
        }

        final boolean haveItems = getListAdapter().getCount() > 0;

        if (haveItems) {

            Uri uri = ContentUris.withAppendedId(getIntent().getData(), getSelectedItemId());

            Intent[] specifics = new Intent[1];

            specifics[0] = new Intent(Intent.ACTION_EDIT, uri);

            MenuItem[] items = new MenuItem[1];

            Intent intent = new Intent(null, uri);

            intent.addCategory(Intent.CATEGORY_ALTERNATIVE);

            menu.addIntentOptions(
                Menu.CATEGORY_ALTERNATIVE,
                Menu.NONE,
                Menu.NONE,
                null,
                specifics,
                intent,
                Menu.NONE,
                items
            );
                if (items[0] != null) {

                    // Sets the Edit menu item shortcut to numeric "1", letter "e"
                    items[0].setShortcut('1', 'e');
                }
            } else {
                // If the list is empty, removes any existing alternative actions from the menu
                menu.removeGroup(Menu.CATEGORY_ALTERNATIVE);
            }

        MenuItem toggleMulti = menu.findItem(R.id.menu_toggle_multi_select);
        MenuItem applyCategory = menu.findItem(R.id.menu_apply_category);
        MenuItem clearCategory = menu.findItem(R.id.menu_clear_category);
        if (mMultiSelectMode) {
            toggleMulti.setTitle(R.string.menu_exit_multi_select);
            applyCategory.setVisible(true);
            clearCategory.setVisible(true);
        } else {
            toggleMulti.setTitle(R.string.menu_multi_select);
            applyCategory.setVisible(false);
            clearCategory.setVisible(false);
        }

        // Displays the menu
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_add) {
            startActivityForResult(new Intent(Intent.ACTION_INSERT, getIntent().getData()), ACTIVITY_REQUEST_CODE_EDIT);
            return true;
        } else if (item.getItemId() == R.id.menu_paste) {
            startActivityForResult(new Intent(Intent.ACTION_INSERT, getIntent().getData()), ACTIVITY_REQUEST_CODE_EDIT);
            return true;
        } else if (item.getItemId() == R.id.menu_toggle_multi_select) {
            if (mMultiSelectMode) {
                exitMultiSelect();
            } else {
                enterMultiSelect();
            }
            return true;
        } else if (item.getItemId() == R.id.menu_apply_category) {
            if (mSelectedIds.isEmpty()) {
                Toast.makeText(this, R.string.toast_no_selection, Toast.LENGTH_SHORT).show();
            } else {
                showCategoryDialogForSelection();
            }
            return true;
        } else if (item.getItemId() == R.id.menu_create_category) {
            showCreateCategoryDialog();
            return true;
        } else if (item.getItemId() == R.id.menu_clear_category) {
            if (mSelectedIds.isEmpty()) {
                Toast.makeText(this, R.string.toast_no_selection, Toast.LENGTH_SHORT).show();
            } else {
                clearCategoryForSelection();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {

        AdapterView.AdapterContextMenuInfo info;

        try {
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return;
        }

        RowItem row = mAdapter.getItem(info.position);
        if (row.type != RowItem.TYPE_NOTE) {
            return;
        }

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_context_menu, menu);

        menu.setHeaderTitle(row.title);

        Intent intent = new Intent(null, Uri.withAppendedPath(getIntent().getData(), 
                                        Integer.toString((int) info.id) ));
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);
    }


    @Override
    public boolean onContextItemSelected(MenuItem item) {
        // The data from the menu item.
        AdapterView.AdapterContextMenuInfo info;

        try {
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);

            return false;
        }
        RowItem row = mAdapter.getItem(info.position);
        if (row.type != RowItem.TYPE_NOTE) {
            return false;
        }
        Uri noteUri = ContentUris.withAppendedId(getIntent().getData(), row.noteId);
        int id = item.getItemId();
        if (id == R.id.context_open) {
            startActivityForResult(new Intent(Intent.ACTION_EDIT, noteUri), ACTIVITY_REQUEST_CODE_EDIT);
            return true;
        } else if (id == R.id.context_copy) { //BEGIN_INCLUDE(copy)
            ClipboardManager clipboard = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);

            clipboard.setPrimaryClip(ClipData.newUri(   // new clipboard item holding a URI
                    getContentResolver(),               // resolver to retrieve URI info
                    "Note",                             // label for the clip
                    noteUri));                          // the URI

            return true;
        } else if (id == R.id.context_delete) {
            getContentResolver().delete(
                    noteUri,  // The URI of the provider
                    null,     // No where clause is needed, since only a single note ID is being
                    // passed in.
                    null      // No where clause is used, so no where arguments are needed.
            );

            // Returns to the caller and skips further processing.
            return true;
        }
        return super.onContextItemSelected(item);
    }

    /**
     * Called when an Activity you launched returns its result.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // 检查是否是我们请求的编辑/新建 Activity
        if (requestCode == ACTIVITY_REQUEST_CODE_EDIT) {
            // 检查子 Activity 是否返回 RESULT_OK (即成功保存)
            if (resultCode == RESULT_OK) {
                // 如果成功，强制重新加载数据
                loadNotes(mCurrentKeyword);
            }
        }
    }


    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {

        RowItem item = mAdapter.getItem(position);
        if (item.type == RowItem.TYPE_CATEGORY) {
            boolean expanded = mCategoryExpanded.containsKey(item.category)
                    ? mCategoryExpanded.get(item.category) : item.expanded;
            mCategoryExpanded.put(item.category, !expanded);
            loadNotes(mCurrentKeyword);
            return;
        }

        if (mMultiSelectMode) {
            toggleSelection(item.noteId);
            return;
        }

        Uri uri = ContentUris.withAppendedId(getIntent().getData(), item.noteId);
        String action = getIntent().getAction();
        if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {
            setResult(RESULT_OK, new Intent().setData(uri));
        } else {
            startActivity(new Intent(Intent.ACTION_EDIT, uri));
        }
        mLastClickedId = item.noteId;
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mCursor != null) {
            stopManagingCursor(mCursor);
            mCursor = null; // 置空，确保 loadNotes 会重新创建游标
        }

        loadNotes(mCurrentKeyword);
    }

    private void loadNotes(String keyword) {
        mCurrentKeyword = keyword;
        String selection = null;
        String[] selectionArgs = null;
        if (keyword != null && keyword.length() > 0) {
            selection = NotePad.Notes.COLUMN_NAME_TITLE + " LIKE ? OR " +
                    NotePad.Notes.COLUMN_NAME_NOTE + " LIKE ?";
            String like = "%" + keyword + "%";
            selectionArgs = new String[] { like, like };
        }

        /*
         * 【修正 1：游标赋值】将 managedQuery 的结果赋值给成员变量 mCursor，
         * 并由其管理生命周期。
         */
        mCursor = managedQuery(
                getIntent().getData(),
                PROJECTION,
                selection,
                selectionArgs,
                NotePad.Notes.DEFAULT_SORT_ORDER
        );

        List<RowItem> notes = new ArrayList<RowItem>();
        boolean hasCategory = false;

        /*
         * 【修正 2：使用成员变量 mCursor 遍历数据】
         */
        if (mCursor != null && mCursor.moveToFirst()) {
            do {
                long id = mCursor.getLong(0);
                String title = mCursor.getString(COLUMN_INDEX_TITLE);
                long modified = mCursor.getLong(COLUMN_INDEX_MODIFIED);
                String category = mMetadataStore.getCategory(id);
                if (!TextUtils.isEmpty(category)) {
                    hasCategory = true;
                }
                RowItem item = new RowItem();
                item.type = RowItem.TYPE_NOTE;
                item.noteId = id;
                item.title = title;
                item.modified = modified;
                item.category = category;
                notes.add(item);
            } while (mCursor.moveToNext());
        }

        List<RowItem> display = new ArrayList<RowItem>();
        Map<String, List<RowItem>> grouped = new HashMap<String, List<RowItem>>();
        for (RowItem n : notes) {
            String cat = TextUtils.isEmpty(n.category) ? getString(R.string.category_none) : n.category;
            if (!grouped.containsKey(cat)) grouped.put(cat, new ArrayList<RowItem>());
            grouped.get(cat).add(n);
        }
        List<String> createdCats = mMetadataStore.getAllCategories();
        if (!createdCats.isEmpty()) {
            hasCategory = true;
            for (String cat : createdCats) {
                if (!grouped.containsKey(cat)) {
                    grouped.put(cat, new ArrayList<RowItem>());
                }
            }
        }

        if (hasCategory) {
            List<String> categories = new ArrayList<String>(grouped.keySet());
            Collections.sort(categories);
            for (String cat : categories) {
                boolean expanded = mCategoryExpanded.containsKey(cat) ? mCategoryExpanded.get(cat) : true;
                RowItem header = new RowItem();
                header.type = RowItem.TYPE_CATEGORY;
                header.category = cat;
                header.expanded = expanded;
                display.add(header);
                if (expanded) {
                    // 恢复原始逻辑，直接添加，依赖于数据库查询时的排序
                    display.addAll(grouped.get(cat));
                }
            }
        } else {
            display.addAll(notes);
        }

        /*
         * 【修正 3：刷新适配器】确保适配器被设置或更新。
         */
        if (mAdapter == null) {
            mAdapter = new NotesAdapter(display);
            setListAdapter(mAdapter);
        } else {
            mAdapter.setData(display);
        }
    }



    private void enterMultiSelect() {
        mMultiSelectMode = true;
    }

    private void exitMultiSelect() {
        mMultiSelectMode = false;
        mSelectedIds.clear();
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    private void toggleSelection(long noteId) {
        if (!mMultiSelectMode) {
            enterMultiSelect();
        }
        if (mSelectedIds.contains(noteId)) {
            mSelectedIds.remove(noteId);
        } else {
            mSelectedIds.add(noteId);
        }
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    private void applySelectionStyle(View view, boolean isSelected) {
        view.setBackgroundColor(isSelected ? Color.parseColor("#FFE0B2") : Color.TRANSPARENT);
    }

    private void showCategoryDialogForSelection() {
        if (mSelectedIds.isEmpty()) {
            Toast.makeText(this, R.string.toast_no_selection, Toast.LENGTH_SHORT).show();
            return;
        }
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_category_title)
                .setView(input)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String value = input.getText().toString().trim();
                        String finalValue = value.length() == 0 ? null : value;
                        for (Long id : mSelectedIds) {
                            mMetadataStore.setCategory(id, finalValue);
                        }
                        Toast.makeText(NotesList.this, R.string.menu_set_category, Toast.LENGTH_SHORT).show();
                        exitMultiSelect();
                        loadNotes(mCurrentKeyword);
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // do nothing
                    }
                })
                .show();
    }

    private void clearCategoryForSelection() {
        for (Long id : mSelectedIds) {
            mMetadataStore.setCategory(id, null);
        }
        Toast.makeText(this, R.string.menu_clear_category, Toast.LENGTH_SHORT).show();
        exitMultiSelect();
        loadNotes(mCurrentKeyword);
    }

    private void showCategoryDialogForNote(final long noteId) {
        if (noteId == AdapterView.INVALID_ROW_ID) {
            Toast.makeText(this, R.string.toast_select_note_first, Toast.LENGTH_SHORT).show();
            return;
        }
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        String current = mMetadataStore.getCategory(noteId);
        if (!TextUtils.isEmpty(current)) {
            input.setText(current);
            input.setSelection(current.length());
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_category_title)
                .setView(input)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String value = input.getText().toString().trim();
                        mMetadataStore.setCategory(noteId, value.length() == 0 ? null : value);
                        Toast.makeText(NotesList.this, R.string.menu_set_category, Toast.LENGTH_SHORT).show();
                        loadNotes(mCurrentKeyword);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static class RowItem {
        static final int TYPE_CATEGORY = 0;
        static final int TYPE_NOTE = 1;

        int type;
        long noteId;
        String title;
        long modified;
        String category;
        boolean expanded;
    }

    private class NotesAdapter extends BaseAdapter {
        private List<RowItem> mData;
        private final LayoutInflater mInflater;

        // 确保 mSelectedIds 和 mDateFormat 在 NotesList 外部类中已定义并可用
        // 假设 mSelectedIds 是 Set<Long>, mDateFormat 是 DateFormat

        NotesAdapter(List<RowItem> data) {
            this.mData = data;
            this.mInflater = LayoutInflater.from(NotesList.this);
        }

        void setData(List<RowItem> data) {
            this.mData = data;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mData == null ? 0 : mData.size();
        }

        @Override
        public RowItem getItem(int position) {
            return mData.get(position);
        }

        @Override
        public long getItemId(int position) {
            RowItem item = mData.get(position);
            return item.type == RowItem.TYPE_NOTE ? item.noteId : -position - 1;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            // 假设 RowItem.TYPE_CATEGORY = 0, RowItem.TYPE_NOTE = 1
            return mData.get(position).type;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            RowItem item = getItem(position);

            if (item.type == RowItem.TYPE_CATEGORY) {
                CategoryHolder holder;

                if (convertView == null || !(convertView.getTag() instanceof CategoryHolder)) {
                    convertView = mInflater.inflate(R.layout.category_item, parent, false);
                    holder = new CategoryHolder();
                    holder.arrow = (TextView) convertView.findViewById(R.id.text_arrow);
                    holder.title = (TextView) convertView.findViewById(R.id.text_category_title);
                    convertView.setTag(holder);
                } else {
                    holder = (CategoryHolder) convertView.getTag();
                }

                holder.arrow.setText(item.expanded ? "v" : ">");
                holder.title.setText(item.category);
                applySelectionStyle(convertView, false);

            } else {
                NoteHolder holder;

                if (convertView == null || !(convertView.getTag() instanceof NoteHolder)) {
                    convertView = mInflater.inflate(R.layout.noteslist_item, parent, false);
                    holder = new NoteHolder();

                    holder.title = (TextView) convertView.findViewById(android.R.id.text1);
                    holder.timestamp = (TextView) convertView.findViewById(R.id.text_timestamp);

                    convertView.setTag(holder);
                } else {
                    holder = (NoteHolder) convertView.getTag();
                }

                boolean selected = mSelectedIds.contains(item.noteId);
                String title = item.title;
                if (selected) {
                    title = "[已选] " + title;
                }

                if (holder.title != null) {
                    holder.title.setText(title);
                }

                if (holder.timestamp != null) {
                    holder.timestamp.setText(mDateFormat.format(new Date(item.modified))); // 约 807 行
                }

                applySelectionStyle(convertView, selected);
            }
            return convertView;
        }

        class NoteHolder {
            TextView title;
            TextView timestamp;
        }

        class CategoryHolder {
            TextView arrow;
            TextView title;
        }

    }

    private void showCreateCategoryDialog() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_create_category)
                .setView(input)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String value = input.getText().toString().trim();
                        if (value.length() == 0) {
                            return;
                        }
                        mMetadataStore.addCategoryName(value);
                        mCategoryExpanded.put(value, true);
                        loadNotes(mCurrentKeyword);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
