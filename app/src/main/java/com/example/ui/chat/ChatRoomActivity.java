package com.example.ui.chat;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.R;
import com.example.data.VibeReelRepository;
import com.example.databinding.ActivityChatRoomBinding;
import com.example.model.ChatMessage;
import com.example.model.ChatRoom;
import com.example.util.HashtagStickerCatalog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;

public class ChatRoomActivity extends AppCompatActivity {

    public static final String EXTRA_CHAT_ID = "extra_chat_id";

    private ActivityChatRoomBinding binding;
    private VibeReelRepository repository;
    private MessageAdapter adapter;
    private ListenerRegistration messagesListener;

    private String chatId;
    private String currentUid;
    private ChatRoom activeRoom;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityChatRoomBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.chatRoomRoot, (v, windowInsets) -> {
            Insets sysBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            int bottomPad = Math.max(sysBars.bottom, ime.bottom);
            v.setPadding(sysBars.left, sysBars.top, sysBars.right, bottomPad);
            return WindowInsetsCompat.CONSUMED;
        });

        chatId = getIntent().getStringExtra(EXTRA_CHAT_ID);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (TextUtils.isEmpty(chatId) || user == null) {
            finish();
            return;
        }
        currentUid = user.getUid();
        repository = new VibeReelRepository(this);

        binding.btnChatBack.setOnClickListener(v -> finish());

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        binding.rvMessages.setLayoutManager(layoutManager);
        adapter = new MessageAdapter(currentUid);
        binding.rvMessages.setAdapter(adapter);

        setupQuickStickers();

        binding.btnSendMessage.setOnClickListener(v -> {
            String text = binding.edtMessageInput.getText() != null
                    ? binding.edtMessageInput.getText().toString().trim() : "";
            if (text.isEmpty()) return;
            sendMessage(text, null);
        });

        loadChatRoomMetadata();
    }

    private void loadChatRoomMetadata() {
        repository.getChatRoom(chatId, new VibeReelRepository.RepoCallback<>() {
            @Override
            public void onSuccess(ChatRoom room) {
                activeRoom = room;
                binding.txtRoomTitle.setText(room.resolveDisplayTitle(currentUid));
                if (room.isGroup) {
                    int count = room.memberIds != null ? room.memberIds.size() : 0;
                    binding.txtRoomSubtitle.setText("Group Chat • " + count + " members");
                } else {
                    binding.txtRoomSubtitle.setText("Direct Message");
                }
            }

            @Override
            public void onError(Exception error) {
                Toast.makeText(ChatRoomActivity.this, "Could not load chat: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void setupQuickStickers() {
        float density = getResources().getDisplayMetrics().density;
        int minHeightPx = Math.round(48 * density);
        int hPad = Math.round(14 * density);
        int marginEnd = Math.round(8 * density);

        for (String sticker : HashtagStickerCatalog.TRENDING_STICKERS) {
            TextView pill = new TextView(this);
            pill.setText(sticker);
            pill.setTextColor(ContextCompat.getColor(this, R.color.white));
            pill.setTextSize(13f);
            pill.setGravity(Gravity.CENTER);
            pill.setMinHeight(minHeightPx);
            pill.setPadding(hPad, 0, hPad, 0);
            pill.setBackgroundResource(R.drawable.bg_sticker_pill);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(marginEnd);
            pill.setLayoutParams(lp);

            pill.setOnClickListener(v -> {
                String typed = binding.edtMessageInput.getText() != null
                        ? binding.edtMessageInput.getText().toString().trim() : "";
                sendMessage(typed, sticker);
            });
            binding.chatQuickStickers.addView(pill);
        }
    }

    private void sendMessage(@NonNull String text, @Nullable String stickerText) {
        if (activeRoom == null) {
            Toast.makeText(this, "Connecting to chat room…", Toast.LENGTH_SHORT).show();
            return;
        }
        binding.btnSendMessage.setEnabled(false);
        repository.sendMessage(activeRoom, text, stickerText, null, new VibeReelRepository.RepoCallback<>() {
            @Override
            public void onSuccess(Void unused) {
                binding.btnSendMessage.setEnabled(true);
                binding.edtMessageInput.setText("");
            }

            @Override
            public void onError(Exception error) {
                binding.btnSendMessage.setEnabled(true);
                Toast.makeText(ChatRoomActivity.this, "Send failed: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (chatId != null && currentUid != null) {
            messagesListener = repository.observeMessages(chatId, currentUid, new VibeReelRepository.RealtimeListener<>() {
                @Override
                public void onUpdate(List<ChatMessage> data) {
                    adapter.submitList(data);
                    if (!data.isEmpty()) {
                        binding.rvMessages.scrollToPosition(data.size() - 1);
                    }
                }

                @Override
                public void onError(Exception error) {
                    Toast.makeText(ChatRoomActivity.this, "Messages sync error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (messagesListener != null) {
            messagesListener.remove();
            messagesListener = null;
        }
    }
}
