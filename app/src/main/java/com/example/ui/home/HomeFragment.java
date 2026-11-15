package com.example.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.MainActivity;
import com.example.R;
import com.example.data.VibeReelRepository;
import com.example.databinding.FragmentHomeBinding;
import com.example.model.PostItem;
import com.example.model.StoryItem;
import com.example.model.UserProfile;
import com.example.ui.common.CommentsBottomSheetDialogFragment;
import com.example.ui.stories.StoryViewerActivity;
import com.example.util.HashtagStickerCatalog;
import com.example.util.TimeFormatUtil;
import com.google.android.material.chip.Chip;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private VibeReelRepository repository;
    private final List<StoryItem> stories = new ArrayList<>();
    private final List<PostItem> allPosts = new ArrayList<>();
    private final List<PostItem> filteredPosts = new ArrayList<>();
    private StoryAdapter storyAdapter;
    private PostAdapter postAdapter;
    private ListenerRegistration storiesReg, postsReg, userReg;
    private String selectedHashtag = "All";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = new VibeReelRepository(requireContext());

        setupHashtagChips();
        setupRecyclerViews();

        binding.btnCoinPill.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).navigateToTab(R.id.nav_wallet, null);
            }
        });

        binding.swipeRefreshHome.setOnRefreshListener(() -> {
            applyHashtagFilter();
            binding.swipeRefreshHome.setRefreshing(false);
        });

        startListeners();
    }

    private void setupHashtagChips() {
        List<String> filters = new ArrayList<>();
        filters.add("All");
        filters.addAll(HashtagStickerCatalog.TRENDING_HASHTAGS);

        for (String tag : filters) {
            Chip chip = new Chip(requireContext());
            chip.setText(tag);
            chip.setCheckable(true);
            chip.setChecked("All".equals(tag));
            chip.setOnClickListener(v -> {
                selectedHashtag = tag;
                applyHashtagFilter();
            });
            binding.chipGroupHashtagFilter.addView(chip);
        }
    }

    private void setupRecyclerViews() {
        storyAdapter = new StoryAdapter(stories, new StoryAdapter.OnStoryClickListener() {
            @Override
            public void onAddStoryClicked() {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).navigateToTab(R.id.nav_upload, "story");
                }
            }

            @Override
            public void onStoryClicked(int storyIndex) {
                if (stories.isEmpty()) return;
                ArrayList<String> ids = new ArrayList<>();
                ArrayList<String> authors = new ArrayList<>();
                ArrayList<String> avatars = new ArrayList<>();
                ArrayList<String> mediaUrls = new ArrayList<>();
                ArrayList<String> mediaTypes = new ArrayList<>();
                ArrayList<String> captions = new ArrayList<>();
                ArrayList<String> stickers = new ArrayList<>();
                for (StoryItem s : stories) {
                    ids.add(s.id);
                    authors.add(s.authorName);
                    avatars.add(s.authorAvatar != null ? s.authorAvatar : "");
                    mediaUrls.add(s.mediaUrl);
                    mediaTypes.add(s.mediaType);
                    captions.add(s.caption != null ? s.caption : "");
                    stickers.add(s.stickerText != null ? s.stickerText : "");
                }
                Intent intent = StoryViewerActivity.createIntent(
                        requireContext(), storyIndex, ids, authors, avatars, mediaUrls, mediaTypes, captions, stickers
                );
                startActivity(intent);
            }
        });
        binding.rvStories.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.rvStories.setAdapter(storyAdapter);

        postAdapter = new PostAdapter(filteredPosts, new PostAdapter.OnPostActionListener() {
            @Override
            public void onLikeClicked(PostItem post, boolean currentlyLiked) {
                repository.togglePostLike(post, currentlyLiked, new VibeReelRepository.RepoCallback<>() {
                    @Override
                    public void onSuccess(Boolean newLikedState) {
                        if (isAdded()) {
                            postAdapter.updateLikedCache(post.id, newLikedState);
                            if (newLikedState) {
                                Toast.makeText(requireContext(), "Liked! +10 🪙 awarded to " + post.authorName, Toast.LENGTH_SHORT).show();
                            }
                        }
                    }

                    @Override
                    public void onError(Exception error) {
                        if (isAdded()) {
                            Toast.makeText(requireContext(), "Like failed: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }

            @Override
            public void onCommentClicked(PostItem post) {
                CommentsBottomSheetDialogFragment.newInstance("posts", post.id)
                        .show(getChildFragmentManager(), "comments_post");
            }

            @Override
            public void onShareClicked(PostItem post) {
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_TEXT, post.authorName + " on VibeReel: " + post.caption + "\n" + post.mediaUrl);
                startActivity(Intent.createChooser(share, "Share Post"));
            }

            @Override
            public void checkLikedState(PostItem post, PostAdapter.LikeStateConsumer consumer) {
                repository.checkLiked("posts", post.id, new VibeReelRepository.RepoCallback<>() {
                    @Override
                    public void onSuccess(Boolean result) {
                        if (isAdded()) consumer.accept(result);
                    }

                    @Override
                    public void onError(Exception error) {
                        // Ignore
                    }
                });
            }
        });
        binding.rvPosts.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvPosts.setAdapter(postAdapter);
    }

    private void startListeners() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        userReg = repository.observeCurrentUser(currentUser.getUid(), new VibeReelRepository.RealtimeListener<>() {
            @Override
            public void onUpdate(UserProfile data) {
                if (binding != null) {
                    binding.txtHeaderCoins.setText(TimeFormatUtil.formatCount(data.getAvailableCoins()));
                }
            }

            @Override
            public void onError(Exception error) {}
        });

        storiesReg = repository.observeStories(new VibeReelRepository.RealtimeListener<>() {
            @Override
            public void onUpdate(List<StoryItem> data) {
                if (binding == null) return;
                stories.clear();
                stories.addAll(data);
                storyAdapter.notifyDataSetChanged();
            }

            @Override
            public void onError(Exception error) {}
        });

        postsReg = repository.observePosts(new VibeReelRepository.RealtimeListener<>() {
            @Override
            public void onUpdate(List<PostItem> data) {
                if (binding == null) return;
                allPosts.clear();
                allPosts.addAll(data);
                applyHashtagFilter();
            }

            @Override
            public void onError(Exception error) {}
        });
    }

    private void applyHashtagFilter() {
        if (binding == null) return;
        filteredPosts.clear();
        if ("All".equals(selectedHashtag)) {
            filteredPosts.addAll(allPosts);
        } else {
            for (PostItem p : allPosts) {
                if (p.hashtags != null && p.hashtags.contains(selectedHashtag.toLowerCase())) {
                    filteredPosts.add(p);
                }
            }
        }
        postAdapter.notifyDataSetChanged();
        binding.txtEmptyPosts.setVisibility(filteredPosts.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (postAdapter != null) {
            postAdapter.releaseInlinePlayer();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (postAdapter != null) postAdapter.releaseInlinePlayer();
        if (storiesReg != null) storiesReg.remove();
        if (postsReg != null) postsReg.remove();
        if (userReg != null) userReg.remove();
        binding = null;
    }
}
