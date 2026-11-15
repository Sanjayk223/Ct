package com.example.ui.chat;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.databinding.ItemChatRoomBinding;
import com.example.model.ChatRoom;
import com.example.util.TimeFormatUtil;

import java.util.ArrayList;
import java.util.List;

public class ChatRoomAdapter extends RecyclerView.Adapter<ChatRoomAdapter.ChatViewHolder> {

    public interface OnChatClickListener {
        void onChatClick(ChatRoom room);
    }

    private final List<ChatRoom> rooms = new ArrayList<>();
    private final String currentUid;
    private final OnChatClickListener listener;

    public ChatRoomAdapter(@NonNull String currentUid, @NonNull OnChatClickListener listener) {
        this.currentUid = currentUid;
        this.listener = listener;
    }

    public void submitList(@NonNull List<ChatRoom> newRooms) {
        rooms.clear();
        rooms.addAll(newRooms);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemChatRoomBinding binding = ItemChatRoomBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ChatViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        holder.bind(rooms.get(position));
    }

    @Override
    public int getItemCount() {
        return rooms.size();
    }

    class ChatViewHolder extends RecyclerView.ViewHolder {
        private final ItemChatRoomBinding binding;

        ChatViewHolder(ItemChatRoomBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ChatRoom room) {
            binding.imgChatRoomIcon.setImageResource(
                    room.isGroup ? R.drawable.ic_group : R.drawable.ic_chat);
            binding.txtChatRoomTitle.setText(room.resolveDisplayTitle(currentUid));

            if (room.isGroup) {
                int count = room.memberIds != null ? room.memberIds.size() : 0;
                binding.txtChatRoomBadge.setText("GROUP • " + count);
            } else {
                binding.txtChatRoomBadge.setText("DM");
            }

            String lastMsg = room.lastMessage != null ? room.lastMessage : "";
            if (room.lastSenderName != null && !room.lastSenderName.isEmpty() && !lastMsg.isEmpty()) {
                binding.txtChatLastMessage.setText(room.lastSenderName + ": " + lastMsg);
            } else {
                binding.txtChatLastMessage.setText(lastMsg.isEmpty() ? "Tap to message" : lastMsg);
            }

            binding.txtChatRoomTime.setText(TimeFormatUtil.formatRelative(room.updatedAt));
            binding.getRoot().setOnClickListener(v -> listener.onChatClick(room));
        }
    }
}
