package com.example.ui.chat;

import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.databinding.ItemChatMessageBinding;
import com.example.model.ChatMessage;
import com.example.util.TimeFormatUtil;

import java.util.ArrayList;
import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    private final List<ChatMessage> messages = new ArrayList<>();
    private final String currentUid;

    public MessageAdapter(@NonNull String currentUid) {
        this.currentUid = currentUid;
    }

    public void submitList(@NonNull List<ChatMessage> newMessages) {
        messages.clear();
        messages.addAll(newMessages);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemChatMessageBinding binding = ItemChatMessageBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new MessageViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        holder.bind(messages.get(position));
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    class MessageViewHolder extends RecyclerView.ViewHolder {
        private final ItemChatMessageBinding binding;

        MessageViewHolder(ItemChatMessageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ChatMessage msg) {
            boolean isMine = currentUid.equals(msg.senderId);
            binding.messageRowRoot.setGravity(isMine ? Gravity.END : Gravity.START);
            binding.messageBubble.setBackgroundResource(
                    isMine ? R.drawable.bg_chat_bubble_mine : R.drawable.bg_chat_bubble_other);

            if (isMine) {
                binding.txtMessageSender.setVisibility(View.GONE);
            } else {
                binding.txtMessageSender.setVisibility(View.VISIBLE);
                binding.txtMessageSender.setText(
                        !TextUtils.isEmpty(msg.senderName) ? msg.senderName : "Creator");
            }

            if (!TextUtils.isEmpty(msg.stickerText)) {
                binding.txtMessageSticker.setVisibility(View.VISIBLE);
                binding.txtMessageSticker.setText(msg.stickerText);
            } else {
                binding.txtMessageSticker.setVisibility(View.GONE);
            }

            if (!TextUtils.isEmpty(msg.text)) {
                binding.txtMessageBody.setVisibility(View.VISIBLE);
                binding.txtMessageBody.setText(msg.text);
            } else {
                binding.txtMessageBody.setVisibility(View.GONE);
            }

            binding.txtMessageTime.setText(TimeFormatUtil.formatClock(msg.createdAt));
        }
    }
}
