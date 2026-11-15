package com.example.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.base.FirestoreEmulatorTestBase;
import com.example.model.ChatRoom;
import com.example.model.PostItem;
import com.example.model.ReelItem;
import com.example.model.UserProfile;
import com.example.model.WithdrawalRequest;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class VibeReelRepositoryRuleTest extends FirestoreEmulatorTestBase {

    private static final String ALICE_EMAIL = "alice_vr@test.com";
    private static final String BOB_EMAIL = "bob_vr@test.com";

    private <T> T awaitCallback(CallbackRunner<T> runner) throws Exception {
        CompletableFuture<T> future = new CompletableFuture<>();
        runner.run(new VibeReelRepository.RepoCallback<>() {
            @Override
            public void onSuccess(T result) {
                future.complete(result);
            }

            @Override
            public void onError(Exception error) {
                future.completeExceptionally(error);
            }
        });
        return future.get(DEFAULT_TIMEOUT_SEC, TimeUnit.SECONDS);
    }

    @FunctionalInterface
    private interface CallbackRunner<T> {
        void run(VibeReelRepository.RepoCallback<T> callback);
    }

    @Test
    public void createPostAndLike_awardsCoinsToAuthor() throws Exception {
        String aliceUid = signInTestUser(ALICE_EMAIL);
        VibeReelRepository aliceRepo = new VibeReelRepository(firestore, auth);
        this.<UserProfile>awaitCallback(cb -> aliceRepo.ensureUserProfile(auth.getCurrentUser(), cb));

        String postId = awaitCallback(cb -> aliceRepo.createPost(
                "https://cdn.example.com/photo_" + UUID.randomUUID() + ".jpg",
                "image",
                "Testing #vibereel coins",
                Arrays.asList("#vibereel", "#trending"),
                "🔥 Hot",
                cb
        ));
        assertNotNull(postId);

        // Read Alice profile before like
        CompletableFuture<UserProfile> beforeFuture = new CompletableFuture<>();
        ListenerRegistration reg0 = aliceRepo.observeCurrentUser(aliceUid, new VibeReelRepository.RealtimeListener<>() {
            @Override
            public void onUpdate(UserProfile data) {
                beforeFuture.complete(data);
            }

            @Override
            public void onError(Exception error) {
                beforeFuture.completeExceptionally(error);
            }
        });
        long coinsBefore = beforeFuture.get(DEFAULT_TIMEOUT_SEC, TimeUnit.SECONDS).coinsBalance;
        reg0.remove();

        // Bob signs in and likes Alice's post
        signInTestUser(BOB_EMAIL);
        VibeReelRepository bobRepo = new VibeReelRepository(firestore, auth);
        this.<UserProfile>awaitCallback(cb -> bobRepo.ensureUserProfile(auth.getCurrentUser(), cb));

        PostItem post = new PostItem();
        post.id = postId;
        post.authorId = aliceUid;
        boolean liked = awaitCallback(cb -> bobRepo.togglePostLike(post, false, cb));
        assertTrue(liked);

        // Verify Alice's coins increased by 10
        signInTestUser(ALICE_EMAIL);
        CompletableFuture<UserProfile> afterFuture = new CompletableFuture<>();
        ListenerRegistration reg1 = aliceRepo.observeCurrentUser(aliceUid, new VibeReelRepository.RealtimeListener<>() {
            @Override
            public void onUpdate(UserProfile data) {
                if (data.coinsBalance >= coinsBefore + 10L) {
                    afterFuture.complete(data);
                }
            }

            @Override
            public void onError(Exception error) {
                afterFuture.completeExceptionally(error);
            }
        });
        UserProfile updatedAlice = afterFuture.get(DEFAULT_TIMEOUT_SEC, TimeUnit.SECONDS);
        reg1.remove();
        assertEquals(coinsBefore + 10L, updatedAlice.coinsBalance);
    }

    @Test
    public void createReel_and_withdrawalOverdrawFails() throws Exception {
        signInTestUser(ALICE_EMAIL);
        VibeReelRepository repo = new VibeReelRepository(firestore, auth);
        this.<UserProfile>awaitCallback(cb -> repo.ensureUserProfile(auth.getCurrentUser(), cb));

        String reelId = awaitCallback(cb -> repo.createReel(
                "https://cdn.example.com/reel_" + UUID.randomUUID() + ".mp4",
                "",
                "My first R2 reel #dance",
                Collections.singletonList("#dance"),
                "⚡ Viral",
                "Original Beat",
                cb
        ));
        assertNotNull(reelId);

        try {
            this.<WithdrawalRequest>awaitCallback(cb -> repo.submitUpiWithdrawal("alice@okicici", 50000L, cb));
            fail("Expected overdraw UPI withdrawal to fail with PERMISSION_DENIED");
        } catch (Exception ex) {
            FirebaseFirestoreException ffe = unwrapFirestoreException(ex);
            assertNotNull("Expected FirebaseFirestoreException, got " + ex, ffe);
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ffe.getCode());
        }
    }

    @Test
    public void groupChat_and_unauthenticatedRejection() throws Exception {
        signInTestUser(ALICE_EMAIL);
        VibeReelRepository aliceRepo = new VibeReelRepository(firestore, auth);
        UserProfile alice = awaitCallback(cb -> aliceRepo.ensureUserProfile(auth.getCurrentUser(), cb));

        signInTestUser(BOB_EMAIL);
        VibeReelRepository bobRepo = new VibeReelRepository(firestore, auth);
        UserProfile bob = awaitCallback(cb -> bobRepo.ensureUserProfile(auth.getCurrentUser(), cb));

        ChatRoom group = awaitCallback(cb -> bobRepo.createGroupChat(
                "Creators Club " + UUID.randomUUID().toString().substring(0, 4),
                Collections.singletonList(alice),
                cb
        ));
        assertNotNull(group);
        assertTrue(group.isGroup);

        this.<Void>awaitCallback(cb -> bobRepo.sendMessage(group, "Hello creators!", "🚀 Boost", null, cb));

        // Unauthenticated query must fail with PERMISSION_DENIED
        auth.signOut();
        CompletableFuture<List<PostItem>> unauthFuture = new CompletableFuture<>();
        try {
            firestore.collection("posts").whereEqualTo("isPublished", true).get()
                    .addOnSuccessListener(s -> unauthFuture.complete(Collections.emptyList()))
                    .addOnFailureListener(unauthFuture::completeExceptionally);
            unauthFuture.get(DEFAULT_TIMEOUT_SEC, TimeUnit.SECONDS);
            fail("Expected unauthenticated query to fail");
        } catch (Exception ex) {
            FirebaseFirestoreException ffe = unwrapFirestoreException(ex);
            assertNotNull(ffe);
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ffe.getCode());
        }
    }
}
