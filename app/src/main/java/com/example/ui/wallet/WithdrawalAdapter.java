package com.example.ui.wallet;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.databinding.ItemWithdrawalBinding;
import com.example.model.WithdrawalRequest;
import com.example.util.TimeFormatUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WithdrawalAdapter extends RecyclerView.Adapter<WithdrawalAdapter.WithdrawalViewHolder> {

    private final List<WithdrawalRequest> items = new ArrayList<>();

    public void submitList(@NonNull List<WithdrawalRequest> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public WithdrawalViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemWithdrawalBinding binding = ItemWithdrawalBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new WithdrawalViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull WithdrawalViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class WithdrawalViewHolder extends RecyclerView.ViewHolder {
        private final ItemWithdrawalBinding binding;

        WithdrawalViewHolder(ItemWithdrawalBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(WithdrawalRequest item) {
            binding.txtWithdrawalUpi.setText(item.upiId);
            String ref = item.referenceCode != null && !item.referenceCode.isEmpty()
                    ? item.referenceCode : "VR-PAYOUT";
            binding.txtWithdrawalMeta.setText(
                    item.coinsRedeemed + " Coins • " + ref + " • " + TimeFormatUtil.formatRelative(item.createdAt));
            binding.txtWithdrawalInr.setText(String.format(Locale.US, "₹%.2f", item.amountInr));
            binding.txtWithdrawalStatus.setText(
                    item.status != null ? item.status.toUpperCase(Locale.US) : "PENDING");
        }
    }
}
