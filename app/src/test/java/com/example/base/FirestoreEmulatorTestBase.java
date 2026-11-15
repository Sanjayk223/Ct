package com.example.base;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.MemoryCacheSettings;

import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
public abstract class FirestoreEmulatorTestBase {

    private static final String EMULATOR_HOST = "127.0.0.1";
    private static final int FIRESTORE_PORT = 8085;
    private static final int AUTH_PORT = 9099;
    private static final String PROJECT_ID = "demo-no-project";
    private static final String DEFAULT_PASSWORD = "password123";
    protected static final long DEFAULT_TIMEOUT_SEC = 8L;

    protected FirebaseFirestore firestore;
    protected FirebaseAuth auth;
    protected String databaseId;

    @Before
    public void setUpFirebase() {
        Context context = ApplicationProvider.getApplicationContext();
        int resId = context.getResources().getIdentifier("firestore_database_id", "string", context.getPackageName());
        databaseId = resId != 0
                ? context.getString(resId)
                : "ai-studio-android-vibereel-d5e20a56-dd44-4f6c-be5c-fb94169f6646";

        FirebaseApp app;
        if (FirebaseApp.getApps(context).isEmpty()) {
            app = FirebaseApp.initializeApp(
                    context,
                    new FirebaseOptions.Builder()
                            .setApplicationId("com.aistudio.vibereel.krmpwx")
                            .setProjectId(PROJECT_ID)
                            .setApiKey("fake-api-key-for-emulator")
                            .build()
            );
            FirebaseFirestore newFirestore = FirebaseFirestore.getInstance(app, databaseId);
            newFirestore.useEmulator(EMULATOR_HOST, FIRESTORE_PORT);
            newFirestore.setFirestoreSettings(
                    new FirebaseFirestoreSettings.Builder()
                            .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                            .build()
            );
            FirebaseAuth newAuth = FirebaseAuth.getInstance(app);
            newAuth.useEmulator(EMULATOR_HOST, AUTH_PORT);
        } else {
            app = FirebaseApp.getInstance();
        }

        firestore = FirebaseFirestore.getInstance(app, databaseId);
        auth = FirebaseAuth.getInstance(app);
    }

    @After
    public void tearDownFirebase() {
        if (auth != null) {
            auth.signOut();
        }
    }

    protected String signInTestUser(String email) throws Exception {
        try {
            AuthResult result = Tasks.await(
                    auth.signInWithEmailAndPassword(email, DEFAULT_PASSWORD),
                    DEFAULT_TIMEOUT_SEC,
                    TimeUnit.SECONDS
            );
            return result.getUser().getUid();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof FirebaseAuthInvalidUserException) {
                AuthResult created = Tasks.await(
                        auth.createUserWithEmailAndPassword(email, DEFAULT_PASSWORD),
                        DEFAULT_TIMEOUT_SEC,
                        TimeUnit.SECONDS
                );
                return created.getUser().getUid();
            }
            throw e;
        }
    }

    protected FirebaseFirestoreException unwrapFirestoreException(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof FirebaseFirestoreException) {
                return (FirebaseFirestoreException) cur;
            }
            cur = cur.getCause();
        }
        return null;
    }
}
