package com.example.ui.reels;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.example.MainActivity;
import com.example.R;
import com.example.data.VibeReelRepository;
import com.example.databinding.FragmentReelsBinding;
import com.example.model.ReelItem;
import com.example.ui.common.CommentsBottomSheetDialogFragment;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class ReelsFragment extends Fragment {

    private FragmentReelsBinding binding;
    private VibeReelRepository repository;
    private final List<ReelItem> reels = new ArrayList<>();
    private ReelsAdapter adapter;
    private ListenerRegistration registration;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentReelsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = new VibeReelRepository(requireContext());

        adapter = new ReelsAdapter(reels, new ReelsAdapter.OnReelActionListener() {
            @Override
            public void onLikeClicked(ReelItem reel, boolean currentlyLiked) {
                repository.toggleReelLike(reel, currentlyLiked, new VibeReelRepository.RepoCallback<>() {
                    @Override
                    public void onSuccess(Boolean newLikedState) {
                        if (isAdded()) {
                            adapter.updateLikedCache(reel.id, newLikedState);
                            if (newLikedState) {
                                Toast.makeText(requireContext(), "Liked Reel! +10 🪙 to " + reel.authorName, Toast.LENGTH_SHORT).show();
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
            public void onCommentClicked(ReelItem reel) {
                CommentsBottomSheetDialogFragment.newInstance("reels", reel.id)
                        .show(getChildFragmentManager(), "comments_reel");
            }

            @Override
            public void onShareClicked(ReelItem reel) {
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_TEXT, reel.authorName + "'s Reel on VibeReel: " + reel.caption + "\n" + reel.videoUrl);
                startActivity(Intent.createChooser(share, "Share Reel"));
            }

            @Override
            public void checkLikedState(ReelItem reel, ReelsAdapter.LikeStateConsumer consumer) {
                repository.checkLiked("reels", reel.id, new VibeReelRepository.RepoCallback<>() {
                    @Override
                    public void onSuccess(Boolean result) {
                        if (isAdded()) consumer.accept(result);
                    }

                    @Override
                    public void onError(Exception error) {}
                });
            }
        });

        binding.reelsViewPager.setAdapter(adapter);
        binding.reelsViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                if (isAdded()) {
                    adapter.playAtPosition(requireContext(), position);
                }
            }
        });

        binding.btnGoToUploadReel.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).navigateToTab(R.id.nav_upload, "reel");
            }
        });

        registration = repository.observeReels(new VibeReelRepository.RealtimeListener<>() {
            @Override
            public void onUpdate(List<ReelItem> data) {
                if (binding == null) return;
                reels.clear();
                reels.addAll(data);
                adapter.notifyDataSetChanged();
                binding.emptyReelsContainer.setVisibility(reels.isEmpty() ? View.VISIBLE : View.GONE);
                if (!reels.isEmpty() && isResumed()) {
                    binding.reelsViewPager.post(() -> {
                        if (isAdded() && binding != null) {
                            adapter.playAtPosition(requireContext(), binding.reelsViewPager.getCurrentItem());
                        }
                    });
                }
            }

            @Override
            public void onError(Exception error) {}
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        if (adapter != null) adapter.pausePlayer();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null && !reels.isEmpty() && binding != null) {
            adapter.playAtPosition(requireContext(), binding.reelsViewPager.getCurrentItem());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (adapter != null) adapter.releasePlayer();
        if (registration != null) registration.remove();
        binding = null;
    }
}
