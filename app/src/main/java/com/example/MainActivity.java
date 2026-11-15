package com.example;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.credentials.CredentialManager;
import androidx.fragment.app.Fragment;

import com.example.auth.GoogleAuthHelper;
import com.example.data.VibeReelRepository;
import com.example.databinding.ActivityMainBinding;
import com.example.model.UserProfile;
import com.example.ui.chat.ChatsFragment;
import com.example.ui.home.HomeFragment;
import com.example.ui.reels.ReelsFragment;
import com.example.ui.upload.UploadFragment;
import com.example.ui.wallet.WalletProfileFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private CredentialManager credentialManager;
    private FirebaseAuth auth;
    private VibeReelRepository repository;
    private String activeUid = null;
    private int currentTabId = R.id.nav_home;

    private final FirebaseAuth.AuthStateListener authStateListener = firebaseAuth -> {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        updateAuthUi(user);
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootContainer, (v, windowInsets) -> {
            Insets sysBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.authGateContainer.setPadding(0, sysBars.top, 0, sysBars.bottom);
            binding.fragmentContainer.setPadding(0, sysBars.top, 0, 0);
            binding.bottomNav.setPadding(0, 0, 0, sysBars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        auth = FirebaseAuth.getInstance();
        credentialManager = CredentialManager.create(this);
        repository = new VibeReelRepository(this);

        binding.btnGoogleSignIn.setOnClickListener(v -> triggerInteractiveSignIn());

        binding.bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == currentTabId && getSupportFragmentManager().findFragmentById(R.id.fragmentContainer) != null) {
                return true;
            }
            currentTabId = id;
            showTabFragment(id, null);
            return true;
        });

        updateAuthUi(auth.getCurrentUser());

        if (auth.getCurrentUser() == null) {
            GoogleAuthHelper.attemptAutoSignIn(this, credentialManager, new GoogleAuthHelper.AuthCallback() {
                @Override
                public void onSuccess(FirebaseUser user) {
                    updateAuthUi(user);
                }

                @Override
                public void onCancelled() {
                    // Stay on sign-in screen for interactive tap
                }

                @Override
                public void onError(String message) {
                    // Stay on sign-in screen for interactive tap
                }
            });
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        auth.addAuthStateListener(authStateListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        auth.removeAuthStateListener(authStateListener);
    }

    private void triggerInteractiveSignIn() {
        binding.btnGoogleSignIn.setEnabled(false);
        binding.btnGoogleSignIn.setText(R.string.signing_in);
        binding.authProgress.setVisibility(View.VISIBLE);
        binding.txtAuthError.setVisibility(View.GONE);

        GoogleAuthHelper.signInWithGoogleInteractive(this, credentialManager, new GoogleAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(FirebaseUser user) {
                binding.btnGoogleSignIn.setEnabled(true);
                binding.btnGoogleSignIn.setText(R.string.sign_in_google);
                binding.authProgress.setVisibility(View.GONE);
                updateAuthUi(user);
            }

            @Override
            public void onCancelled() {
                binding.btnGoogleSignIn.setEnabled(true);
                binding.btnGoogleSignIn.setText(R.string.sign_in_google);
                binding.authProgress.setVisibility(View.GONE);
            }

            @Override
            public void onError(String message) {
                binding.btnGoogleSignIn.setEnabled(true);
                binding.btnGoogleSignIn.setText(R.string.sign_in_google);
                binding.authProgress.setVisibility(View.GONE);
                binding.txtAuthError.setText(message);
                binding.txtAuthError.setVisibility(View.VISIBLE);
            }
        });
    }

    private void updateAuthUi(FirebaseUser user) {
        if (user == null) {
            activeUid = null;
            binding.authGateContainer.setVisibility(View.VISIBLE);
            binding.mainContentContainer.setVisibility(View.GONE);
            Fragment existing = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
            if (existing != null) {
                getSupportFragmentManager().beginTransaction().remove(existing).commitAllowingStateLoss();
            }
        } else {
            binding.authGateContainer.setVisibility(View.GONE);
            binding.mainContentContainer.setVisibility(View.VISIBLE);
            if (!user.getUid().equals(activeUid)) {
                activeUid = user.getUid();
                repository.ensureUserProfile(user, new VibeReelRepository.RepoCallback<>() {
                    @Override
                    public void onSuccess(UserProfile result) {
                        // Profile initialized
                    }

                    @Override
                    public void onError(Exception error) {
                        Toast.makeText(MainActivity.this, "Profile sync warning: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
                currentTabId = R.id.nav_home;
                binding.bottomNav.setSelectedItemId(R.id.nav_home);
                showTabFragment(R.id.nav_home, null);
            }
        }
    }

    public void navigateToTab(int menuItemId, String uploadPresetMode) {
        currentTabId = menuItemId;
        binding.bottomNav.setSelectedItemId(menuItemId);
        showTabFragment(menuItemId, uploadPresetMode);
    }

    public void performSignOut() {
        GoogleAuthHelper.signOut(credentialManager, () -> updateAuthUi(null));
    }

    private void showTabFragment(int menuItemId, String uploadPresetMode) {
        if (auth.getCurrentUser() == null) return;
        Fragment target;
        if (menuItemId == R.id.nav_reels) {
            target = new ReelsFragment();
        } else if (menuItemId == R.id.nav_upload) {
            target = UploadFragment.newInstance(uploadPresetMode);
        } else if (menuItemId == R.id.nav_chats) {
            target = new ChatsFragment();
        } else if (menuItemId == R.id.nav_wallet) {
            target = new WalletProfileFragment();
        } else {
            target = new HomeFragment();
        }
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, target)
                .commit();
    }
}
