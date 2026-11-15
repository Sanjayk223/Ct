package com.example.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.PropertyName;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.ArrayList;
import java.util.List;

@IgnoreExtraProperties
public class StoryItem {
    @DocumentId
    public String id = "";
    public String authorId = "";
    public String authorName = "";
    public String authorAvatar = "";
    public String mediaUrl = "";
    public String mediaType = "image";
    public String caption = "";
    public String stickerText = "";
    public List<String> hashtags = new ArrayList<>();
    public long viewersCount = 0L;
    @PropertyName("isActive")
    public boolean isActive = true;
    @ServerTimestamp
    public Timestamp createdAt;

    public StoryItem() {}

    @PropertyName("isActive")
    public boolean isActive() {
        return isActive;
    }

    @PropertyName("isActive")
    public void setActive(boolean active) {
        isActive = active;
    }
}
