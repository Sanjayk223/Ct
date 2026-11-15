package com.example.ui.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.databinding.ItemUserPickBinding;
import com.example.model.UserProfile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UserPickAdapter extends RecyclerView.Adapter<UserPickAdapter.UserViewHolder> {

    public interface OnSingleUserPickListener {
        void onUserPicked(UserProfile user);
    }

    private final List<UserProfile> users = new ArrayList<>();
    private final Set<String> selectedIds = new HashSet<>();
    private final boolean multiSelect;
    private final OnSingleUserPickListener singlePickListener;

    public UserPickAdapter(
            @NonNull List<UserProfile> users,
            boolean multiSelect,
            OnSingleUserPickListener singlePickListener
    ) {
        this.users.addAll(users);
        this.multiSelect = multiSelect;
        this.singlePickListener = singlePickListener;
    }

    @NonNull
    public List<UserProfile> getSelectedUsers() {
        List<UserProfile> selected = new ArrayList<>();
        for (UserProfile u : users) {
            if (selectedIds.contains(u.userId)) {
                selected.add(u);
            }
        }
        return selected;
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemUserPickBinding binding = ItemUserPickBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new UserViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        holder.bind(users.get(position));
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    class UserViewHolder extends RecyclerView.ViewHolder {
        private final ItemUserPickBinding binding;

        UserViewHolder(ItemUserPickBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(UserProfile user) {
            String displayName = (user.displayName != null && !user.displayName.isEmpty())
                    ? user.displayName : "Vibe Creator";
            String username = (user.username != null && !user.username.isEmpty())
                    ? "@" + user.username : "";

            binding.txtPickUserName.setText(displayName);
            binding.txtPickUsername.setText(username);

            if (multiSelect) {
                binding.chkUserPick.setVisibility(View.VISIBLE);
                binding.chkUserPick.setOnCheckedChangeListener(null);
                binding.chkUserPick.setChecked(selectedIds.contains(user.userId));
                binding.chkUserPick.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        selectedIds.add(user.userId);
                    } else {
                        selectedIds.remove(user.userId);
                    }
                });
                binding.getRoot().setOnClickListener(v -> binding.chkUserPick.toggle());
            } else {
                binding.chkUserPick.setVisibility(View.GONE);
                binding.getRoot().setOnClickListener(v -> {
                    if (singlePickListener != null) {
                        singlePickListener.onUserPicked(user);
                    }
                });
            }
        }
    }
}
