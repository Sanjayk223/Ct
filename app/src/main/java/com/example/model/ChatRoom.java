package com.example.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.PropertyName;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@IgnoreExtraProperties
public class ChatRoom {
    @DocumentId
    public String id = "";
    public String title = "";
    @PropertyName("isGroup")
    public boolean isGroup = false;
    public List<String> memberIds = new ArrayList<>();
    public Map<String, String> memberNames = new HashMap<>();
    public String lastMessage = "";
    public String lastSenderName = "";
    public String createdBy = "";
    @ServerTimestamp
    public Timestamp createdAt;
    @ServerTimestamp
    public Timestamp updatedAt;

    public ChatRoom() {}

    @PropertyName("isGroup")
    public boolean isGroup() {
        return isGroup;
    }

    @PropertyName("isGroup")
    public void setGroup(boolean group) {
        isGroup = group;
    }

    public String resolveDisplayTitle(String currentUid) {
        if (isGroup && title != null && !title.trim().isEmpty()) {
            return title;
        }
        if (memberNames != null) {
            for (Map.Entry<String, String> entry : memberNames.entrySet()) {
                if (!entry.getKey().equals(currentUid) && entry.getValue() != null && !entry.getValue().isEmpty()) {
                    return entry.getValue();
                }
            }
        }
        return (title != null && !title.isEmpty()) ? title : "Direct Chat";
    }
}
