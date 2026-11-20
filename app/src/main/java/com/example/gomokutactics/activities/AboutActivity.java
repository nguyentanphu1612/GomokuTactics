package com.example.gomokutactics.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.gomokutactics.R;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.title_about);
        setContentView(R.layout.activity_about);

        findViewById(R.id.btnShareApp).setOnClickListener(v -> {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.about_share_text));
            startActivity(Intent.createChooser(shareIntent, getString(R.string.chooser_share_via)));
        });

        findViewById(R.id.btnContactAuthor).setOnClickListener(v -> {
            Intent emailIntent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:punnguyen1612@gmail.com"));
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.about_email_subject));
            startActivity(Intent.createChooser(emailIntent, getString(R.string.btn_contact_author)));
        });
    }
}