package com.example.ui.home;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.R;
import com.example.model.PostItem;
import com.example.util.TimeFormatUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PostAdapter extends RecyclerView.Adapter<PostAdapter.PostVH> {

    public interface OnPostActionListener {
        void onLikeClicked(PostItem post, boolean currentlyLiked);
        void onCommentClicked(PostItem post);
        void onShareClicked(PostItem post);
        void checkLikedState(PostItem post, LikeStateConsumer consumer);
    }

    public interface LikeStateConsumer {
        void accept(boolean liked);
    }

    private final List<PostItem> posts;
    private final OnPostActionListener listener;
    private final Map<String, Boolean> likedCache = new HashMap<>();
    private ExoPlayer activeInlinePlayer;
    private PlayerView activePlayerView;

    public PostAdapter(List<PostItem> posts, OnPostActionListener listener) {
        this.posts = posts;
        this.listener = listener;
    }

    public void updateLikedCache(String postId, boolean liked) {
        likedCache.put(postId, liked);
        for (int i = 0; i < posts.size(); i++) {
            if (posts.get(i).id.equals(postId)) {
                notifyItemChanged(i);
                break;
            }
        }
    }

    public void releaseInlinePlayer() {
        if (activePlayerView != null) {
            activePlayerView.setPlayer(null);
            activePlayerView.setVisibility(View.GONE);
            activePlayerView = null;
        }
        if (activeInlinePlayer != null) {
            activeInlinePlayer.release();
            activeInlinePlayer = null;
        }
    }

    @NonNull
    @Override
    public PostVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_post, parent, false);
        return new PostVH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PostVH holder, int position) {
        PostItem post = posts.get(position);
        holder.authorName.setText(post.authorName);
        holder.time.setText(TimeFormatUtil.formatRelative(post.createdAt));
        holder.coinsEarned.setText("🪙 " + TimeFormatUtil.formatCount(post.coinsAwarded));
        holder.likesCount.setText(TimeFormatUtil.formatCount(post.likesCount));
        holder.commentsCount.setText(TimeFormatUtil.formatCount(post.commentsCount));
        holder.caption.setText(post.caption);

        if (post.hashtags != null && !post.hashtags.isEmpty()) {
            holder.hashtags.setVisibility(View.VISIBLE);
            holder.hashtags.setText(String.join("  ", post.hashtags));
        } else {
            holder.hashtags.setVisibility(View.GONE);
        }

        if (post.trendingSticker != null && !post.trendingSticker.trim().isEmpty()) {
            holder.sticker.setVisibility(View.VISIBLE);
            holder.sticker.setText(post.trendingSticker);
        } else {
            holder.sticker.setVisibility(View.GONE);
        }

        Glide.with(holder.avatar)
                .load(post.authorAvatar)
                .placeholder(R.drawable.img_default_avatar)
                .error(R.drawable.img_default_avatar)
                .into(holder.avatar);

        holder.playerView.setPlayer(null);
        holder.playerView.setVisibility(View.GONE);
        holder.mediaImage.setVisibility(View.VISIBLE);

        Glide.with(holder.mediaImage)
                .load(post.mediaUrl)
                .placeholder(R.drawable.img_wallet_banner)
                .error(R.drawable.img_wallet_banner)
                .into(holder.mediaImage);

        boolean isVideo = "video".equalsIgnoreCase(post.mediaType);
        holder.btnPlayVideo.setVisibility(isVideo ? View.VISIBLE : View.GONE);
        holder.btnPlayVideo.setOnClickListener(v -> playInlineVideo(holder, post.mediaUrl));

        Boolean cachedLike = likedCache.get(post.id);
        if (cachedLike == null) {
            holder.likeIcon.setImageResource(R.drawable.ic_heart_outline);
            listener.checkLikedState(post, liked -> {
                likedCache.put(post.id, liked);
                holder.likeIcon.setImageResource(liked ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
            });
        } else {
            holder.likeIcon.setImageResource(cachedLike ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
        }

        holder.btnLike.setOnClickListener(v -> {
            boolean currentlyLiked = Boolean.TRUE.equals(likedCache.get(post.id));
            listener.onLikeClicked(post, currentlyLiked);
        });
        holder.btnComment.setOnClickListener(v -> listener.onCommentClicked(post));
        holder.btnShare.setOnClickListener(v -> listener.onShareClicked(post));
    }

    private void playInlineVideo(PostVH holder, String videoUrl) {
        releaseInlinePlayer();
        Context ctx = holder.itemView.getContext();
        activeInlinePlayer = new ExoPlayer.Builder(ctx).build();
        activePlayerView = holder.playerView;
        holder.mediaImage.setVisibility(View.GONE);
        holder.btnPlayVideo.setVisibility(View.GONE);
        holder.playerView.setVisibility(View.VISIBLE);
        holder.playerView.setPlayer(activeInlinePlayer);
        activeInlinePlayer.setMediaItem(MediaItem.fromUri(videoUrl));
        activeInlinePlayer.prepare();
        activeInlinePlayer.setPlayWhenReady(true);
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    static class PostVH extends RecyclerView.ViewHolder {
        final ImageView avatar, mediaImage, likeIcon;
        final PlayerView playerView;
        final ImageButton btnPlayVideo, btnShare;
        final View btnLike, btnComment;
        final TextView authorName, time, coinsEarned, sticker, likesCount, commentsCount, caption, hashtags;

        PostVH(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.imgPostAuthorAvatar);
            authorName = itemView.findViewById(R.id.txtPostAuthorName);
            time = itemView.findViewById(R.id.txtPostTime);
            coinsEarned = itemView.findViewById(R.id.txtPostCoinsEarned);
            mediaImage = itemView.findViewById(R.id.imgPostMedia);
            playerView = itemView.findViewById(R.id.postPlayerView);
            btnPlayVideo = itemView.findViewById(R.id.btnPlayPostVideo);
            sticker = itemView.findViewById(R.id.txtPostSticker);
            btnLike = itemView.findViewById(R.id.btnPostLike);
            likeIcon = itemView.findViewById(R.id.imgPostLikeIcon);
            likesCount = itemView.findViewById(R.id.txtPostLikesCount);
            btnComment = itemView.findViewById(R.id.btnPostComment);
            commentsCount = itemView.findViewById(R.id.txtPostCommentsCount);
            btnShare = itemView.findViewById(R.id.btnPostShare);
            caption = itemView.findViewById(R.id.txtPostCaption);
            hashtags = itemView.findViewById(R.id.txtPostHashtags);
        }
    }
}
