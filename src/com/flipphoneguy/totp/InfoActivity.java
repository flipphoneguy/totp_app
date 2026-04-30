package com.flipphoneguy.totp;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class InfoActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtil.applyTheme(this);
        setContentView(R.layout.activity_info);

        ((TextView) findViewById(R.id.version_text))
            .setText("v" + UpdateChecker.currentVersion(this));

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        ((Button) findViewById(R.id.btn_github)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                openUrl(getString(R.string.info_github_url));
            }
        });
        ((Button) findViewById(R.id.btn_repo)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                openUrl(getString(R.string.info_repo_url));
            }
        });
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {}
    }
}
