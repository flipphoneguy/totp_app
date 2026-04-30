package com.flipphoneguy.totp;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.OutputStream;
import java.util.List;

public class SettingsActivity extends Activity {

    private static final int REQ_VERIFY_PW    = 401;
    private static final int REQ_SET_PW       = 402;
    private static final int REQ_EXPORT_PLAIN = 410;
    private static final int REQ_EXPORT_ENC   = 411;
    private static final int REQ_IMPORT       = 412;
    private static final int REQ_PERM_WRITE   = 501;

    private Switch swPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        swPassword = findViewById(R.id.sw_password);
        swPassword.setChecked(EntryStore.isPasswordEnabled(this));

        ((TextView) findViewById(R.id.version_label))
            .setText("v" + UpdateChecker.currentVersion(this));

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        findViewById(R.id.row_password).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onTogglePassword(); }
        });
        findViewById(R.id.btn_export_plain).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { exportPlain(); }
        });
        findViewById(R.id.btn_export_enc).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { exportEncrypted(); }
        });
        findViewById(R.id.btn_import).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startImport(); }
        });
        findViewById(R.id.btn_check_update).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                UpdateChecker.check(SettingsActivity.this);
            }
        });
        findViewById(R.id.btn_about).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, InfoActivity.class));
            }
        });
    }

    // ── Password toggle ────────────────────────────────────────────────────
    private void onTogglePassword() {
        boolean enabled = EntryStore.isPasswordEnabled(this);
        if (!enabled) {
            // Set new password
            Intent i = new Intent(this, PasswordActivity.class);
            i.putExtra(PasswordActivity.EXTRA_MODE, PasswordActivity.MODE_SET);
            startActivityForResult(i, REQ_SET_PW);
        } else {
            // Verify before disabling
            new AlertDialog.Builder(this)
                .setTitle("Disable password?")
                .setMessage("Accounts stay encrypted on disk, but the password prompt will no longer be required. Continue?")
                .setPositiveButton("Disable", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        try {
                            EntryStore.disablePassword(SettingsActivity.this);
                            swPassword.setChecked(false);
                            Toast.makeText(SettingsActivity.this,
                                "Password disabled.", Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Toast.makeText(SettingsActivity.this,
                                "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_SET_PW) {
            swPassword.setChecked(EntryStore.isPasswordEnabled(this));
            return;
        }
        if (req == REQ_EXPORT_PLAIN || req == REQ_EXPORT_ENC || req == REQ_IMPORT) {
            App.endSubIntent();
        }

        if (res != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;

        if (req == REQ_EXPORT_PLAIN) doExportPlain(uri);
        else if (req == REQ_EXPORT_ENC) doExportEncrypted(uri);
        else if (req == REQ_IMPORT) doImport(uri);
    }

    // ── Export plain ───────────────────────────────────────────────────────
    private void exportPlain() {
        new AlertDialog.Builder(this)
            .setTitle("Export plaintext")
            .setMessage("This will save your seeds in unencrypted JSON. " +
                "Anyone with the file can read your codes. Continue?")
            .setPositiveButton("Continue", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    i.setType("application/json");
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.putExtra(Intent.EXTRA_TITLE, "totp_backup.json");
                    App.beginSubIntent();
                    startActivityForResult(i, REQ_EXPORT_PLAIN);
                }
            })
            .setNegativeButton(R.string.btn_cancel, null)
            .show();
    }

    private void doExportPlain(Uri uri) {
        try {
            List<TotpEntry> entries = EntryStore.load(this);
            String json = JsonCodec.serialize(entries);
            OutputStream os = getContentResolver().openOutputStream(uri);
            os.write(json.getBytes("UTF-8"));
            os.close();
            Toast.makeText(this, "Exported " + entries.size() + " entries.",
                Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(),
                Toast.LENGTH_LONG).show();
        }
    }

    // ── Export encrypted ───────────────────────────────────────────────────
    private void exportEncrypted() {
        promptPassword("Backup password", new PwCallback() {
            @Override public void onPassword(String pw) {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.setType("application/octet-stream");
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.putExtra(Intent.EXTRA_TITLE, "totp_backup.totp1");
                pendingExportPw = pw;
                App.beginSubIntent();
                startActivityForResult(i, REQ_EXPORT_ENC);
            }
        });
    }
    private String pendingExportPw;

    private void doExportEncrypted(Uri uri) {
        try {
            List<TotpEntry> entries = EntryStore.load(this);
            String json = JsonCodec.serialize(entries);
            String wire = CryptoUtil.encryptWithPassword(json, pendingExportPw.toCharArray());
            OutputStream os = getContentResolver().openOutputStream(uri);
            os.write(wire.getBytes("UTF-8"));
            os.close();
            Toast.makeText(this, "Exported (encrypted).", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(),
                Toast.LENGTH_LONG).show();
        } finally {
            pendingExportPw = null;
        }
    }

    // ── Import ─────────────────────────────────────────────────────────────
    private void startImport() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        App.beginSubIntent();
        startActivityForResult(i, REQ_IMPORT);
    }

    private void doImport(final Uri uri) {
        try {
            String content = EntryStore.readAll(getContentResolver().openInputStream(uri));
            if (CryptoUtil.isPasswordEncrypted(content)) {
                final String wire = content;
                promptPassword("Backup password", new PwCallback() {
                    @Override public void onPassword(String pw) {
                        try {
                            String json = CryptoUtil.decryptWithPassword(wire, pw.toCharArray());
                            mergeImported(json);
                        } catch (Exception e) {
                            Toast.makeText(SettingsActivity.this,
                                "Wrong password or corrupt file.",
                                Toast.LENGTH_LONG).show();
                        }
                    }
                });
            } else {
                mergeImported(content);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Import failed: " + e.getMessage(),
                Toast.LENGTH_LONG).show();
        }
    }

    private void mergeImported(String json) throws Exception {
        List<TotpEntry> incoming = JsonCodec.parse(json);
        List<TotpEntry> existing = EntryStore.load(this);
        // Avoid exact duplicates (name+seed)
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (TotpEntry e : existing) seen.add(e.name + "\u0001" + e.seed);
        int added = 0;
        for (TotpEntry e : incoming) {
            String k = e.name + "\u0001" + e.seed;
            if (!seen.contains(k)) {
                existing.add(e);
                seen.add(k);
                added++;
            }
        }
        EntryStore.save(this, existing);
        Toast.makeText(this, "Imported " + added + " new entries.",
            Toast.LENGTH_SHORT).show();
    }

    // ── Password prompt ────────────────────────────────────────────────────
    private interface PwCallback { void onPassword(String pw); }

    private void promptPassword(String title, final PwCallback cb) {
        final EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        int pad = (int)(16 * getResources().getDisplayMetrics().density);
        e.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(this)
            .setTitle(title)
            .setView(e)
            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    String s = e.getText().toString();
                    if (!s.isEmpty()) cb.onPassword(s);
                }
            })
            .setNegativeButton(R.string.btn_cancel, null)
            .show();
    }

}
