package com.example.personalfinancemanager;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
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
        // This grabs your item_transaction.xml blueprint
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transaction, parent, false);
        return new TransactionHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull TransactionHolder holder, int position) {
        Transaction currentTransaction = transactions.get(position);

        // 1. Set the basic text fields
        holder.tvMessage.setText(currentTransaction.getMessage());
        holder.tvCategory.setText(currentTransaction.getCategory());

        // 2. Format the raw timestamp into a readable date
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        holder.tvDate.setText(sdf.format(new Date(currentTransaction.getTimestamp())));

        // 3. Format Amount & Color safely
        // We check for both "Debit" and "expense" just in case your database has older entries!
        String type = currentTransaction.getType();
        if (type != null && (type.equalsIgnoreCase("Debit") || type.equalsIgnoreCase("expense"))) {
            holder.tvAmount.setText("-₹" + currentTransaction.getAmount());
            holder.tvAmount.setTextColor(Color.parseColor("#FF5252")); // Red
        } else {
            holder.tvAmount.setText("+₹" + currentTransaction.getAmount());
            holder.tvAmount.setTextColor(Color.parseColor("#4CAF50")); // Green
        }
    }

    @Override
    public int getItemCount() {
        return transactions.size();
    }

    // When the database updates, this pushes the new list to the screen
    public void setTransactions(List<Transaction> transactions) {
        this.transactions = transactions;
        notifyDataSetChanged();
    }

    class TransactionHolder extends RecyclerView.ViewHolder {
        // 1. Declare the variables
        private TextView tvMessage;
        private TextView tvAmount;
        private TextView tvDate;
        private TextView tvCategory;

        public TransactionHolder(View itemView) {
            super(itemView);
            // 2. Link them to the NEW IDs from your updated XML
            tvMessage = itemView.findViewById(R.id.tvTransactionMessage);
            tvAmount = itemView.findViewById(R.id.tvTransactionAmount);
            tvDate = itemView.findViewById(R.id.tvTransactionDate);
            tvCategory = itemView.findViewById(R.id.tvCategory);
        }
    }
}