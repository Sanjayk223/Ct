package com.example.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.ArrayList;
import java.util.List;

@IgnoreExtraProperties
public class ChatMessage {
    @DocumentId
    public String id = "";
    public String chatId = "";
    public String senderId = "";
    public String senderName = "";
    public String senderAvatar = "";
    public String text = "";
    public String stickerText = "";
    public String mediaUrl = "";
    public List<String> memberIds = new ArrayList<>();
    @ServerTimestamp
    public Timestamp createdAt;

    public ChatMessage() {}
}
