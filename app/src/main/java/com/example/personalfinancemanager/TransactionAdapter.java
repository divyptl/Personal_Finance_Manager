package com.example.personalfinancemanager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TransactionHolder> {

    private List<Transaction> transactions = new ArrayList<>();

    @NonNull
    @Override
    public TransactionHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transaction, parent, false);
        return new TransactionHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull TransactionHolder holder, int position) {
        Transaction current = transactions.get(position);

        holder.tvMessage.setText(current.getMessage());
        holder.tvCategory.setText(current.getCategory());

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        holder.tvDate.setText(sdf.format(new Date(current.getTimestamp())));

        String type = current.getType();
        boolean isExpense = type != null
                && (type.equalsIgnoreCase("Debit") || type.equalsIgnoreCase("expense"));

        if (isExpense) {
            holder.tvAmount.setText(String.format(Locale.getDefault(), "-\u20B9%.2f", current.getAmount()));
            holder.tvAmount.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.accent_red));
        } else {
            holder.tvAmount.setText(String.format(Locale.getDefault(), "+\u20B9%.2f", current.getAmount()));
            holder.tvAmount.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.accent_green));
        }
    }

    @Override
    public int getItemCount() {
        return transactions.size();
    }

    public void setTransactions(List<Transaction> transactions) {
        this.transactions = transactions;
        notifyDataSetChanged();
    }

    static class TransactionHolder extends RecyclerView.ViewHolder {
        final TextView tvMessage, tvAmount, tvDate, tvCategory;

        TransactionHolder(View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvTransactionMessage);
            tvAmount = itemView.findViewById(R.id.tvTransactionAmount);
            tvDate = itemView.findViewById(R.id.tvTransactionDate);
            tvCategory = itemView.findViewById(R.id.tvCategory);
        }
    }
}
