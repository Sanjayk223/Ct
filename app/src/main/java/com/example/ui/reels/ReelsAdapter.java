package com.example.ui.reels;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.R;
import com.example.model.ReelItem;
import com.example.util.TimeFormatUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReelsAdapter extends RecyclerView.Adapter<ReelsAdapter.ReelVH> {

    public interface OnReelActionListener {
        void onLikeClicked(ReelItem reel, boolean currentlyLiked);
        void onCommentClicked(ReelItem reel);
        void onShareClicked(ReelItem reel);
        void checkLikedState(ReelItem reel, LikeStateConsumer consumer);
    }

    public interface LikeStateConsumer {
        void accept(boolean liked);
    }

    private final List<ReelItem> reels;
    private final OnReelActionListener listener;
    private final Map<String, Boolean> likedCache = new HashMap<>();
    private final Map<Integer, ReelVH> holders = new HashMap<>();
    private ExoPlayer activePlayer;
    private int activePosition = -1;
    private boolean isMuted = false;

    public ReelsAdapter(List<ReelItem> reels, OnReelActionListener listener) {
        this.reels = reels;
        this.listener = listener;
    }

    public void updateLikedCache(String reelId, boolean liked) {
        likedCache.put(reelId, liked);
        for (int i = 0; i < reels.size(); i++) {
            if (reels.get(i).id.equals(reelId)) {
                notifyItemChanged(i);
                break;
            }
        }
    }

    public void playAtPosition(Context context, int position) {
        if (position < 0 || position >= reels.size()) return;
        if (activePosition == position && activePlayer != null) {
            activePlayer.setPlayWhenReady(true);
            return;
        }
        releasePlayer();
        activePosition = position;
        ReelVH holder = holders.get(position);
        if (holder == null) return;

        ReelItem reel = reels.get(position);
        activePlayer = new ExoPlayer.Builder(context).build();
        activePlayer.setRepeatMode(Player.REPEAT_MODE_ONE);
        activePlayer.setVolume(isMuted ? 0f : 1f);
        holder.playerView.setPlayer(activePlayer);
        activePlayer.setMediaItem(MediaItem.fromUri(reel.videoUrl));
        activePlayer.prepare();
        activePlayer.setPlayWhenReady(true);
    }

    public void pausePlayer() {
        if (activePlayer != null) {
            activePlayer.setPlayWhenReady(false);
        }
    }

    public void releasePlayer() {
        for (ReelVH vh : holders.values()) {
            vh.playerView.setPlayer(null);
        }
        if (activePlayer != null) {
            activePlayer.release();
            activePlayer = null;
        }
        activePosition = -1;
    }

    @NonNull
    @Override
    public ReelVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_reel, parent, false);
        return new ReelVH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReelVH holder, int position) {
        holders.put(position, holder);
        ReelItem reel = reels.get(position);

        holder.authorName.setText("@" + reel.authorName);
        holder.caption.setText(reel.caption);
        holder.likesCount.setText(TimeFormatUtil.formatCount(reel.likesCount));
        holder.commentsCount.setText(TimeFormatUtil.formatCount(reel.commentsCount));
        holder.coins.setText("🪙 " + TimeFormatUtil.formatCount(reel.coinsAwarded));
        holder.music.setText("🎵 " + (reel.musicTitle != null && !reel.musicTitle.isEmpty() ? reel.musicTitle : "Original Sound"));

        if (reel.hashtags != null && !reel.hashtags.isEmpty()) {
            holder.hashtags.setVisibility(View.VISIBLE);
            holder.hashtags.setText(String.join("  ", reel.hashtags));
        } else {
            holder.hashtags.setVisibility(View.GONE);
        }

        if (reel.trendingSticker != null && !reel.trendingSticker.trim().isEmpty()) {
            holder.sticker.setVisibility(View.VISIBLE);
            holder.sticker.setText(reel.trendingSticker);
        } else {
            holder.sticker.setVisibility(View.GONE);
        }

        Glide.with(holder.avatar)
                .load(reel.authorAvatar)
                .placeholder(R.drawable.img_default_avatar)
                .error(R.drawable.img_default_avatar)
                .into(holder.avatar);

        holder.btnMute.setImageResource(isMuted ? R.drawable.ic_volume_off : R.drawable.ic_volume_on);
        holder.btnMute.setOnClickListener(v -> {
            isMuted = !isMuted;
            if (activePlayer != null) {
                activePlayer.setVolume(isMuted ? 0f : 1f);
            }
            holder.btnMute.setImageResource(isMuted ? R.drawable.ic_volume_off : R.drawable.ic_volume_on);
        });

        holder.playerView.setOnClickListener(v -> {
            if (activePlayer != null) {
                activePlayer.setPlayWhenReady(!activePlayer.getPlayWhenReady());
            }
        });

        Boolean cached = likedCache.get(reel.id);
        if (cached == null) {
            holder.likeIcon.setImageResource(R.drawable.ic_heart_outline);
            listener.checkLikedState(reel, liked -> {
                likedCache.put(reel.id, liked);
                holder.likeIcon.setImageResource(liked ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
            });
        } else {
            holder.likeIcon.setImageResource(cached ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
        }

        holder.btnLike.setOnClickListener(v -> {
            boolean currentlyLiked = Boolean.TRUE.equals(likedCache.get(reel.id));
            listener.onLikeClicked(reel, currentlyLiked);
        });
        holder.btnComment.setOnClickListener(v -> listener.onCommentClicked(reel));
        holder.btnShare.setOnClickListener(v -> listener.onShareClicked(reel));

        if (position == activePosition && activePlayer != null) {
            holder.playerView.setPlayer(activePlayer);
        }
    }

    @Override
    public void onViewRecycled(@NonNull ReelVH holder) {
        super.onViewRecycled(holder);
        holder.playerView.setPlayer(null);
    }

    @Override
    public int getItemCount() {
        return reels.size();
    }

    static class ReelVH extends RecyclerView.ViewHolder {
        final PlayerView playerView;
        final ImageView avatar, likeIcon;
        final ImageButton btnMute, btnShare;
        final View btnLike, btnComment;
        final TextView sticker, authorName, caption, hashtags, music, likesCount, commentsCount, coins;

        ReelVH(@NonNull View itemView) {
            super(itemView);
            playerView = itemView.findViewById(R.id.reelPlayerView);
            avatar = itemView.findViewById(R.id.imgReelAuthorAvatar);
            sticker = itemView.findViewById(R.id.txtReelSticker);
            btnMute = itemView.findViewById(R.id.btnReelMute);
            btnLike = itemView.findViewById(R.id.btnReelLike);
            likeIcon = itemView.findViewById(R.id.imgReelLikeIcon);
            likesCount = itemView.findViewById(R.id.txtReelLikesCount);
            btnComment = itemView.findViewById(R.id.btnReelComment);
            commentsCount = itemView.findViewById(R.id.txtReelCommentsCount);
            btnShare = itemView.findViewById(R.id.btnReelShare);
            coins = itemView.findViewById(R.id.txtReelCoins);
            authorName = itemView.findViewById(R.id.txtReelAuthorName);
            caption = itemView.findViewById(R.id.txtReelCaption);
            hashtags = itemView.findViewById(R.id.txtReelHashtags);
            music = itemView.findViewById(R.id.txtReelMusic);
        }
    }
}
