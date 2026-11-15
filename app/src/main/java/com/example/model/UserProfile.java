package com.example.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.PropertyName;
import com.google.firebase.firestore.ServerTimestamp;

@IgnoreExtraProperties
public class UserProfile {
    @DocumentId
    public String id = "";
    public String userId = "";
    public String displayName = "";
    public String username = "";
    public String avatarUrl = "";
    public String bio = "";
    @PropertyName("isDiscoverable")
    public boolean isDiscoverable = true;
    public long coinsBalance = 0L;
    public long withdrawnCoins = 0L;
    public long totalLikesReceived = 0L;
    @ServerTimestamp
    public Timestamp createdAt;
    @ServerTimestamp
    public Timestamp updatedAt;

    public UserProfile() {}

    @PropertyName("isDiscoverable")
    public boolean isDiscoverable() {
        return isDiscoverable;
    }

    @PropertyName("isDiscoverable")
    public void setDiscoverable(boolean discoverable) {
        isDiscoverable = discoverable;
    }

    public long getAvailableCoins() {
        long avail = coinsBalance - withdrawnCoins;
        return Math.max(0L, avail);
    }
}
