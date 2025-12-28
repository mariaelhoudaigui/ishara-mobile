package com.example.signlanguagetranslator;

import androidx.appcompat.app.AppCompatActivity;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.VideoView;

public class VideoDisplay extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_display);

        String videoUrl = getIntent().getStringExtra("video_id");

        VideoView videoView = findViewById(R.id.mainVideo);
        ProgressBar progressBar = findViewById(R.id.bufferProgress);

        MediaController mediaController = new MediaController(this);
        mediaController.setAnchorView(videoView);

        videoView.setMediaController(mediaController);
        videoView.setVideoURI(Uri.parse(videoUrl));

        progressBar.setVisibility(View.VISIBLE);

        videoView.setOnPreparedListener(mp -> {
            progressBar.setVisibility(View.GONE);
            mp.start();
        });

        videoView.setOnErrorListener((mp, what, extra) -> {
            progressBar.setVisibility(View.GONE);
            return false;
        });

        videoView.start();
    }
}
