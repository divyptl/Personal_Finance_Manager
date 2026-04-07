package com.example.personalfinancemanager;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class StockAdapter extends RecyclerView.Adapter<StockAdapter.StockHolder> {

    private List<Stock> stocks = new ArrayList<>();

    @NonNull
    @Override
    public StockHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_stock, parent, false);
        return new StockHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull StockHolder holder, int position) {
        Stock currentStock = stocks.get(position);

        holder.tvTicker.setText(currentStock.getTicker());
        holder.tvQuantity.setText("Qty: " + currentStock.getQuantity());

        double invested = currentStock.getQuantity() * currentStock.getAverageBuyPrice();
        holder.tvInvested.setText(String.format("Invested: ₹%.2f", invested));

        // TODO: Replace this simulated logic with your actual AngelOne API call later
        // Simulate an API response for now so your app doesn't crash:
        double simulatedLivePrice = currentStock.getAverageBuyPrice() * 1.05; // Assuming a 5% profit simulation

        double currentVal = currentStock.getQuantity() * simulatedLivePrice;
        double pnl = currentVal - invested;
        double pnlPercentage = (pnl / invested) * 100;

        holder.tvLivePrice.setText(String.format("LTP: ₹%.2f", simulatedLivePrice));
        holder.tvPnl.setText(String.format("₹%.2f (%.2f%%)", pnl, pnlPercentage));

        if (pnl >= 0) {
            holder.tvPnl.setTextColor(Color.parseColor("#4CAF50")); // Green
        } else {
            holder.tvPnl.setTextColor(Color.parseColor("#FF5252")); // Red
        }
    }

    @Override
    public int getItemCount() { return stocks.size(); }

    public void setStocks(List<Stock> stocks) {
        this.stocks = stocks;
        notifyDataSetChanged();
    }

    class StockHolder extends RecyclerView.ViewHolder {
        private TextView tvTicker, tvQuantity, tvInvested, tvLivePrice, tvPnl;

        public StockHolder(View itemView) {
            super(itemView);
            // Ensure these IDs match your item_stock.xml!
            tvTicker = itemView.findViewById(R.id.tvTicker);
            tvQuantity = itemView.findViewById(R.id.tvHoldings);
            tvInvested = itemView.findViewById(R.id.tvInvested);
            tvLivePrice = itemView.findViewById(R.id.tvLivePrice);
            tvPnl = itemView.findViewById(R.id.tvPnL);
        }
    }
}