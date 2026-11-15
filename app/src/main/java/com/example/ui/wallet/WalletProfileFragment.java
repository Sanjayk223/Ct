package com.example.ui.wallet;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.example.MainActivity;
import com.example.R;
import com.example.data.VibeReelRepository;
import com.example.databinding.FragmentWalletProfileBinding;
import com.example.model.UserProfile;
import com.example.model.WithdrawalRequest;
import com.example.util.HashtagStickerCatalog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;
import java.util.Locale;

public class WalletProfileFragment extends Fragment {

    private static final long MIN_WITHDRAW_COINS = 100L;

    private FragmentWalletProfileBinding binding;
    private VibeReelRepository repository;
    private WithdrawalAdapter withdrawalAdapter;
    private ListenerRegistration userListener;
    private ListenerRegistration withdrawalsListener;

    private UserProfile currentProfile;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentWalletProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = new VibeReelRepository(requireContext());

        withdrawalAdapter = new WithdrawalAdapter();
        binding.rvWithdrawals.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvWithdrawals.setAdapter(withdrawalAdapter);

        binding.edtCoinsToWithdraw.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                updateConversionPreview(s != null ? s.toString().trim() : "");
            }
        });

        binding.btnSubmitUpiWithdrawal.setOnClickListener(v -> submitWithdrawal());
        binding.btnEditProfile.setOnClickListener(v -> showEditProfileDialog());
        binding.btnSignOut.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).performSignOut();
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        userListener = repository.observeCurrentUser(user.getUid(), new VibeReelRepository.RealtimeListener<>() {
            @Override
            public void onUpdate(UserProfile profile) {
                currentProfile = profile;
                bindProfile(profile);
            }

            @Override
            public void onError(Exception error) {
                if (isAdded()) {
                    Toast.makeText(requireContext(), "Wallet sync error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });

        withdrawalsListener = repository.observeWithdrawals(user.getUid(), new VibeReelRepository.RealtimeListener<>() {
            @Override
            public void onUpdate(List<WithdrawalRequest> list) {
                if (binding == null) return;
                withdrawalAdapter.submitList(list);
                binding.txtEmptyWithdrawals.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(Exception error) {
                if (isAdded()) {
                    Toast.makeText(requireContext(), "Withdrawal history error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public void onStop() {
        super.onStop();
        if (userListener != null) {
            userListener.remove();
            userListener = null;
        }
        if (withdrawalsListener != null) {
            withdrawalsListener.remove();
            withdrawalsListener = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void bindProfile(@NonNull UserProfile profile) {
        if (binding == null) return;
        binding.txtProfileName.setText(
                profile.displayName != null && !profile.displayName.isEmpty() ? profile.displayName : "Vibe Creator");
        binding.txtProfileUsername.setText(
                profile.username != null && !profile.username.isEmpty() ? "@" + profile.username : "@creator");
        binding.txtProfileBio.setText(
                profile.bio != null ? profile.bio : "");

        Glide.with(this)
                .load(profile.avatarUrl != null && !profile.avatarUrl.isEmpty() ? profile.avatarUrl : R.drawable.img_default_avatar)
                .placeholder(R.drawable.img_default_avatar)
                .error(R.drawable.img_default_avatar)
                .into(binding.imgProfileAvatar);

        long availableCoins = profile.getAvailableCoins();
        double availableInr = availableCoins / 10.0;

        binding.txtAvailableCoins.setText(String.valueOf(availableCoins));
        binding.txtAvailableInr.setText(String.format(Locale.US, "₹%.2f", availableInr));
        binding.txtWalletBreakdown.setText(String.format(
                Locale.US,
                "%d Likes • %d Earned • %d Withdrawn (100 Coins = ₹10)",
                profile.totalLikesReceived,
                profile.coinsBalance,
                profile.withdrawnCoins
        ));
    }

    private void updateConversionPreview(@NonNull String rawCoins) {
        if (binding == null) return;
        if (rawCoins.isEmpty()) {
            binding.txtWithdrawalConversionPreview.setText("Minimum 100 coins (₹10.00)");
            return;
        }
        try {
            long coins = Long.parseLong(rawCoins);
            double inr = coins / 10.0;
            binding.txtWithdrawalConversionPreview.setText(
                    String.format(Locale.US, "%d Coins = ₹%.2f UPI Payout", coins, inr));
        } catch (NumberFormatException e) {
            binding.txtWithdrawalConversionPreview.setText("Enter a valid coin amount");
        }
    }

    private void submitWithdrawal() {
        if (currentProfile == null) {
            Toast.makeText(requireContext(), "Loading wallet balance…", Toast.LENGTH_SHORT).show();
            return;
        }

        String upiId = binding.edtUpiId.getText() != null
                ? binding.edtUpiId.getText().toString().trim() : "";
        String coinsRaw = binding.edtCoinsToWithdraw.getText() != null
                ? binding.edtCoinsToWithdraw.getText().toString().trim() : "";

        if (!HashtagStickerCatalog.isValidUpiId(upiId)) {
            binding.edtUpiId.setError("Enter valid UPI ID (e.g. name@okicici)");
            return;
        }

        long coins;
        try {
            coins = Long.parseLong(coinsRaw);
        } catch (NumberFormatException e) {
            binding.edtCoinsToWithdraw.setError("Enter a valid number of coins");
            return;
        }

        if (coins < MIN_WITHDRAW_COINS) {
            binding.edtCoinsToWithdraw.setError("Minimum withdrawal is 100 coins");
            return;
        }

        long available = currentProfile.getAvailableCoins();
        if (coins > available) {
            binding.edtCoinsToWithdraw.setError("Insufficient balance (" + available + " coins available)");
            return;
        }

        binding.btnSubmitUpiWithdrawal.setEnabled(false);
        repository.submitUpiWithdrawal(upiId, coins, new VibeReelRepository.RepoCallback<>() {
            @Override
            public void onSuccess(WithdrawalRequest req) {
                if (!isAdded() || binding == null) return;
                binding.btnSubmitUpiWithdrawal.setEnabled(true);
                binding.edtCoinsToWithdraw.setText("");
                Toast.makeText(
                        requireContext(),
                        String.format(Locale.US, "UPI payout ₹%.2f submitted (%s)", req.amountInr, req.referenceCode),
                        Toast.LENGTH_LONG
                ).show();
            }

            @Override
            public void onError(Exception error) {
                if (!isAdded() || binding == null) return;
                binding.btnSubmitUpiWithdrawal.setEnabled(true);
                Toast.makeText(requireContext(), "Withdrawal failed: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showEditProfileDialog() {
        if (currentProfile == null) return;
        float density = getResources().getDisplayMetrics().density;
        int pad = Math.round(16 * density);

        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, pad / 2, pad, 0);

        EditText edtName = new EditText(requireContext());
        edtName.setHint("Display Name");
        edtName.setText(currentProfile.displayName);
        edtName.setMinHeight(Math.round(48 * density));

        EditText edtUsername = new EditText(requireContext());
        edtUsername.setHint("Username");
        edtUsername.setText(currentProfile.username);
        edtUsername.setMinHeight(Math.round(48 * density));

        EditText edtBio = new EditText(requireContext());
        edtBio.setHint("Creator Bio");
        edtBio.setText(currentProfile.bio);
        edtBio.setMinHeight(Math.round(48 * density));

        container.addView(edtName);
        container.addView(edtUsername);
        container.addView(edtBio);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Edit Creator Profile")
                .setView(container)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = edtName.getText().toString().trim();
                    String handle = edtUsername.getText().toString().trim().replace("@", "");
                    String bio = edtBio.getText().toString().trim();
                    if (name.isEmpty() || handle.isEmpty()) {
                        Toast.makeText(requireContext(), "Name and username are required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    repository.updateProfile(name, handle, bio, new VibeReelRepository.RepoCallback<>() {
                        @Override
                        public void onSuccess(Void unused) {
                            if (isAdded()) {
                                Toast.makeText(requireContext(), "Profile updated!", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onError(Exception error) {
                            if (isAdded()) {
                                Toast.makeText(requireContext(), "Update failed: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                })
                .show();
    }
}
