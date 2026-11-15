package com.example.auth;

import android.app.Activity;
import android.content.Context;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;

import com.example.R;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class GoogleAuthHelper {

    private static final String TAG = "Auth";

    public interface AuthCallback {
        void onSuccess(FirebaseUser user);
        void onCancelled();
        void onError(String message);
    }

    private static final ExecutorService AUTH_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private GoogleAuthHelper() {}

    public static void attemptAutoSignIn(
            @NonNull Context context,
            @NonNull CredentialManager credentialManager,
            @NonNull AuthCallback callback
    ) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            MAIN_HANDLER.post(() -> callback.onSuccess(auth.getCurrentUser()));
            return;
        }

        String clientId;
        try {
            clientId = context.getString(R.string.default_web_client_id);
        } catch (Exception e) {
            MAIN_HANDLER.post(callback::onCancelled);
            return;
        }

        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(true)
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        credentialManager.getCredentialAsync(
                context,
                request,
                new CancellationSignal(),
                AUTH_EXECUTOR,
                new CredentialManagerCallback<>() {
                    @Override
                    public void onResult(@NonNull GetCredentialResponse response) {
                        handleCredentialResult(response.getCredential(), callback);
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        MAIN_HANDLER.post(callback::onCancelled);
                    }
                }
        );
    }

    public static void signInWithGoogleInteractive(
            @NonNull Activity activity,
            @NonNull CredentialManager credentialManager,
            @NonNull AuthCallback callback
    ) {
        String clientId;
        try {
            clientId = activity.getString(R.string.default_web_client_id);
        } catch (Exception e) {
            MAIN_HANDLER.post(() -> callback.onError("Google Sign-In configuration missing: default_web_client_id not found"));
            return;
        }

        GetSignInWithGoogleOption signInOption = new GetSignInWithGoogleOption.Builder(clientId).build();
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build();

        credentialManager.getCredentialAsync(
                activity,
                request,
                new CancellationSignal(),
                AUTH_EXECUTOR,
                new CredentialManagerCallback<>() {
                    @Override
                    public void onResult(@NonNull GetCredentialResponse response) {
                        handleCredentialResult(response.getCredential(), callback);
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        if (e instanceof GetCredentialCancellationException) {
                            Log.w(TAG, "Google Sign-In flow cancelled or dismissed: " + e.getMessage(), e);
                            MAIN_HANDLER.post(callback::onCancelled);
                        } else {
                            Log.e(TAG, "Google Sign-In failed", e);
                            String msg = e.getLocalizedMessage() != null ? e.getLocalizedMessage() : "Sign in failed";
                            MAIN_HANDLER.post(() -> callback.onError(msg));
                        }
                    }
                }
        );
    }

    private static void handleCredentialResult(@NonNull Credential credential, @NonNull AuthCallback callback) {
        if (credential instanceof CustomCredential
                && credential.getType().equals(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)) {
            try {
                GoogleIdTokenCredential googleCred = GoogleIdTokenCredential.createFrom(credential.getData());
                AuthCredential authCredential = GoogleAuthProvider.getCredential(googleCred.getIdToken(), null);
                FirebaseAuth.getInstance().signInWithCredential(authCredential)
                        .addOnSuccessListener(authResult -> MAIN_HANDLER.post(() -> callback.onSuccess(authResult.getUser())))
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Firebase signInWithCredential failed", e);
                            MAIN_HANDLER.post(() -> callback.onError(
                                    e.getLocalizedMessage() != null ? e.getLocalizedMessage() : "Authentication failed"
                            ));
                        });
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse GoogleIdTokenCredential", e);
                MAIN_HANDLER.post(() -> callback.onError("Invalid Google ID token"));
            }
        } else {
            MAIN_HANDLER.post(() -> callback.onError("Unexpected credential type"));
        }
    }

    public static void signOut(
            @NonNull CredentialManager credentialManager,
            @NonNull Runnable onSignOutComplete
    ) {
        ClearCredentialStateRequest clearRequest = new ClearCredentialStateRequest();
        credentialManager.clearCredentialStateAsync(
                clearRequest,
                new CancellationSignal(),
                AUTH_EXECUTOR,
                new CredentialManagerCallback<>() {
                    @Override
                    public void onResult(@NonNull Void result) {
                        FirebaseAuth.getInstance().signOut();
                        MAIN_HANDLER.post(onSignOutComplete);
                    }

                    @Override
                    public void onError(@NonNull ClearCredentialException e) {
                        Log.w(TAG, "Failed to clear credential state: " + e.getMessage());
                        FirebaseAuth.getInstance().signOut();
                        MAIN_HANDLER.post(onSignOutComplete);
                    }
                }
        );
    }
}
