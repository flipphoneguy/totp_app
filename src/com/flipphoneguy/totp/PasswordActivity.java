package com.flipphoneguy.totp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

/**
 * Modes:
 *   MODE_UNLOCK — single password field; verifies it can decrypt entries.enc.
 *   MODE_SET    — password + confirm; sets up encryption, migrates plaintext.
 *   MODE_VERIFY — single password field; used to confirm before disabling.
 */
public class PasswordActivity extends Activity {

    public static final String EXTRA_MODE = "mode";
    public static final int MODE_UNLOCK = 0;
    public static final int MODE_SET    = 1;
    public static final int MODE_VERIFY = 2;

    private int mode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtil.applyTheme(this);
        setContentView(R.layout.activity_password);

        mode = getIntent().getIntExtra(EXTRA_MODE, MODE_UNLOCK);

        TextView title = findViewById(R.id.title_text);
        EditText pw    = findViewById(R.id.edit_password);
        EditText pw2   = findViewById(R.id.edit_password_confirm);
        Button btn     = findViewById(R.id.btn_unlock);
        final TextView err = findViewById(R.id.error_text);

        if (mode == MODE_SET) {
            title.setText(R.string.set_password_title);
            pw2.setVisibility(View.VISIBLE);
            btn.setText(R.string.btn_save);
        } else {
            title.setText(R.string.password_title);
            pw2.setVisibility(View.GONE);
            btn.setText(R.string.btn_unlock);
        }

        btn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { submit(err); }
        });

        pw.requestFocus();
    }

    private void submit(TextView err) {
        EditText pwField  = findViewById(R.id.edit_password);
        EditText pwField2 = findViewById(R.id.edit_password_confirm);

        String p1 = pwField.getText().toString();
        if (p1.isEmpty()) { showErr(err, "Enter a password."); return; }

        if (mode == MODE_SET) {
            String p2 = pwField2.getText().toString();
            if (!p1.equals(p2)) { showErr(err, "Passwords don't match."); return; }
            try {
                EntryStore.enablePassword(this, p1.toCharArray());
                Toast.makeText(this, "Password enabled.", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            } catch (Exception e) {
                showErr(err, "Could not enable: " + e.getMessage());
            }
            return;
        }

        // UNLOCK / VERIFY: try to decrypt entries.enc
        File f = new File(getFilesDir(), "entries.enc");
        if (!f.exists()) {
            // No file — first-run with password enabled, accept any password and
            // create an empty list.
            EntryStore.setActivePassword(p1.toCharArray());
            try {
                EntryStore.save(this, new java.util.ArrayList<TotpEntry>());
            } catch (Exception ignored) {}
            setResult(RESULT_OK);
            finish();
            return;
        }
        try {
            String wire = EntryStore.readAll(f);
            CryptoUtil.decrypt(wire, p1.toCharArray());
            EntryStore.setActivePassword(p1.toCharArray());
            setResult(RESULT_OK);
            finish();
        } catch (Exception e) {
            showErr(err, "Wrong password.");
        }
    }

    private void showErr(TextView err, String msg) {
        err.setVisibility(View.VISIBLE);
        err.setText(msg);
    }

    @Override
    public void onBackPressed() {
        if (mode == MODE_UNLOCK) {
            // Don't let the user bypass the lock — exit the app.
            finishAffinity();
        } else {
            super.onBackPressed();
        }
    }
}
