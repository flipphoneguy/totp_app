package com.flipphoneguy.totp;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;

public final class UpdateChecker {
    private static final String API_URL =
        "https://api.github.com/repos/flipphoneguy/totp_app/releases/latest";

    private UpdateChecker() {}

    public static String currentVersion(Context ctx) {
        try {
            PackageInfo pi = ctx.getPackageManager()
                .getPackageInfo(ctx.getPackageName(), 0);
            return pi.versionName;
        } catch (Exception e) {
            return "0.0.0";
        }
    }

    /**
     * Checks GitHub for a newer release. Runs network call on a background
     * thread and shows result on the UI thread.
     */
    public static void check(final Context ctx) {
        Toast.makeText(ctx, "Checking for updates…", Toast.LENGTH_SHORT).show();
        final Handler ui = new Handler(Looper.getMainLooper());

        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    HttpURLConnection conn =
                        (HttpURLConnection) new URL(API_URL).openConnection();
                    conn.setRequestProperty("Accept", "application/vnd.github+json");
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    int status = conn.getResponseCode();
                    if (status != 200) {
                        showToast(ui, ctx, "Update check failed (" + status + ").");
                        return;
                    }

                    StringBuilder sb = new StringBuilder();
                    Reader r = new InputStreamReader(conn.getInputStream(), "UTF-8");
                    char[] buf = new char[2048];
                    int n;
                    while ((n = r.read(buf)) != -1) sb.append(buf, 0, n);
                    r.close();

                    JSONObject obj = new JSONObject(sb.toString());
                    String tag = obj.optString("tag_name", "").trim();
                    final String latest = stripV(tag);
                    final String current = currentVersion(ctx);

                    final String apkUrl = pickApkUrl(obj.optJSONArray("assets"));

                    if (latest.isEmpty()) {
                        showToast(ui, ctx, "No release found.");
                        return;
                    }

                    if (compare(latest, current) <= 0) {
                        showToast(ui, ctx,
                            "You're on the latest version (" + current + ").");
                        return;
                    }

                    ui.post(new Runnable() {
                        @Override public void run() {
                            promptInstall(ctx, latest, apkUrl);
                        }
                    });
                } catch (Exception e) {
                    showToast(ui, ctx, "Update check failed: " + e.getMessage());
                }
            }
        }).start();
    }

    private static String pickApkUrl(JSONArray assets) {
        if (assets == null) return null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.optJSONObject(i);
            if (a == null) continue;
            String name = a.optString("name", "");
            if (name.toLowerCase().endsWith(".apk")) {
                return a.optString("browser_download_url", null);
            }
        }
        return null;
    }

    private static void showToast(Handler ui, final Context ctx, final String msg) {
        ui.post(new Runnable() {
            @Override public void run() {
                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private static void promptInstall(final Context ctx, final String latest,
                                      final String apkUrl) {
        if (apkUrl == null) {
            new AlertDialog.Builder(ctx)
                .setTitle("Update available")
                .setMessage("Version " + latest + " is available, but no APK was attached "
                    + "to the release. Open the release page in your browser?")
                .setPositiveButton("Open", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        Intent i = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/flipphoneguy/totp_app/releases/latest"));
                        ctx.startActivity(i);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
            return;
        }
        new AlertDialog.Builder(ctx)
            .setTitle("Update to " + latest + "?")
            .setMessage("A new version is available. Download and install now?")
            .setPositiveButton("Install", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    download(ctx, apkUrl, latest);
                }
            })
            .setNegativeButton("Later", null)
            .show();
    }

    private static void download(final Context ctx, String apkUrl, String latest) {
        try {
            final DownloadManager dm = (DownloadManager)
                ctx.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(apkUrl));
            req.setTitle("Authenticator " + latest);
            req.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            String fileName = "Authenticator-" + latest + ".apk";
            req.setDestinationInExternalFilesDir(ctx, Environment.DIRECTORY_DOWNLOADS, fileName);
            req.setMimeType("application/vnd.android.package-archive");
            final long id = dm.enqueue(req);

            Toast.makeText(ctx, "Downloading update…", Toast.LENGTH_SHORT).show();

            BroadcastReceiver onComplete = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent intent) {
                    long got = intent.getLongExtra(
                        DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                    if (got != id) return;
                    try { c.unregisterReceiver(this); } catch (Exception ignored) {}
                    DownloadManager.Query q = new DownloadManager.Query().setFilterById(id);
                    android.database.Cursor cur = dm.query(q);
                    try {
                        if (cur.moveToFirst()) {
                            int statusIdx =
                                cur.getColumnIndex(DownloadManager.COLUMN_STATUS);
                            int status = cur.getInt(statusIdx);
                            if (status != DownloadManager.STATUS_SUCCESSFUL) {
                                Toast.makeText(c, "Download failed.",
                                    Toast.LENGTH_LONG).show();
                                return;
                            }
                            int uriIdx = cur.getColumnIndex(
                                DownloadManager.COLUMN_LOCAL_URI);
                            String localUri = cur.getString(uriIdx);
                            if (localUri == null) return;
                            File f = new File(Uri.parse(localUri).getPath());
                            launchInstaller(c, f);
                        }
                    } finally {
                        cur.close();
                    }
                }
            };
            IntentFilter f = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            if (Build.VERSION.SDK_INT >= 33) {
                // RECEIVER_EXPORTED = 0x2 (introduced in API 33). Inlined so
                // we keep min-SDK 21 compile.
                ctx.registerReceiver(onComplete, f, 0x2);
            } else {
                ctx.registerReceiver(onComplete, f);
            }
        } catch (Exception e) {
            Toast.makeText(ctx, "Could not download: " + e.getMessage(),
                Toast.LENGTH_LONG).show();
        }
    }

    private static void launchInstaller(Context ctx, File apk) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            Uri uri;
            if (Build.VERSION.SDK_INT >= 24) {
                uri = SharedFileProvider.getUriForFile(ctx, apk);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                uri = Uri.fromFile(apk);
            }
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception e) {
            Toast.makeText(ctx, "Install launch failed: " + e.getMessage(),
                Toast.LENGTH_LONG).show();
        }
    }

    private static String stripV(String s) {
        if (s == null) return "";
        return s.startsWith("v") || s.startsWith("V") ? s.substring(1) : s;
    }

    /** Returns >0 if a newer than b, 0 if equal, <0 if older. */
    public static int compare(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int x = i < pa.length ? safeInt(pa[i]) : 0;
            int y = i < pb.length ? safeInt(pb[i]) : 0;
            if (x != y) return x - y;
        }
        return 0;
    }

    private static int safeInt(String s) {
        try {
            // strip non-digit suffix (e.g. "1-rc1")
            StringBuilder b = new StringBuilder();
            for (char c : s.toCharArray()) {
                if (c >= '0' && c <= '9') b.append(c); else break;
            }
            return b.length() == 0 ? 0 : Integer.parseInt(b.toString());
        } catch (Exception e) { return 0; }
    }
}
