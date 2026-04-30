package com.flipphoneguy.totp;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Minimal FileProvider replacement so we don't have to bundle androidx.core.
 * Serves files under getExternalFilesDir(...) and getCacheDir() via content://
 * URIs of the form: content://com.flipphoneguy.totp.files/&lt;tag&gt;/&lt;path&gt;
 */
public class SharedFileProvider extends ContentProvider {
    public static final String AUTHORITY = "com.flipphoneguy.totp.files";

    private static final int CODE_EXT = 1;
    private static final int CODE_CACHE = 2;
    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
    static {
        MATCHER.addURI(AUTHORITY, "ext/*", CODE_EXT);
        MATCHER.addURI(AUTHORITY, "ext/*/*", CODE_EXT);
        MATCHER.addURI(AUTHORITY, "ext/*/*/*", CODE_EXT);
        MATCHER.addURI(AUTHORITY, "cache/*", CODE_CACHE);
    }

    public static Uri getUriForFile(Context ctx, File f) {
        File extRoot = ctx.getExternalFilesDir(null);
        File cacheRoot = ctx.getCacheDir();
        String abs = f.getAbsolutePath();
        if (extRoot != null && abs.startsWith(extRoot.getAbsolutePath())) {
            String rel = abs.substring(extRoot.getAbsolutePath().length());
            if (rel.startsWith("/")) rel = rel.substring(1);
            return Uri.parse("content://" + AUTHORITY + "/ext/" + Uri.encode(rel, "/"));
        }
        if (abs.startsWith(cacheRoot.getAbsolutePath())) {
            String rel = abs.substring(cacheRoot.getAbsolutePath().length());
            if (rel.startsWith("/")) rel = rel.substring(1);
            return Uri.parse("content://" + AUTHORITY + "/cache/" + Uri.encode(rel, "/"));
        }
        throw new IllegalArgumentException("File not in shareable dir: " + abs);
    }

    private File resolve(Uri uri) {
        String path = uri.getPath();
        if (path == null) return null;
        if (path.startsWith("/ext/")) {
            File root = getContext().getExternalFilesDir(null);
            if (root == null) return null;
            return new File(root, path.substring(5));
        }
        if (path.startsWith("/cache/")) {
            return new File(getContext().getCacheDir(), path.substring(7));
        }
        return null;
    }

    @Override public boolean onCreate() { return true; }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = resolve(uri);
        if (f == null || !f.exists()) throw new FileNotFoundException(String.valueOf(uri));
        int m = ParcelFileDescriptor.MODE_READ_ONLY;
        if ("w".equals(mode) || "rw".equals(mode)) m = ParcelFileDescriptor.MODE_READ_WRITE;
        return ParcelFileDescriptor.open(f, m);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File f = resolve(uri);
        if (f == null) return null;
        String[] cols = projection != null ? projection :
            new String[]{ OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE };
        MatrixCursor c = new MatrixCursor(cols);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = f.getName();
            else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = f.length();
        }
        c.addRow(row);
        return c;
    }

    @Override
    public String getType(Uri uri) {
        File f = resolve(uri);
        if (f == null) return null;
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return "application/octet-stream";
        String ext = name.substring(dot + 1).toLowerCase();
        if ("apk".equals(ext)) return "application/vnd.android.package-archive";
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return TextUtils.isEmpty(mime) ? "application/octet-stream" : mime;
    }

    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
