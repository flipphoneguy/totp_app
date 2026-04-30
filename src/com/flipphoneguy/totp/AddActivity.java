package com.flipphoneguy.totp;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class AddActivity extends Activity {

    private static final int REQ_CAMERA      = 201;
    private static final int REQ_PICK_IMAGE  = 202;
    private static final int REQ_PERM_CAMERA = 301;

    private View manualForm;
    private Uri pendingPhotoUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add);

        manualForm = findViewById(R.id.manual_form);

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        findViewById(R.id.card_manual).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                manualForm.setVisibility(View.VISIBLE);
                EditText n = findViewById(R.id.edit_name);
                n.requestFocus();
            }
        });

        findViewById(R.id.card_camera).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { launchCamera(); }
        });

        findViewById(R.id.card_picture).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { launchPicker(); }
        });

        ((Button) findViewById(R.id.btn_save)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { saveManual(); }
        });
    }

    // ── Camera ────────────────────────────────────────────────────────────
    private void launchCamera() {
        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{ Manifest.permission.CAMERA }, REQ_PERM_CAMERA);
            return;
        }
        try {
            File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (dir == null) dir = getCacheDir();
            File photo = new File(dir, "qr_capture.jpg");
            pendingPhotoUri = SharedFileProvider.getUriForFile(this, photo);
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, pendingPhotoUri);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            App.beginSubIntent();
            startActivityForResult(i, REQ_CAMERA);
        } catch (Exception e) {
            Toast.makeText(this, "Camera not available: " + e.getMessage(),
                Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code == REQ_PERM_CAMERA) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED)
                launchCamera();
            else
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
        }
    }

    private void launchPicker() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        App.beginSubIntent();
        startActivityForResult(i, REQ_PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        App.endSubIntent();
        if (res != RESULT_OK) return;

        Bitmap bmp = null;
        try {
            if (req == REQ_CAMERA && pendingPhotoUri != null) {
                InputStream in = getContentResolver().openInputStream(pendingPhotoUri);
                bmp = BitmapFactory.decodeStream(in);
                if (in != null) in.close();
            } else if (req == REQ_PICK_IMAGE && data != null && data.getData() != null) {
                InputStream in = getContentResolver().openInputStream(data.getData());
                bmp = BitmapFactory.decodeStream(in);
                if (in != null) in.close();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Could not read image: " + e.getMessage(),
                Toast.LENGTH_LONG).show();
            return;
        }

        if (bmp == null) {
            Toast.makeText(this, "Could not load image", Toast.LENGTH_SHORT).show();
            return;
        }

        String text = QrDecoder.decode(bmp);
        if (text == null) {
            Toast.makeText(this, "No QR code found in image", Toast.LENGTH_LONG).show();
            return;
        }

        TotpEntry parsed = OtpAuthUri.parse(text);
        if (parsed == null) {
            // Maybe the QR is just a Base32 secret — prefill manual form
            if (Base32.isValid(text)) {
                manualForm.setVisibility(View.VISIBLE);
                ((EditText) findViewById(R.id.edit_seed)).setText(text);
                ((EditText) findViewById(R.id.edit_name)).requestFocus();
                Toast.makeText(this,
                    "Detected raw seed — enter a name and Save.",
                    Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "QR is not an otpauth code.", Toast.LENGTH_LONG).show();
            }
            return;
        }

        addAndFinish(parsed);
    }

    private void saveManual() {
        String name = ((EditText) findViewById(R.id.edit_name)).getText().toString().trim();
        String seed = ((EditText) findViewById(R.id.edit_seed)).getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Enter a name", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Base32.isValid(seed)) {
            Toast.makeText(this, "Invalid Base32 seed", Toast.LENGTH_SHORT).show();
            return;
        }
        addAndFinish(new TotpEntry(name, seed));
    }

    private void addAndFinish(TotpEntry e) {
        List<TotpEntry> list = EntryStore.load(this);
        list.add(e);
        try {
            EntryStore.save(this, list);
            Toast.makeText(this, "Added: " + e.name, Toast.LENGTH_SHORT).show();
            finish();
        } catch (Exception ex) {
            Toast.makeText(this, "Save failed: " + ex.getMessage(),
                Toast.LENGTH_LONG).show();
        }
    }

}
