package com.example.android.notepad;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import com.example.android.notepad.NotePad;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/**
 * 存储扩展元数据（分类、附件）的轻量工具，不改动原有 NotePadProvider 表结构。
 */
class NoteMetadataStore {

    private static final String PREF_NAME = "note_metadata";
    private static final String KEY_DATA = "data";
    private final Context context;

    static class Attachment {
        final String type;
        final String uri;

        Attachment(String type, String uri) {
            this.type = type;
            this.uri = uri;
        }
    }

    private static class NoteExtras {
        String category;
        List<Attachment> attachments = new ArrayList<Attachment>();
    }

    private final SharedPreferences prefs;
    private JSONObject backing;

    NoteMetadataStore(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_DATA, "{}");
        try {
            backing = new JSONObject(raw);
        } catch (JSONException e) {
            backing = new JSONObject();
        }
    }

    String getCategory(long noteId) {
        return getExtras(noteId).category;
    }

    List<Attachment> getAttachments(long noteId) {
        return new ArrayList<Attachment>(getExtras(noteId).attachments);
    }

    void setCategory(long noteId, String category) {
        if (category != null && category.length() > 0) {
            addCategoryName(category);
        }
        NoteExtras extras = getExtras(noteId);
        extras.category = category;
        putExtras(noteId, extras);

        // 【修正点 1：通知 Content Resolver 笔记元数据已更改】
        // 这将触发 NotesList 的刷新（如果它正在监听 Notes.CONTENT_URI）
        Uri noteUri = NotePad.Notes.CONTENT_URI;
        try {
            noteUri = Uri.withAppendedPath(NotePad.Notes.CONTENT_URI, String.valueOf(noteId));
        } catch (Exception ignored) {
        }
        context.getContentResolver().notifyChange(noteUri, null);
    }

    void addAttachment(long noteId, String type, String uri) {
        NoteExtras extras = getExtras(noteId);
        extras.attachments.add(new Attachment(type, uri));
        putExtras(noteId, extras);
    }

    List<String> getAllCategories() {
        Set<String> result = new HashSet<String>();
        ContentResolver cr = context.getContentResolver();
        Cursor c = null;
        try {
            c = cr.query(NotePad.Categories.CONTENT_URI,
                    new String[]{NotePad.Categories.COLUMN_NAME_NAME},
                    null, null, null);
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(NotePad.Categories.COLUMN_NAME_NAME);
                do {
                    String name = c.getString(idx);
                    if (name != null && name.length() > 0) {
                        result.add(name);
                    }
                } while (c.moveToNext());
            }
        } finally {
            if (c != null) c.close();
        }
        // 兼容旧数据：从 SharedPreferences 读取
        JSONArray names = backing.optJSONArray("_categories");
        if (names != null) {
            for (int i = 0; i < names.length(); i++) {
                String name = names.optString(i, null);
                if (name != null && name.length() > 0) {
                    result.add(name);
                }
            }
        }
        JSONArray keys = backing.names();
        if (keys != null) {
            for (int i = 0; i < keys.length(); i++) {
                String key = keys.optString(i, null);
                if (key == null || "_categories".equals(key)) continue;
                try {
                    JSONObject obj = backing.getJSONObject(key);
                    String cat = obj.optString("category", null);
                    if (cat != null && cat.length() > 0) {
                        result.add(cat);
                    }
                } catch (JSONException ignored) {
                }
            }
        }
        return new ArrayList<String>(result);
    }

    void addCategoryName(String name) {
        if (name == null || name.length() == 0) return;
        // 确保 ContentValues 中只包含 NAME 字段
        ContentValues values = new ContentValues();
        values.put(NotePad.Categories.COLUMN_NAME_NAME, name);
        try {
            context.getContentResolver().insert(NotePad.Categories.CONTENT_URI, values);

            // 【修正点 2：通知 Content Resolver 分类列表已更改】
            // 这有助于刷新需要分类列表（如分类选择对话框）的组件
            context.getContentResolver().notifyChange(NotePad.Categories.CONTENT_URI, null);

        } catch (Exception ignored) {
            // ignore duplicate/insert errors
        }
        // 兼容旧存储，继续写 prefs
        try {
            JSONArray arr = backing.optJSONArray("_categories");
            if (arr == null) {
                arr = new JSONArray();
            }
            for (int i = 0; i < arr.length(); i++) {
                if (name.equals(arr.optString(i))) {
                    prefs.edit().putString(KEY_DATA, backing.toString()).apply();
                    return;
                }
            }
            arr.put(name);
            backing.put("_categories", arr);
            prefs.edit().putString(KEY_DATA, backing.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    private NoteExtras getExtras(long noteId) {
        NoteExtras extras = new NoteExtras();
        String key = String.valueOf(noteId);
        if (backing.has(key)) {
            try {
                JSONObject obj = backing.getJSONObject(key);
                extras.category = obj.optString("category", null);
                JSONArray arr = obj.optJSONArray("attachments");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject a = arr.optJSONObject(i);
                        if (a == null) continue;
                        String type = a.optString("type", "");
                        String uri = a.optString("uri", "");
                        if (uri.length() > 0) {
                            extras.attachments.add(new Attachment(type, uri));
                        }
                    }
                }
            } catch (JSONException ignored) {
                // 返回空 extras
            }
        }
        return extras;
    }

    private void putExtras(long noteId, NoteExtras extras) {
        try {
            JSONObject obj = new JSONObject();
            if (extras.category != null) {
                obj.put("category", extras.category);
            }
            JSONArray arr = new JSONArray();
            for (Attachment att : extras.attachments) {
                JSONObject item = new JSONObject();
                item.put("type", att.type);
                item.put("uri", att.uri);
                arr.put(item);
            }
            obj.put("attachments", arr);
            backing.put(String.valueOf(noteId), obj);
            prefs.edit().putString(KEY_DATA, backing.toString()).apply();
        } catch (JSONException ignored) {
            // ignore bad write
        }
    }
}