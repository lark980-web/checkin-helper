package com.checkin.helper;

import java.io.File;
import java.io.FileNotFoundException;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

/**
 * 极简文件分享 Provider(不依赖 androidx):
 * 把 getExternalFilesDir 下的 .txt 日志/页面文字通过 content:// 分享出去,
 * 解决 Android 11+ 文件管理进不去 Android/data 导致用户拿不到文件的问题。
 */
public class FileShareProvider extends ContentProvider {

    public static Uri uriFor(android.content.Context ctx, File f) {
        return new Uri.Builder().scheme("content")
                .authority(ctx.getPackageName() + ".fileshare")
                .appendPath(f.getName()).build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    private File fileFor(String name) {
        if (name == null) return null;
        try {
            String safe = new File(name).getName(); // 去掉路径, 防穿越
            File dir = getContext().getExternalFilesDir(null);
            if (dir != null) {
                File f = new File(dir, safe);
                if (f.isFile() && f.exists()) return f;
            }
            File cache = getContext().getCacheDir();
            if (cache != null) {
                File f = new File(cache, safe);
                if (f.isFile() && f.exists()) return f;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        File f = fileFor(uri.getLastPathSegment());
        if (f == null) throw new FileNotFoundException(uri.toString());
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return "text/plain";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File f = fileFor(uri.getLastPathSegment());
        MatrixCursor c = new MatrixCursor(
                new String[]{"_display_name", "_size"});
        if (f != null) c.addRow(new Object[]{f.getName(), f.length()});
        return c;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        return 0;
    }
}
