package com.example.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.PropertyName;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.ArrayList;
import java.util.List;

@IgnoreExtraProperties
public class ReelItem {
    @DocumentId
    public String id = "";
    public String authorId = "";
    public String authorName = "";
    public String authorAvatar = "";
    public String videoUrl = "";
    public String thumbnailUrl = "";
    public String caption = "";
    public List<String> hashtags = new ArrayList<>();
    public String trendingSticker = "";
    public String musicTitle = "Original Sound";
    public long likesCount = 0L;
    public long commentsCount = 0L;
    public long coinsAwarded = 0L;
    @PropertyName("isPublished")
    public boolean isPublished = true;
    @ServerTimestamp
    public Timestamp createdAt;
    @ServerTimestamp
    public Timestamp updatedAt;

    public ReelItem() {}

    @PropertyName("isPublished")
    public boolean isPublished() {
        return isPublished;
    }

    @PropertyName("isPublished")
    public void setPublished(boolean published) {
        isPublished = published;
    }
}
