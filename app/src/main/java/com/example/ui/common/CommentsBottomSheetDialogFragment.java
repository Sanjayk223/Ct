package com.example.ui.common;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.data.VibeReelRepository;
import com.example.databinding.DialogCommentsSheetBinding;
import com.example.model.CommentItem;
import com.example.util.TimeFormatUtil;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class CommentsBottomSheetDialogFragment extends BottomSheetDialogFragment {

    private static final String ARG_COLLECTION = "collection";
    private static final String ARG_TARGET_ID = "targetId";

    private DialogCommentsSheetBinding binding;
    private VibeReelRepository repository;
    private ListenerRegistration registration;
    private final List<CommentItem> comments = new ArrayList<>();
    private CommentAdapter adapter;

    public static CommentsBottomSheetDialogFragment newInstance(String collectionName, String targetId) {
        CommentsBottomSheetDialogFragment frag = new CommentsBottomSheetDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_COLLECTION, collectionName);
        args.putString(ARG_TARGET_ID, targetId);
        frag.setArguments(args);
        return frag;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogCommentsSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = new VibeReelRepository(requireContext());
        String collection = requireArguments().getString(ARG_COLLECTION, "posts");
        String targetId = requireArguments().getString(ARG_TARGET_ID, "");

        adapter = new CommentAdapter(comments);
        binding.rvComments.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvComments.setAdapter(adapter);

        registration = repository.observeComments(collection, targetId, new VibeReelRepository.RealtimeListener<>() {
            @Override
            public void onUpdate(List<CommentItem> data) {
                comments.clear();
                comments.addAll(data);
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onError(Exception error) {
                if (isAdded()) {
                    Toast.makeText(requireContext(), "Failed to load comments", Toast.LENGTH_SHORT).show();
                }
            }
        });

        binding.btnPostCommentSend.setOnClickListener(v -> {
            String text = binding.edtCommentInput.getText() != null ? binding.edtCommentInput.getText().toString().trim() : "";
            if (text.isEmpty()) return;
            binding.btnPostCommentSend.setEnabled(false);
            repository.addComment(collection, targetId, text, new VibeReelRepository.RepoCallback<>() {
                @Override
                public void onSuccess(Void result) {
                    if (isAdded()) {
                        binding.edtCommentInput.setText("");
                        binding.btnPostCommentSend.setEnabled(true);
                    }
                }

                @Override
                public void onError(Exception error) {
                    if (isAdded()) {
                        binding.btnPostCommentSend.setEnabled(true);
                        Toast.makeText(requireContext(), "Comment failed: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            });
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        binding = null;
    }

    private static class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.VH> {
        private final List<CommentItem> items;

        CommentAdapter(List<CommentItem> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comment, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            CommentItem item = items.get(position);
            holder.author.setText(item.authorName);
            holder.time.setText(TimeFormatUtil.formatRelative(item.createdAt));
            holder.text.setText(item.text);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView author, time, text;
            VH(@NonNull View itemView) {
                super(itemView);
                author = itemView.findViewById(R.id.txtCommentAuthor);
                time = itemView.findViewById(R.id.txtCommentTime);
                text = itemView.findViewById(R.id.txtCommentText);
            }
        }
    }
}
