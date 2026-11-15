package com.example.data;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.firestore.FirebaseFirestoreException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class FirestoreErrorHandler {

    private static final String TAG = "FirestoreError";

    public enum OperationType {
        CREATE("create"),
        UPDATE("update"),
        DELETE("delete"),
        LIST("list"),
        GET("get"),
        WRITE("write");

        public final String value;

        OperationType(String value) {
            this.value = value;
        }
    }

    private FirestoreErrorHandler() {}

    public static String handleFirestoreError(
            FirebaseAuth auth,
            Throwable exception,
            OperationType operationType,
            String path
    ) {
        Throwable rootError = findRootError(exception);
        FirebaseUser currentUser = auth != null ? auth.getCurrentUser() : null;

        JSONArray providerArray = new JSONArray();
        if (currentUser != null) {
            for (UserInfo provider : currentUser.getProviderData()) {
                JSONObject providerJson = new JSONObject();
                try {
                    providerJson.put("providerId", provider.getProviderId());
                    providerJson.put("email", provider.getEmail());
                    providerArray.put(providerJson);
                } catch (JSONException ignored) {}
            }
        }

        JSONObject authInfoJson = new JSONObject();
        JSONObject errorInfoJson = new JSONObject();
        try {
            authInfoJson.put("userId", currentUser != null ? currentUser.getUid() : JSONObject.NULL);
            authInfoJson.put("email", currentUser != null ? currentUser.getEmail() : JSONObject.NULL);
            authInfoJson.put("emailVerified", currentUser != null ? currentUser.isEmailVerified() : JSONObject.NULL);
            authInfoJson.put("tenantId", currentUser != null ? currentUser.getTenantId() : JSONObject.NULL);
            authInfoJson.put("providerInfo", providerArray);

            errorInfoJson.put("error", rootError.getMessage() != null ? rootError.getMessage() : rootError.toString());
            errorInfoJson.put("operationType", operationType.value);
            errorInfoJson.put("path", path != null ? path : JSONObject.NULL);
            errorInfoJson.put("authInfo", authInfoJson);
        } catch (JSONException ignored) {}

        String jsonString = errorInfoJson.toString();
        Log.e(TAG, "Firestore Error: " + jsonString);
        return jsonString;
    }

    private static Throwable findRootError(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof FirebaseFirestoreException) {
                return cur;
            }
            cur = cur.getCause();
        }
        return t != null ? t : new RuntimeException("Unknown Firestore error");
    }
}
