package com.flipphoneguy.totp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_UNLOCK   = 100;

    private ListView listView;
    private View emptyState;
    private Button btnRemove;

    private final List<TotpEntry> entries = new ArrayList<>();
    private EntryAdapter adapter;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (adapter != null) adapter.notifyDataSetChanged();
            ui.postDelayed(this, 1000);
        }
    };
    private boolean unlockPending = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listView   = findViewById(R.id.totp_list);
        emptyState = findViewById(R.id.empty_state);
        btnRemove  = findViewById(R.id.btn_remove);

        adapter = new EntryAdapter(this, entries);
        listView.setAdapter(adapter);

        findViewById(R.id.btn_about).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, InfoActivity.class));
            }
        });
        findViewById(R.id.btn_settings).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        findViewById(R.id.btn_add).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, AddActivity.class));
            }
        });
        btnRemove.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showRemoveDialog(); }
        });

        listView.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> p, View v, int pos, long id) {
                showCodeDialog(entries.get(pos));
            }
        });

        // Unlock check happens in onResume so it covers both cold start and
        // returning from background.

        // Handle otpauth:// intents from QR scanners
        handleViewIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleViewIntent(intent);
    }

    private void handleViewIntent(Intent intent) {
        if (intent == null) return;
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            Uri data = intent.getData();
            if ("otpauth".equalsIgnoreCase(data.getScheme())) {
                TotpEntry e = OtpAuthUri.parse(data.toString());
                if (e != null) {
                    entries.add(e);
                    saveAndRefresh();
                    Toast.makeText(this, "Added: " + e.name, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Could not parse otpauth URI",
                        Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (EntryStore.isPasswordEnabled(this) && !EntryStore.hasActivePassword()) {
            if (!unlockPending) {
                unlockPending = true;
                Intent i = new Intent(this, PasswordActivity.class);
                i.putExtra(PasswordActivity.EXTRA_MODE, PasswordActivity.MODE_UNLOCK);
                startActivityForResult(i, REQ_UNLOCK);
            }
            return;
        }
        reload();
        ui.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(tick);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_UNLOCK) {
            unlockPending = false;
            if (res != RESULT_OK) {
                finish();
                return;
            }
            reload();
        }
    }

    private void reload() {
        List<TotpEntry> loaded = EntryStore.load(this);
        entries.clear();
        entries.addAll(loaded);
        adapter.notifyDataSetChanged();
        boolean empty = entries.isEmpty();
        listView.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        btnRemove.setEnabled(!empty);
    }

    private void saveAndRefresh() {
        try {
            EntryStore.save(this, entries);
        } catch (Exception e) {
            Toast.makeText(this, "Save failed: " + e.getMessage(),
                Toast.LENGTH_LONG).show();
        }
        reload();
    }

    private void showCodeDialog(final TotpEntry e) {
        String code = TotpGenerator.code(e.seed);
        if (code.length() == 6) code = code.substring(0, 3) + " " + code.substring(3);
        new AlertDialog.Builder(this)
            .setTitle(e.name)
            .setMessage("Code: " + code +
                "\nExpires in: " + TotpGenerator.secondsRemaining() + "s")
            .setPositiveButton("Copy", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    String c = TotpGenerator.code(e.seed);
                    android.content.ClipboardManager cm =
                        (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("totp", c));
                    Toast.makeText(MainActivity.this, "Copied", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Close", null)
            .show();
    }

    private void showRemoveDialog() {
        if (entries.isEmpty()) return;
        final String[] names = new String[entries.size()];
        for (int i = 0; i < entries.size(); i++) names[i] = entries.get(i).name;
        final boolean[] checked = new boolean[entries.size()];

        new AlertDialog.Builder(this)
            .setTitle(R.string.btn_remove)
            .setMultiChoiceItems(names, checked,
                new DialogInterface.OnMultiChoiceClickListener() {
                    @Override public void onClick(DialogInterface d, int which, boolean b) {
                        checked[which] = b;
                    }
                })
            .setPositiveButton(R.string.btn_delete, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    List<TotpEntry> kept = new ArrayList<>();
                    for (int i = 0; i < entries.size(); i++) {
                        if (!checked[i]) kept.add(entries.get(i));
                    }
                    entries.clear();
                    entries.addAll(kept);
                    saveAndRefresh();
                }
            })
            .setNegativeButton(R.string.btn_cancel, null)
            .show();
    }
}
