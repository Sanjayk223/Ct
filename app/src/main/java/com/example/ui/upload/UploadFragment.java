package com.example.ui.upload;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;

import com.bumptech.glide.Glide;
import com.example.MainActivity;
import com.example.R;
import com.example.data.R2UploadService;
import com.example.data.VibeReelRepository;
import com.example.databinding.FragmentUploadBinding;
import com.example.util.HashtagStickerCatalog;
import com.google.android.material.chip.Chip;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;

public class UploadFragment extends Fragment {

    private static final String ARG_PRESET = "preset";

    private FragmentUploadBinding binding;
    private VibeReelRepository repository;
    private R2UploadService r2Service;

    private Uri selectedUri;
    private String selectedMediaType = "video";
    private String uploadMode = "reel"; // reel, post, story
    private final List<String> selectedHashtags = new ArrayList<>();
    private String selectedSticker = "";
    private ExoPlayer previewPlayer;

    private final ActivityResultLauncher<PickVisualMediaRequest> mediaPicker =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null && isAdded()) {
                    onMediaSelected(uri);
                }
            });

    public static UploadFragment newInstance(@Nullable String presetMode) {
        UploadFragment f = new UploadFragment();
        Bundle args = new Bundle();
        if (presetMode != null) args.putString(ARG_PRESET, presetMode);
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentUploadBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = new VibeReelRepository(requireContext());
        r2Service = new R2UploadService();

        if (R2UploadService.isConfigured()) {
            binding.txtR2StatusBadge.setText(R.string.r2_ready);
            binding.txtR2StatusBadge.setTextColor(getResources().getColor(R.color.vr_green, null));
        } else {
            binding.txtR2StatusBadge.setText(R.string.r2_not_configured);
            binding.txtR2StatusBadge.setTextColor(getResources().getColor(R.color.vr_gold, null));
        }

        String preset = getArguments() != null ? getArguments().getString(ARG_PRESET) : null;
        if ("story".equals(preset)) {
            binding.toggleUploadType.check(R.id.btnTypeStory);
            uploadMode = "story";
        } else if ("post".equals(preset)) {
            binding.toggleUploadType.check(R.id.btnTypePost);
            uploadMode = "post";
        } else {
            binding.toggleUploadType.check(R.id.btnTypeReel);
            uploadMode = "reel";
        }
        updateModeUi();

        binding.toggleUploadType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btnTypeStory) {
                uploadMode = "story";
            } else if (checkedId == R.id.btnTypePost) {
                uploadMode = "post";
            } else {
                uploadMode = "reel";
            }
            updateModeUi();
        });

        setupHashtagAndStickerChips();

        binding.cardMediaPicker.setOnClickListener(v -> launchPhotoPicker());
        binding.btnPublishMedia.setOnClickListener(v -> startR2UploadAndPublish());
    }

    private void updateModeUi() {
        binding.tilMusicTitle.setVisibility("reel".equals(uploadMode) ? View.VISIBLE : View.GONE);
    }

    private void setupHashtagAndStickerChips() {
        for (String tag : HashtagStickerCatalog.TRENDING_HASHTAGS) {
            Chip chip = new Chip(requireContext());
            chip.setText(tag);
            chip.setCheckable(true);
            chip.setOnCheckedChangeListener((btn, isChecked) -> {
                if (isChecked) {
                    if (!selectedHashtags.contains(tag)) selectedHashtags.add(tag);
                } else {
                    selectedHashtags.remove(tag);
                }
            });
            binding.chipGroupUploadHashtags.addView(chip);
        }

        for (String sticker : HashtagStickerCatalog.TRENDING_STICKERS) {
            Chip chip = new Chip(requireContext());
            chip.setText(sticker);
            chip.setCheckable(true);
            chip.setOnCheckedChangeListener((btn, isChecked) -> {
                if (isChecked) {
                    selectedSticker = sticker;
                } else if (sticker.equals(selectedSticker)) {
                    selectedSticker = "";
                }
            });
            binding.chipGroupUploadStickers.addView(chip);
        }
    }

    private void launchPhotoPicker() {
        if ("reel".equals(uploadMode)) {
            mediaPicker.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.VideoOnly.INSTANCE)
                    .build());
        } else {
            mediaPicker.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo.INSTANCE)
                    .build());
        }
    }

    private void onMediaSelected(Uri uri) {
        selectedUri = uri;
        String mime = requireContext().getContentResolver().getType(uri);
        boolean isVideo = mime != null && mime.startsWith("video/");
        selectedMediaType = isVideo ? "video" : "image";

        binding.layoutPickPlaceholder.setVisibility(View.GONE);
        releasePreviewPlayer();

        if (isVideo) {
            binding.imgUploadPreview.setVisibility(View.GONE);
            binding.uploadVideoPreview.setVisibility(View.VISIBLE);
            previewPlayer = new ExoPlayer.Builder(requireContext()).build();
            binding.uploadVideoPreview.setPlayer(previewPlayer);
            previewPlayer.setMediaItem(MediaItem.fromUri(uri));
            previewPlayer.prepare();
            previewPlayer.setPlayWhenReady(true);
        } else {
            binding.uploadVideoPreview.setVisibility(View.GONE);
            binding.imgUploadPreview.setVisibility(View.VISIBLE);
            Glide.with(this).load(uri).into(binding.imgUploadPreview);
        }
    }

    private void startR2UploadAndPublish() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        if (selectedUri == null) {
            Toast.makeText(requireContext(), "Please select a video or photo first", Toast.LENGTH_SHORT).show();
            return;
        }
        if ("reel".equals(uploadMode) && !"video".equals(selectedMediaType)) {
            Toast.makeText(requireContext(), "Reels require a video file", Toast.LENGTH_SHORT).show();
            return;
        }

        String caption = binding.edtUploadCaption.getText() != null
                ? binding.edtUploadCaption.getText().toString().trim() : "";
        String music = binding.edtMusicTitle.getText() != null
                ? binding.edtMusicTitle.getText().toString().trim() : "Original Sound";
        List<String> finalTags = HashtagStickerCatalog.extractHashtags(caption, selectedHashtags);

        setUploadingState(true, 0);
        String folder = uploadMode + "s";

        r2Service.uploadMedia(requireContext(), selectedUri, folder, user.getUid(), new R2UploadService.UploadCallback() {
            @Override
            public void onProgress(int percent) {
                if (isAdded() && binding != null) {
                    binding.uploadProgressBar.setProgress(percent);
                    binding.txtUploadProgressLabel.setText("Uploading to Cloudflare R2… " + percent + "%");
                }
            }

            @Override
            public void onSuccess(String publicMediaUrl) {
                if (!isAdded()) return;
                binding.txtUploadProgressLabel.setText("Saving to VibeReel feed…");
                saveMetadataToFirestore(publicMediaUrl, caption, finalTags, music);
            }

            @Override
            public void onError(String errorMessage) {
                if (!isAdded()) return;
                setUploadingState(false, 0);
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void saveMetadataToFirestore(String mediaUrl, String caption, List<String> hashtags, String musicTitle) {
        VibeReelRepository.RepoCallback<String> doneCallback = new VibeReelRepository.RepoCallback<>() {
            @Override
            public void onSuccess(String docId) {
                if (!isAdded()) return;
                setUploadingState(false, 100);
                Toast.makeText(requireContext(), "Published! Share & earn 10 🪙 per like", Toast.LENGTH_SHORT).show();
                if (getActivity() instanceof MainActivity) {
                    int targetTab = "reel".equals(uploadMode) ? R.id.nav_reels : R.id.nav_home;
                    ((MainActivity) getActivity()).navigateToTab(targetTab, null);
                }
            }

            @Override
            public void onError(Exception error) {
                if (!isAdded()) return;
                setUploadingState(false, 0);
                Toast.makeText(requireContext(), "Publish failed: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        };

        if ("reel".equals(uploadMode)) {
            repository.createReel(mediaUrl, "", caption, hashtags, selectedSticker, musicTitle, doneCallback);
        } else if ("story".equals(uploadMode)) {
            repository.createStory(mediaUrl, selectedMediaType, caption, selectedSticker, hashtags, doneCallback);
        } else {
            repository.createPost(mediaUrl, selectedMediaType, caption, hashtags, selectedSticker, doneCallback);
        }
    }

    private void setUploadingState(boolean uploading, int progress) {
        if (binding == null) return;
        binding.btnPublishMedia.setEnabled(!uploading);
        binding.cardMediaPicker.setEnabled(!uploading);
        binding.uploadProgressBar.setVisibility(uploading ? View.VISIBLE : View.GONE);
        binding.txtUploadProgressLabel.setVisibility(uploading ? View.VISIBLE : View.GONE);
        binding.uploadProgressBar.setProgress(progress);
    }

    private void releasePreviewPlayer() {
        if (binding != null) binding.uploadVideoPreview.setPlayer(null);
        if (previewPlayer != null) {
            previewPlayer.release();
            previewPlayer = null;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (previewPlayer != null) previewPlayer.setPlayWhenReady(false);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        releasePreviewPlayer();
        binding = null;
    }
}
