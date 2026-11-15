package com.example.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.ServerTimestamp;

@IgnoreExtraProperties
public class CommentItem {
    @DocumentId
    public String id = "";
    public String targetId = "";
    public String authorId = "";
    public String authorName = "";
    public String authorAvatar = "";
    public String text = "";
    @ServerTimestamp
    public Timestamp createdAt;

    public CommentItem() {}
}
