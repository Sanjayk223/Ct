package com.example.ui.stories;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;

import com.bumptech.glide.Glide;
import com.example.R;
import com.example.data.VibeReelRepository;
import com.example.databinding.ActivityStoryViewerBinding;

import java.util.ArrayList;

public class StoryViewerActivity extends AppCompatActivity {

    private static final String EXTRA_INDEX = "index";
    private static final String EXTRA_IDS = "ids";
    private static final String EXTRA_AUTHORS = "authors";
    private static final String EXTRA_AVATARS = "avatars";
    private static final String EXTRA_URLS = "urls";
    private static final String EXTRA_TYPES = "types";
    private static final String EXTRA_CAPTIONS = "captions";
    private static final String EXTRA_STICKERS = "stickers";

    private ActivityStoryViewerBinding binding;
    private VibeReelRepository repository;
    private ArrayList<String> ids, authors, avatars, urls, types, captions, stickers;
    private int currentIndex = 0;
    private ExoPlayer player;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int progress = 0;

    private final Runnable progressTick = new Runnable() {
        @Override
        public void run() {
            progress += 2;
            binding.storyProgressBar.setProgress(progress);
            if (progress >= 100) {
                showNextStory();
            } else {
                handler.postDelayed(this, 100);
            }
        }
    };

    public static Intent createIntent(
            Context context,
            int index,
            ArrayList<String> ids,
            ArrayList<String> authors,
            ArrayList<String> avatars,
            ArrayList<String> urls,
            ArrayList<String> types,
            ArrayList<String> captions,
            ArrayList<String> stickers
    ) {
        Intent i = new Intent(context, StoryViewerActivity.class);
        i.putExtra(EXTRA_INDEX, index);
        i.putStringArrayListExtra(EXTRA_IDS, ids);
        i.putStringArrayListExtra(EXTRA_AUTHORS, authors);
        i.putStringArrayListExtra(EXTRA_AVATARS, avatars);
        i.putStringArrayListExtra(EXTRA_URLS, urls);
        i.putStringArrayListExtra(EXTRA_TYPES, types);
        i.putStringArrayListExtra(EXTRA_CAPTIONS, captions);
        i.putStringArrayListExtra(EXTRA_STICKERS, stickers);
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityStoryViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.storyViewerRoot, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.storyTopOverlay.setPadding(16, sys.top + 12, 16, 12);
            return WindowInsetsCompat.CONSUMED;
        });

        repository = new VibeReelRepository(this);
        currentIndex = getIntent().getIntExtra(EXTRA_INDEX, 0);
        ids = getIntent().getStringArrayListExtra(EXTRA_IDS);
        authors = getIntent().getStringArrayListExtra(EXTRA_AUTHORS);
        avatars = getIntent().getStringArrayListExtra(EXTRA_AVATARS);
        urls = getIntent().getStringArrayListExtra(EXTRA_URLS);
        types = getIntent().getStringArrayListExtra(EXTRA_TYPES);
        captions = getIntent().getStringArrayListExtra(EXTRA_CAPTIONS);
        stickers = getIntent().getStringArrayListExtra(EXTRA_STICKERS);

        if (ids == null || ids.isEmpty()) {
            finish();
            return;
        }

        binding.btnCloseStory.setOnClickListener(v -> finish());
        binding.zonePrevStory.setOnClickListener(v -> showPrevStory());
        binding.zoneNextStory.setOnClickListener(v -> showNextStory());

        renderCurrentStory();
    }

    private void renderCurrentStory() {
        handler.removeCallbacks(progressTick);
        releasePlayer();
        progress = 0;
        binding.storyProgressBar.setProgress(0);

        String id = ids.get(currentIndex);
        String author = authors.get(currentIndex);
        String avatar = avatars.get(currentIndex);
        String url = urls.get(currentIndex);
        String type = types.get(currentIndex);
        String caption = captions.get(currentIndex);
        String sticker = stickers.get(currentIndex);

        binding.txtStoryViewerAuthor.setText(author);
        binding.txtStoryViewerMeta.setText((currentIndex + 1) + " / " + ids.size());
        binding.txtStoryCaption.setText(caption);

        if (sticker != null && !sticker.isEmpty()) {
            binding.txtStorySticker.setVisibility(View.VISIBLE);
            binding.txtStorySticker.setText(sticker);
        } else {
            binding.txtStorySticker.setVisibility(View.GONE);
        }

        Glide.with(this)
                .load(avatar)
                .placeholder(R.drawable.img_default_avatar)
                .error(R.drawable.img_default_avatar)
                .into(binding.imgStoryViewerAvatar);

        if ("video".equalsIgnoreCase(type)) {
            binding.imgStoryViewerMedia.setVisibility(View.GONE);
            binding.storyPlayerView.setVisibility(View.VISIBLE);
            player = new ExoPlayer.Builder(this).build();
            binding.storyPlayerView.setPlayer(player);
            player.setMediaItem(MediaItem.fromUri(url));
            player.prepare();
            player.setPlayWhenReady(true);
        } else {
            binding.storyPlayerView.setVisibility(View.GONE);
            binding.imgStoryViewerMedia.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load(url)
                    .placeholder(R.drawable.img_auth_hero)
                    .error(R.drawable.img_auth_hero)
                    .into(binding.imgStoryViewerMedia);
        }

        repository.incrementStoryView(id);
        handler.postDelayed(progressTick, 100);
    }

    private void showNextStory() {
        if (currentIndex + 1 < ids.size()) {
            currentIndex++;
            renderCurrentStory();
        } else {
            finish();
        }
    }

    private void showPrevStory() {
        if (currentIndex > 0) {
            currentIndex--;
            renderCurrentStory();
        } else {
            renderCurrentStory();
        }
    }

    private void releasePlayer() {
        if (binding != null) {
            binding.storyPlayerView.setPlayer(null);
        }
        if (player != null) {
            player.release();
            player = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(progressTick);
        releasePlayer();
    }
}
