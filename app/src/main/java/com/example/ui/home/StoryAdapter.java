package com.example.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.R;
import com.example.model.StoryItem;

import java.util.List;

public class StoryAdapter extends RecyclerView.Adapter<StoryAdapter.StoryVH> {

    public interface OnStoryClickListener {
        void onAddStoryClicked();
        void onStoryClicked(int storyIndex);
    }

    private final List<StoryItem> stories;
    private final OnStoryClickListener listener;

    public StoryAdapter(List<StoryItem> stories, OnStoryClickListener listener) {
        this.stories = stories;
        this.listener = listener;
    }

    @NonNull
    @Override
    public StoryVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_story, parent, false);
        return new StoryVH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StoryVH holder, int position) {
        if (position == 0) {
            holder.name.setText(R.string.your_story);
            holder.badgeAdd.setVisibility(View.VISIBLE);
            holder.avatar.setImageResource(R.drawable.img_default_avatar);
            holder.itemView.setOnClickListener(v -> listener.onAddStoryClicked());
        } else {
            int storyIndex = position - 1;
            StoryItem story = stories.get(storyIndex);
            holder.name.setText(story.authorName);
            holder.badgeAdd.setVisibility(View.GONE);
            String imgSource = (story.authorAvatar != null && !story.authorAvatar.isEmpty())
                    ? story.authorAvatar : story.mediaUrl;
            Glide.with(holder.avatar)
                    .load(imgSource)
                    .placeholder(R.drawable.img_default_avatar)
                    .error(R.drawable.img_default_avatar)
                    .into(holder.avatar);
            holder.itemView.setOnClickListener(v -> listener.onStoryClicked(storyIndex));
        }
    }

    @Override
    public int getItemCount() {
        return stories.size() + 1;
    }

    static class StoryVH extends RecyclerView.ViewHolder {
        final ImageView avatar;
        final TextView badgeAdd;
        final TextView name;

        StoryVH(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.imgStoryAvatar);
            badgeAdd = itemView.findViewById(R.id.badgeAddStory);
            name = itemView.findViewById(R.id.txtStoryName);
        }
    }
}
