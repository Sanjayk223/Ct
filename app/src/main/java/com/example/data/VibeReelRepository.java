package com.example.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.R;
import com.example.data.FirestoreErrorHandler.OperationType;
import com.example.model.ChatMessage;
import com.example.model.ChatRoom;
import com.example.model.CommentItem;
import com.example.model.PostItem;
import com.example.model.ReelItem;
import com.example.model.StoryItem;
import com.example.model.UserProfile;
import com.example.model.WithdrawalRequest;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class VibeReelRepository {

    public interface RepoCallback<T> {
        void onSuccess(T result);
        void onError(Exception error);
    }

    public interface RealtimeListener<T> {
        void onUpdate(T data);
        void onError(Exception error);
    }

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    public VibeReelRepository(@NonNull FirebaseFirestore db, @NonNull FirebaseAuth auth) {
        this.db = db;
        this.auth = auth;
    }

    public VibeReelRepository(@NonNull Context context) {
        this(
            FirebaseFirestore.getInstance(
                context.getApplicationContext().getString(R.string.firestore_database_id)
            ),
            FirebaseAuth.getInstance()
        );
    }

    @NonNull
    public FirebaseFirestore getDb() {
        return db;
    }

    @NonNull
    private String requireUserId() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || user.getUid().isEmpty()) {
            throw new IllegalStateException("User must be signed in with Google before accessing Firestore.");
        }
        return user.getUid();
    }

    @NonNull
    private String currentUserName() {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null && user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
            return user.getDisplayName().trim();
        }
        return "Creator";
    }

    @NonNull
    private String currentUserAvatar() {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null && user.getPhotoUrl() != null) {
            return user.getPhotoUrl().toString();
        }
        return "";
    }

    // ---- Users & Profiles ----

    public void ensureUserProfile(@NonNull FirebaseUser firebaseUser, @NonNull RepoCallback<UserProfile> callback) {
        String uid = firebaseUser.getUid();
        DocumentReference userRef = db.collection("users").document(uid);
        userRef.get()
            .addOnSuccessListener(snapshot -> {
                if (snapshot.exists()) {
                    UserProfile existing = snapshot.toObject(UserProfile.class, ServerTimestampBehavior.ESTIMATE);
                    callback.onSuccess(existing);
                } else {
                    String displayName = firebaseUser.getDisplayName() != null && !firebaseUser.getDisplayName().trim().isEmpty()
                            ? firebaseUser.getDisplayName().trim() : "Vibe Creator";
                    String cleanHandle = displayName.toLowerCase(Locale.US).replaceAll("[^a-z0-9_]", "");
                    if (cleanHandle.length() < 2) cleanHandle = "creator";
                    String username = cleanHandle.substring(0, Math.min(cleanHandle.length(), 18)) + "_" + uid.substring(0, Math.min(4, uid.length()));
                    String avatar = firebaseUser.getPhotoUrl() != null ? firebaseUser.getPhotoUrl().toString() : "";

                    Map<String, Object> payload = new HashMap<>();
                    payload.put("userId", uid);
                    payload.put("displayName", displayName);
                    payload.put("username", username);
                    if (!avatar.isEmpty()) payload.put("avatarUrl", avatar);
                    payload.put("bio", "Creating Reels & earning coins on VibeReel");
                    payload.put("isDiscoverable", true);
                    payload.put("coinsBalance", 0L);
                    payload.put("withdrawnCoins", 0L);
                    payload.put("totalLikesReceived", 0L);
                    payload.put("createdAt", FieldValue.serverTimestamp());
                    payload.put("updatedAt", FieldValue.serverTimestamp());

                    userRef.set(payload)
                        .addOnSuccessListener(unused -> {
                            UserProfile created = new UserProfile();
                            created.id = uid;
                            created.userId = uid;
                            created.displayName = displayName;
                            created.username = username;
                            created.avatarUrl = avatar;
                            created.bio = "Creating Reels & earning coins on VibeReel";
                            created.isDiscoverable = true;
                            created.createdAt = Timestamp.now();
                            created.updatedAt = Timestamp.now();
                            callback.onSuccess(created);
                        })
                        .addOnFailureListener(e -> {
                            FirestoreErrorHandler.handleFirestoreError(auth, e, OperationType.CREATE, userRef.getPath());
                            callback.onError(e);
                        });
                }
            })
            .addOnFailureListener(e -> {
                FirestoreErrorHandler.handleFirestoreError(auth, e, OperationType.GET, userRef.getPath());
                callback.onError(e);
            });
    }

    public ListenerRegistration observeCurrentUser(@NonNull String uid, @NonNull RealtimeListener<UserProfile> listener) {
        DocumentReference docRef = db.collection("users").document(uid);
        return docRef.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                FirestoreErrorHandler.handleFirestoreError(auth, error, OperationType.GET, docRef.getPath());
                listener.onError(error);
                return;
            }
            if (snapshot != null && snapshot.exists()) {
                UserProfile profile = snapshot.toObject(UserProfile.class, ServerTimestampBehavior.ESTIMATE);
                if (profile != null) {
                    listener.onUpdate(profile);
                }
            }
        });
    }

    public void updateProfile(@NonNull String displayName, @NonNull String username, @NonNull String bio, @NonNull RepoCallback<Void> callback) {
        String uid = requireUserId();
        DocumentReference docRef = db.collection("users").document(uid);
        Map<String, Object> updates = new HashMap<>();
        updates.put("displayName", displayName.trim());
        updates.put("username", username.trim());
        updates.put("bio", bio.trim());
        updates.put("isDiscoverable", true);
        updates.put("updatedAt", FieldValue.serverTimestamp());

        docRef.update(updates)
            .addOnSuccessListener(unused -> callback.onSuccess(null))
            .addOnFailureListener(e -> {
                FirestoreErrorHandler.handleFirestoreError(auth, e, OperationType.UPDATE, docRef.getPath());
                callback.onError(e);
            });
    }

    public void fetchDiscoverableUsers(@NonNull RepoCallback<List<UserProfile>> callback) {
        requireUserId();
        db.collection("users")
            .whereEqualTo("isDiscoverable", true)
            .limit(50)
            .get()
            .addOnSuccessListener(snapshots -> {
                List<UserProfile> list = new ArrayList<>();
                for (QueryDocumentSnapshot doc : snapshots) {
                    UserProfile u = doc.toObject(UserProfile.class, ServerTimestampBehavior.ESTIMATE);
                    list.add(u);
                }
                callback.onSuccess(list);
            })
            .addOnFailureListener(e -> {
                FirestoreErrorHandler.handleFirestoreError(auth, e, OperationType.LIST, "users");
                callback.onError(e);
            });
    }

    // ---- Stories ----

    public ListenerRegistration observeStories(@NonNull RealtimeListener<List<StoryItem>> listener) {
        requireUserId();
        return db.collection("stories")
            .whereEqualTo("isActive", true)
            .limit(40)
            .addSnapshotListener((snapshots, error) -> {
                if (error != null) {
                    FirestoreErrorHandler.handleFirestoreError(auth, error, OperationType.LIST, "stories");
                    listener.onError(error);
                    return;
                }
                List<StoryItem> list = new ArrayList<>();
                if (snapshots != null) {
                    for (QueryDocumentSnapshot doc : snapshots) {
                        list.add(doc.toObject(StoryItem.class, ServerTimestampBehavior.ESTIMATE));
                    }
                    Collections.sort(list, (a, b) -> compareTimestampsDesc(a.createdAt, b.createdAt));
                }
                listener.onUpdate(list);
            });
    }

    public void createStory(
            @NonNull String mediaUrl,
            @NonNull String mediaType,
            @Nullable String caption,
            @Nullable String stickerText,
            @NonNull List<String> hashtags,
            @NonNull RepoCallback<String> callback
    ) {
        String uid = requireUserId();
        DocumentReference docRef = db.collection("stories").document();
        Map<String, Object> payload = new HashMap<>();
        payload.put("authorId", uid);
        payload.put("authorName", currentUserName());
        String avatar = currentUserAvatar();
        if (!avatar.isEmpty()) payload.put("authorAvatar", avatar);
        payload.put("mediaUrl", mediaUrl);
        payload.put("mediaType", mediaType);
        if (caption != null && !caption.trim().isEmpty()) payload.put("caption", caption.trim());
        if (stickerText != null && !stickerText.trim().isEmpty()) payload.put("stickerText", stickerText.trim());
        payload.put("hashtags", limitHashtags(hashtags));
        payload.put("viewersCount", 0L);
        payload.put("isActive", true);
        payload.put("createdAt", FieldValue.serverTimestamp());

        docRef.set(payload)
            .addOnSuccessListener(unused -> callback.onSuccess(docRef.getId()))
            .addOnFailureListener(e -> {
                FirestoreErrorHandler.handleFirestoreError(auth, e, OperationType.CREATE, docRef.getPath());
                callback.onError(e);
            });
    }

    public void incrementStoryView(@NonNull String storyId) {
        if (auth.getCurrentUser() == null) return;
        DocumentReference ref = db.collection("stories").document(storyId);
        ref.update("viewersCount", FieldValue.increment(1L))
            .addOnFailureListener(e -> FirestoreErrorHandler.handleFirestoreError(auth, e, OperationType.UPDATE, ref.getPath()));
    }

    // ---- Posts ----

    public ListenerRegistration observePosts(@NonNull RealtimeListener<List<PostItem>> listener) {
        requireUserId();
        return db.collection("posts")
            .whereEqualTo("isPublished", true)
            .limit(50)
            .addSnapshotListener((snapshots, error) -> {
                if (error != null) {
                    FirestoreErrorHandler.handleFirestoreError(auth, error, OperationType.LIST, "posts");
                    listener.onError(error);
                    return;
                }
                List<PostItem> list = new ArrayList<>();
                if (snapshots != null) {
                    for (QueryDocumentSnapshot doc : snapshots) {
                        list.add(doc.toObject(PostItem.class, ServerTimestampBehavior.ESTIMATE));
                    }
                    Collections.sort(list, (a, b) -> compareTimestampsDesc(a.createdAt, b.createdAt));
                }
                listener.onUpdate(list);
            });
    }

    public void createPost(
            @NonNull String mediaUrl,
            @NonNull String mediaType,
            @NonNull String caption,
            @NonNull List<String> hashtags,
            @Nullable String trendingSticker,
            @NonNull RepoCallback<String> callback
    ) {
        String uid = requireUserId();
        DocumentReference docRef = db.collection("posts").document();
        Map<String, Object> payload = new HashMap<>();
        payload.put("authorId", uid);
        payload.put("authorName", currentUserName());
        String avatar = currentUserAvatar();
        if (!avatar.isEmpty()) payload.put("authorAvatar", avatar);
        payload.put("mediaUrl", mediaUrl);
        payload.put("mediaType", mediaType);
        payload.put("caption", caption.trim());
        payload.put("hashtags", limitHashtags(hashtags));
        if (trendingSticker != null && !trendingSticker.trim().isEmpty()) {
            payload.put("trendingSticker", trendingSticker.trim());
        }
        payload.put("likesCount", 0L);
        payload.put("commentsCount", 0L);
        payload.put("coinsAwarded", 0L);
        payload.put("isPublished", true);
        payload.put("createdAt", FieldValue.serverTimestamp());
        payload.put("updatedAt", FieldValue.serverTimestamp());

        docRef.set(payload)
            .addOnSuccessListener(unused -> callback.onSuccess(docRef.getId()))
            .addOnFailureListener(e -> {
                FirestoreErrorHandler.handleFirestoreError(auth, e, OperationType.CREATE, docRef.getPath());
                callback.onError(e);
            });
    }

    public void checkLiked(@NonNull String collectionName, @NonNull String targetId, @NonNull RepoCallback<Boolean> callback) {
        String uid = requireUserId();
        DocumentReference likeRef = db.collection(collectionName).document(targetId).collection("likes").document(uid);
        likeRef.get()
            .addOnSuccessListener(snap -> callback.onSuccess(snap.exists()))
            .addOnFailureListener(e -> {
                FirestoreErrorHandler.handleFirestoreError(auth, e, OperationType.GET, likeRef.getPath());
                callback.onError(e);
            });
    }

    public void togglePostLike(@NonNull PostItem post, boolean currentlyLiked, @NonNull RepoCallback<Boolean> callback) {
        String uid = requireUserId();
        DocumentReference postRef = db.collection("posts").document(post.id);
        DocumentReference likeRef = postRef.collection("likes").document(uid);
        DocumentReference authorRef = db.collection("users").document(post.authorId);

        WriteBatch batch = db.batch();
        if (currentlyLiked) {
            batch.delete(likeRef);
            Map<String, Object> postUpdate = new HashMap<>();
            postUpdate.put("likesCount", FieldValue.increment(-1L));
            postUpdate.put("coinsAwarded", FieldValue.increment(-10L));
            postUpdate.put("updatedAt", FieldValue.serverTimestamp());
            batch.update(postRef, postUpdate);

            Map<String, Object> userUpdate = new HashMap<>();
            userUpdate.put("coinsBalance", FieldValue.increment(-10L));
            userUpdate.put("totalLikesReceived", FieldValue.increment(-1L));
            userUpdate.put("updatedAt", FieldValue.serverTimestamp());
            batch.update(authorRef, userUpdate);
        } else {
            Map<String, Object> likePayload = new HashMap<>();
            likePayload.put("userId", uid);
            likePayload.put("targetId", post.id);
            likePayload.put("createdAt", FieldValue.serverTimestamp());
            batch.set(likeRef, likePayload);

            Map<String, Object> postUpdate = new HashMap<>();
            postUpdate.put("likesCount", FieldValue.increment(1L));
            postUpdate.put("coinsAwarded", FieldValue.increment(10L));
            postUpdate.put("updatedAt", FieldValue.serverTimestamp());
            batch.update(postRef, postUpdate);

            Map<String, Object> userUpdate = new HashMap<>();
            userUpdate.put("coinsBalance", FieldValue.increment(10L));
            userUpdate.put("totalLikesReceived", FieldValue.increment(1L));
            userUpdate.put("updatedAt", FieldValue.serverTimestamp());
            batch.update(authorRef, userUpdate);
        }

        batch.commit()
            .addOnSuccessListener(unused -> callback.onSuccess(!currentlyLiked))
            .addOnFailureListener(e -> {
                FirestoreErrorHandler.handleFirestoreError(auth, e, OperationType.WRITE, postRef.getPath());
                callback.onError(e);
            });
    }

    // ---- Reels ----

    public ListenerRegistration observeReels(@NonNull RealtimeListener<List<ReelItem>> listener) {
        requireUserId();
        return db.collection("reels")
            .whereEqualTo("isPublished", true)
            .limit(50)
            .addSnapshotListener((snapshots, error) -> {
                if (error != null) {
                    FirestoreErrorHandler.handleFirestoreError(auth, error, OperationType.LIST, "reels");
                    listener.onError(error);
                    return;
                }
                List<ReelItem> list = new ArrayList<>();
                if (snapshots != null) {
                    for (QueryDocumentSnapshot doc : snapshots) {
                        list.add(doc.toObject(ReelItem.class, ServerTimestampBehavior.ESTIMATE));
                    }
                    Collections.sort(list, (a, b) -> compareTimestampsDesc(a.createdAt, b.createdAt));
                }
                listener.onUpdate(list);
            });
    }

    public void createReel(
            @NonNull String videoUrl,
            @Nullable String thumbnailUrl,
            @NonNull String caption,
            @NonNull List<String> hashtags,
            @Nullable String trendingSticker,
            @Nullable String musicTitle,
            @NonNull RepoCallback<String> callback
    ) {
        String uid = requireUserId();
        DocumentReference docRef = db.collection("reels").document();
        Map<String, Object> payload = new HashMap<>();
        payload.put("authorId", uid);
        payload.put("authorName", currentUserName());
        String avatar = currentUserAvatar();
        if (!avatar.isEmpty()) payload.put("authorAvatar", avatar);
        payload.put("videoUrl", videoUrl);
        if (thumbnailUrl != null && !thumbnailUrl.trim().isEmpty()) payload.put("thumbnailUrl", thumbnailUrl.trim());
        payload.put("caption", caption.trim());
        payload.put("hashtags", limitHashtags(hashtags));
        if (trendingSticker != null && !trendingSticker.trim().isEmpty()) payload.put("trendingSticker", trendingSticker.trim());
        if (musicTitle != null && !musicTitle.trim().isEmpty()) payload.put("musicTitle", musicTitle.trim());
        payload.put("likesCount", 0L);
        payload.put("commentsCount", 0L);
        payload.put("coinsAwarded", 0L);
        payload.put("isPublished", true);
        payload.put("createdAt", FieldValue.serverTimestamp());
        payload.put("updatedAt", FieldValue.serverTimestamp());

        docRef.set(payload)
            .addOnSuccessListener(unused -> callback.onSuccess(docRef.getId()))
            .addOnFailureListener(e -> {
                FirestoreErrorHandler.handleFirestoreError(auth, e, OperationType.CREATE, docRef.getPath());
                callback.onError(e);
            });
    }

    public void toggleReelLike(@NonNull ReelItem reel, boolean currentlyLiked, @NonNull RepoCallback<Boolean> callback) {
        String uid = requireUserId();
        DocumentReference reelRef = db.collection("reels").document(reel.id);
        DocumentReference likeRef = reelRef.collection("likes").document(uid);
        DocumentReference authorRef = db.collection("users").document(reel.authorId);

        WriteBatch batch = db.batch();
        if (currentlyLiked) {
            batch.delete(likeRef);
            Map<String, Object> reelUpdate = new HashMap<>();
            reelUpdate.put("likesCount", FieldValue.increment(-1L));
            reelUpdate.put("coinsAwarded", FieldValue.increment(-10L));
            reelUpdate.put("updatedAt", FieldValue.serverTimestamp());
            batch.update(reelRef, reelUpdate);

            Map<String, Object> userUpdate = new HashMap<>();
            userUpdate.put("coinsBalance", FieldValue.increment(-10L));
            userUpdate.put("totalLikesReceived", FieldValue.increment(-1L));
            userUpdate.put("updatedAt", FieldValue.serverTimestamp());
            batch.update(authorRef, userUpdate);
        } else {
            Map<String, Object> likePayload = new HashMap<>();
            likePayload.put("userId", uid);
            likePayload.put("targetId", reel.id);
            likePayload.put("createdAt", FieldValue.serverTimestamp());
            batch.set(likeRef, likePayload);

            Map<String, Object> reelUpdate = new HashMap<>();
            reelUpdate.put("likesCount", FieldValue.increment(1L));
            reelUpdate.put("coinsAwarded", FieldValue.increment(10L));
            reelUpdate.put("updatedAt", FieldValue.serverTimestamp());
            batch.update(reelRef, reelUpdate);

            Map<String, Object> userUpdate = new HashMap<>();
            userUpdate.put("coinsBalance", FieldValue.increment(10L));
            userUpdate.put("totalLikesReceived", FieldValue.increment(1L));
            userUpdate.put("updatedAt", FieldValue.serverTimestamp());
            batch.update(authorRef, userUpdate);
        }

        batch.commit()
            .addOnSuccessListener(unused -> callback.onSuccess(!currentlyLiked))
            .addOnFailureListener(e -> {
                FirestoreErrorHandler.handleFirestoreError(auth, e, OperationType.WRITE, reelRef.getPath());
                callback.onError(e);
            });
    }

    // ---- Comments (Posts & Reels) ----

    public ListenerRegistration observeComments(@NonNull String collectionName, @NonNull String targetId, @NonNull RealtimeListener<List<CommentItem>> listener) {
        requireUserId();
        return db.collection(collectionName).document(targetId).collection("comments")
            .whereEqualTo("targetId", targetId)
            .limit(100)
            .addSnapshotListener((snapshots, error) -> {
                if (error != null) {
                    FirestoreErrorHandler.handleFirestoreError(auth, error, OperationType.LIST, collectionName + "/" + targetId + "/comments");
                    listener.onError(error);
                    return;
                }
                List<CommentItem> list = new ArrayList<>();
                if (snapshots != null) {
                    for (QueryDocumentSnapshot doc : snapshots) {
                        list.add(doc.toObject(CommentItem.class, ServerTimestampBehavior.ESTIMATE));
                    }
                    Collections.sort(list, (a, b) -> compareTimestampsAsc(a.createdAt, b.createdAt));
                }
                listener.onUpdate(list);
            });
    }

    public void addComment(@NonNull String collectionName, @NonNull String targetId, @NonNull String text, @NonNull RepoCallback<Void> callback) {
        String uid = requireUserId();
        DocumentReference parentRef = db.collection(collectionName).document(targetId);
        DocumentReference commentRef = parentRef.collection("comments").document();

        WriteBatch batch = db.batch();
        Map<String, Object> payload = new HashMap<>();
        payload.put("targetId", targetId);
        payload.put("authorId", uid);
        payload.put("authorName", currentUserName());
        String avatar = currentUserAvatar();
        if (!avatar.isEmpty()) payload.put("authorAvatar", avatar);
        payload.put("text", text.trim());
        payload.put("createdAt", FieldValue.serverTimestamp());
        batch.set(commentRef, payload);

        Map<String, Object> parentUpdate = new HashMap<>();
        parentUpdate.put("commentsCount", FieldValue.increment(1L));
        parentUpdate.put("updatedAt", FieldValue.serverTimestamp());
        batch.update(parentRef, parentUpdate);

        batch.commit()
            .addOnSuccessListener(unused -> callback.onSuccess(null))
            .addOnFailureListener(e -> {
                FirestoreErrorHandler.handleFirestoreError(auth, e, OperationType.CREATE, commentRef.getPath());
                callback.onError(e);
            });
    }

    // ---- Direct & Group Chats ----

    public ListenerRegistration observeChats(@NonNull String uid, @NonNull RealtimeListener<List<ChatRoom>> listener) {
        return db.collection("chats")
            .whereArrayContains("memberIds", uid)
            .limit(50)
            .addSnapshotListener((snapshots, error) -> {
                if (error != null) {
                    FirestoreErrorHandler.handleFirestoreError(auth, error, OperationType.LIST, "chats");
                    listener.onError(error);
                    return;
                }
                List<ChatRoom> list = new ArrayList<>();
                if (snapshots != null) {
                    for (QueryDocumentSnapshot doc : snapshots) {
                        list.add(doc.toObject(ChatRoom.class, ServerTimestampBehavior.ESTIMATE));
                    }
                    Collections.sort(list, (a, b) -> compareTimestampsDesc(a.updatedAt, b.updatedAt));
                }
                listener.onUpdate(list);
            });
    }

    public void createOrOpenDirectChat(@NonNull UserProfile otherUser, @NonNull RepoCallback<ChatRoom> callback) {
        String uid = requireUserId();
        String otherUid = otherUser.userId;
        String chatId = uid.compareTo(otherUid) < 0 ? "dm_" + uid + "_" + otherUid : "dm_" + otherUid + "_" + uid;
        DocumentReference chatRef = db.collection("chats").document(chatId);

        chatRef.get()
            .addOnSuccessListener(snapshot -> {
                if (snapshot.exists()) {
                    callback.onSuccess(snapshot.toObject(ChatRoom.class, ServerTimestampBehavior.ESTIMATE));
                } else {
                    List<String> memberIds = new ArrayList<>();
                    memberIds.add(uid);
                    if (!otherUid.equals(uid)) memberIds.add(otherUid);

                    Map<String, String> memberNames = new HashMap<>();
                    memberNames.put(uid, currentUserName());
                    memberNames.put(otherUid, otherUser.displayName);

                    Map<String, Object> payload = new HashMap<>();
                    payload.put("title", otherUser.displayName);
                    payload.put("isGroup", false);
                    payload.put("memberIds", memberIds);
                    payload.put("memberNames", memberNames);
                    payload.put("lastMessage", "Started a conversation");
                    payload.put("lastSenderName", currentUserName());
                    payload.put("createdBy", uid);
                    payload.put("createdAt", FieldValue.serverTimestamp());
                    payload.put("updatedAt", FieldValue.serverTimestamp());

                    chatRef.set(payload)
                        .addOnSuccessListener(unused -> {
                            ChatRoom room = new ChatRoom();
                            room.id = chatId;
                            room.title = otherUser.displayName;
                            room.isGroup = false;
                            room.memberIds = memberIds;
                            room.memberNames = memberNames;
                            room.lastMessage = "Started a conversation";
                            room.lastSenderName = currentUserName();
                            room.createdBy = uid;
                            callback.onSuccess(room);
                        })
                        .addOnFailureListener(e -> {
                            FirestoreErrorHandler.handleFirestoreError(auth, e, OperationType.CREATE, chatRef.getPath());
                            callback.onError(e);
                        });
                }
            })
            .addOnFailureListener(e -> {
                FirestoreErrorHandler.handleFirestoreError(auth, e, OperationType.GET, chatRef.getPath());
                callback.onError(e);
            });
    }

    public void createGroupChat(@NonNull String title, @NonNull List<UserProfile> selectedMembers, @NonNull RepoCallback<ChatRoom> callback) {
        String uid = requireUserId();
        DocumentReference chatRef = db.collection("chats").document();

        List<String> memberIds = new ArrayList<>();
        memberIds.add(uid);
        Map<String, String> memberNames = new HashMap<>();
        memberNames.put(uid, currentUserName());

        for (UserProfile u : selectedMembers) {
            if (!memberIds.contains(u.userId) && memberIds.size() < 50) {
                memberIds.add(u.userId);
                memberNames.put(u.userId, u.displayName);
            }
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("title", title.trim());
        payload.put("isGroup", true);
        payload.put("memberIds", memberIds);
        payload.put("memberNames", memberNames);
        payload.put("lastMessage", "Group created");
        payload.put("lastSenderName", currentUserName());
        payload.put("createdBy", uid);
        payload.put("createdAt", FieldValue.serverTimestamp());
        payload.put("updatedAt", FieldValue.serverTimestamp());

        chatRef.set(payload)
            .addOnSuccessListener(unused -> {
                ChatRoom room = new ChatRoom();
                room.id = chatRef.getId();
                room.title = title.trim();
                room.isGroup = true;
                room.memberIds = memberIds;
                room.memberNames = memberNames;
                room.lastMessage = "Group created";
                room.lastSenderName = currentUserName();
                room.createdBy = uid;
                callback.onSuccess(room);
            })
            .addOnFailureListener(e -> {
                FirestoreErrorHandler.handleFirestoreError(auth, e, OperationType.CREATE, chatRef.getPath());
                callback.onError(e);
            });
    }

    public void getChatRoom(@NonNull String chatId, @NonNull RepoCallback<ChatRoom> callback) {
        requireUserId();
        DocumentReference ref = db.collection("chats").document(chatId);
        ref.get()
            .addOnSuccessListener(snap -> {
                if (snap.exists()) {
                    callback.onSuccess(snap.toObject(ChatRoom.class, ServerTimestampBehavior.ESTIMATE));
                } else {
                    callback.onError(new IllegalStateException("Chat not found"));
                }
            })
            .addOnFailureListener(e -> {
                FirestoreErrorHandler.handleFirestoreError(auth, e, OperationType.GET, ref.getPath());
                callback.onError(e);
            });
    }

    public ListenerRegistration observeMessages(@NonNull String chatId, @NonNull String currentUid, @NonNull RealtimeListener<List<ChatMessage>> listener) {
        return db.collection("chats").document(chatId).collection("messages")
            .whereArrayContains("memberIds", currentUid)
            .limit(100)
            .addSnapshotListener((snapshots, error) -> {
                if (error != null) {
                    FirestoreErrorHandler.handleFirestoreError(auth, error, OperationType.LIST, "chats/" + chatId + "/messages");
                    listener.onError(error);
                    return;
                }
                List<ChatMessage> list = new ArrayList<>();
                if (snapshots != null) {
                    for (QueryDocumentSnapshot doc : snapshots) {
                        list.add(doc.toObject(ChatMessage.class, ServerTimestampBehavior.ESTIMATE));
                    }
                    Collections.sort(list, (a, b) -> compareTimestampsAsc(a.createdAt, b.createdAt));
                }
                listener.onUpdate(list);
            });
    }

    public void sendMessage(
            @NonNull ChatRoom room,
            @NonNull String text,
            @Nullable String stickerText,
            @Nullable String mediaUrl,
            @NonNull RepoCallback<Void> callback
    ) {
        String uid = requireUserId();
        DocumentReference chatRef = db.collection("chats").document(room.id);
        DocumentReference msgRef = chatRef.collection("messages").document();

        WriteBatch batch = db.batch();
        Map<String, Object> msgPayload = new HashMap<>();
        msgPayload.put("chatId", room.id);
        msgPayload.put("senderId", uid);
        msgPayload.put("senderName", currentUserName());
        String avatar = currentUserAvatar();
        if (!avatar.isEmpty()) msgPayload.put("senderAvatar", avatar);
        msgPayload.put("text", text.trim());
        if (stickerText != null && !stickerText.trim().isEmpty()) msgPayload.put("stickerText", stickerText.trim());
        if (mediaUrl != null && !mediaUrl.trim().isEmpty()) msgPayload.put("mediaUrl", mediaUrl.trim());
        msgPayload.put("memberIds", room.memberIds);
        msgPayload.put("createdAt", FieldValue.serverTimestamp());
        batch.set(msgRef, msgPayload);

        String preview = !text.trim().isEmpty() ? text.trim() : (stickerText != null ? stickerText : "Media");
        if (preview.length() > 80) preview = preview.substring(0, 80);

        Map<String, Object> chatUpdate = new HashMap<>();
        chatUpdate.put("lastMessage", preview);
        chatUpdate.put("lastSenderName", currentUserName());
        chatUpdate.put("updatedAt", FieldValue.serverTimestamp());
        batch.update(chatRef, chatUpdate);

        batch.commit()
            .addOnSuccessListener(unused -> callback.onSuccess(null))
            .addOnFailureListener(e -> {
                FirestoreErrorHandler.handleFirestoreError(auth, e, OperationType.WRITE, msgRef.getPath());
                callback.onError(e);
            });
    }

    // ---- UPI Withdrawals ----

    public ListenerRegistration observeWithdrawals(@NonNull String uid, @NonNull RealtimeListener<List<WithdrawalRequest>> listener) {
        return db.collection("users").document(uid).collection("withdrawals")
            .limit(30)
            .addSnapshotListener((snapshots, error) -> {
                if (error != null) {
                    FirestoreErrorHandler.handleFirestoreError(auth, error, OperationType.LIST, "users/" + uid + "/withdrawals");
                    listener.onError(error);
                    return;
                }
                List<WithdrawalRequest> list = new ArrayList<>();
                if (snapshots != null) {
                    for (QueryDocumentSnapshot doc : snapshots) {
                        list.add(doc.toObject(WithdrawalRequest.class, ServerTimestampBehavior.ESTIMATE));
                    }
                    Collections.sort(list, (a, b) -> compareTimestampsDesc(a.createdAt, b.createdAt));
                }
                listener.onUpdate(list);
            });
    }

    public void submitUpiWithdrawal(
            @NonNull String upiId,
            long coinsToRedeem,
            @NonNull RepoCallback<WithdrawalRequest> callback
    ) {
        String uid = requireUserId();
        DocumentReference userRef = db.collection("users").document(uid);
        DocumentReference wdRef = userRef.collection("withdrawals").document();

        double amountInr = coinsToRedeem / 10.0; // 100 coins = ₹10
        String refCode = "VR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.US);

        WriteBatch batch = db.batch();
        Map<String, Object> userUpdate = new HashMap<>();
        userUpdate.put("withdrawnCoins", FieldValue.increment(coinsToRedeem));
        userUpdate.put("updatedAt", FieldValue.serverTimestamp());
        batch.update(userRef, userUpdate);

        Map<String, Object> wdPayload = new HashMap<>();
        wdPayload.put("userId", uid);
        wdPayload.put("upiId", upiId.trim());
        wdPayload.put("coinsRedeemed", coinsToRedeem);
        wdPayload.put("amountInr", amountInr);
        wdPayload.put("status", "pending");
        wdPayload.put("referenceCode", refCode);
        wdPayload.put("createdAt", FieldValue.serverTimestamp());
        batch.set(wdRef, wdPayload);

        batch.commit()
            .addOnSuccessListener(unused -> {
                WithdrawalRequest req = new WithdrawalRequest();
                req.id = wdRef.getId();
                req.userId = uid;
                req.upiId = upiId.trim();
                req.coinsRedeemed = coinsToRedeem;
                req.amountInr = amountInr;
                req.status = "pending";
                req.referenceCode = refCode;
                req.createdAt = Timestamp.now();
                callback.onSuccess(req);
            })
            .addOnFailureListener(e -> {
                FirestoreErrorHandler.handleFirestoreError(auth, e, OperationType.WRITE, wdRef.getPath());
                callback.onError(e);
            });
    }

    // ---- Helpers ----

    private static List<String> limitHashtags(List<String> tags) {
        List<String> clean = new ArrayList<>();
        if (tags != null) {
            for (String tag : tags) {
                if (tag == null) continue;
                String trimmed = tag.trim();
                if (!trimmed.isEmpty()) {
                    if (!trimmed.startsWith("#")) trimmed = "#" + trimmed;
                    if (!clean.contains(trimmed) && clean.size() < 10) {
                        clean.add(trimmed);
                    }
                }
            }
        }
        return clean;
    }

    private static int compareTimestampsDesc(@Nullable Timestamp a, @Nullable Timestamp b) {
        long ta = a != null ? a.getSeconds() * 1000L + a.getNanoseconds() / 1_000_000L : 0L;
        long tb = b != null ? b.getSeconds() * 1000L + b.getNanoseconds() / 1_000_000L : 0L;
        return Long.compare(tb, ta);
    }

    private static int compareTimestampsAsc(@Nullable Timestamp a, @Nullable Timestamp b) {
        long ta = a != null ? a.getSeconds() * 1000L + a.getNanoseconds() / 1_000_000L : 0L;
        long tb = b != null ? b.getSeconds() * 1000L + b.getNanoseconds() / 1_000_000L : 0L;
        return Long.compare(ta, tb);
    }
}
