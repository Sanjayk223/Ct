package com.example.ui.chat;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.R;
import com.example.data.VibeReelRepository;
import com.example.databinding.DialogCreateChatBinding;
import com.example.databinding.FragmentChatsBinding;
import com.example.model.ChatRoom;
import com.example.model.UserProfile;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class ChatsFragment extends Fragment {

    private static final int FILTER_ALL = 0;
    private static final int FILTER_DIRECT = 1;
    private static final int FILTER_GROUPS = 2;

    private FragmentChatsBinding binding;
    private VibeReelRepository repository;
    private ChatRoomAdapter adapter;
    private ListenerRegistration chatsListener;

    private final List<ChatRoom> allRooms = new ArrayList<>();
    private int activeFilter = FILTER_ALL;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentChatsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = new VibeReelRepository(requireContext());

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String currentUid = user != null ? user.getUid() : "";

        adapter = new ChatRoomAdapter(currentUid, this::openChatRoom);
        binding.rvChats.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvChats.setAdapter(adapter);

        binding.chipGroupChatFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.contains(R.id.chipChatsDirect)) {
                activeFilter = FILTER_DIRECT;
            } else if (checkedIds.contains(R.id.chipChatsGroups)) {
                activeFilter = FILTER_GROUPS;
            } else {
                activeFilter = FILTER_ALL;
            }
            applyFilter();
        });

        binding.btnNewDm.setOnClickListener(v -> loadUsersAndShowDialog(false));
        binding.btnNewGroup.setOnClickListener(v -> loadUsersAndShowDialog(true));
    }

    @Override
    public void onStart() {
        super.onStart();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        chatsListener = repository.observeChats(user.getUid(), new VibeReelRepository.RealtimeListener<>() {
            @Override
            public void onUpdate(List<ChatRoom> data) {
                if (binding == null) return;
                allRooms.clear();
                allRooms.addAll(data);
                applyFilter();
            }

            @Override
            public void onError(Exception error) {
                if (isAdded()) {
                    Toast.makeText(requireContext(), "Failed to load chats: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public void onStop() {
        super.onStop();
        if (chatsListener != null) {
            chatsListener.remove();
            chatsListener = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void applyFilter() {
        if (binding == null) return;
        List<ChatRoom> filtered = new ArrayList<>();
        for (ChatRoom room : allRooms) {
            if (activeFilter == FILTER_DIRECT && room.isGroup) continue;
            if (activeFilter == FILTER_GROUPS && !room.isGroup) continue;
            filtered.add(room);
        }
        adapter.submitList(filtered);
        binding.txtEmptyChats.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void loadUsersAndShowDialog(boolean isGroup) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;
        String currentUid = currentUser.getUid();

        repository.fetchDiscoverableUsers(new VibeReelRepository.RepoCallback<>() {
            @Override
            public void onSuccess(List<UserProfile> users) {
                if (!isAdded()) return;
                List<UserProfile> others = new ArrayList<>();
                for (UserProfile u : users) {
                    if (u.userId != null && !u.userId.equals(currentUid)) {
                        others.add(u);
                    }
                }
                if (others.isEmpty()) {
                    Toast.makeText(requireContext(), "No other creators found yet. Invite a friend to sign in!", Toast.LENGTH_LONG).show();
                    return;
                }
                if (isGroup) {
                    showGroupDialog(others);
                } else {
                    showDirectMessageDialog(others);
                }
            }

            @Override
            public void onError(Exception error) {
                if (isAdded()) {
                    Toast.makeText(requireContext(), "Could not load creators: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void showDirectMessageDialog(@NonNull List<UserProfile> others) {
        DialogCreateChatBinding dialogBinding = DialogCreateChatBinding.inflate(getLayoutInflater());
        dialogBinding.edtGroupTitle.setVisibility(View.GONE);
        dialogBinding.rvPickUsers.setLayoutManager(new LinearLayoutManager(requireContext()));

        final AlertDialog[] dialogHolder = new AlertDialog[1];
        UserPickAdapter pickAdapter = new UserPickAdapter(others, false, pickedUser -> {
            if (dialogHolder[0] != null) {
                dialogHolder[0].dismiss();
            }
            repository.createOrOpenDirectChat(pickedUser, new VibeReelRepository.RepoCallback<>() {
                @Override
                public void onSuccess(ChatRoom room) {
                    if (isAdded()) openChatRoom(room);
                }

                @Override
                public void onError(Exception error) {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), "Error opening DM: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            });
        });
        dialogBinding.rvPickUsers.setAdapter(pickAdapter);

        dialogHolder[0] = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Start Direct Message")
                .setView(dialogBinding.getRoot())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showGroupDialog(@NonNull List<UserProfile> others) {
        DialogCreateChatBinding dialogBinding = DialogCreateChatBinding.inflate(getLayoutInflater());
        dialogBinding.edtGroupTitle.setVisibility(View.VISIBLE);
        dialogBinding.rvPickUsers.setLayoutManager(new LinearLayoutManager(requireContext()));

        UserPickAdapter pickAdapter = new UserPickAdapter(others, true, null);
        dialogBinding.rvPickUsers.setAdapter(pickAdapter);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Create Group Chat")
                .setView(dialogBinding.getRoot())
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create Squad", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = dialogBinding.edtGroupTitle.getText() != null
                    ? dialogBinding.edtGroupTitle.getText().toString().trim() : "";
            List<UserProfile> selected = pickAdapter.getSelectedUsers();

            if (title.isEmpty()) {
                dialogBinding.edtGroupTitle.setError("Enter a group name");
                return;
            }
            if (selected.isEmpty()) {
                Toast.makeText(requireContext(), "Select at least 1 member", Toast.LENGTH_SHORT).show();
                return;
            }

            dialog.dismiss();
            repository.createGroupChat(title, selected, new VibeReelRepository.RepoCallback<>() {
                @Override
                public void onSuccess(ChatRoom room) {
                    if (isAdded()) openChatRoom(room);
                }

                @Override
                public void onError(Exception error) {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), "Group creation failed: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }));

        dialog.show();
    }

    private void openChatRoom(@NonNull ChatRoom room) {
        Intent intent = new Intent(requireContext(), ChatRoomActivity.class);
        intent.putExtra(ChatRoomActivity.EXTRA_CHAT_ID, room.id);
        startActivity(intent);
    }
}
