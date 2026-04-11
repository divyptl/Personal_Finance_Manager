package com.example.personalfinancemanager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StockAdapter extends RecyclerView.Adapter<StockAdapter.StockHolder> {

    private List<Stock> stocks = new ArrayList<>();
    private Map<String, Double> livePrices = new HashMap<>();
    private OnStockLongClickListener longClickListener;

    public interface OnStockLongClickListener {
        void onStockLongClick(Stock stock, int position);
    }

    public void setOnStockLongClickListener(OnStockLongClickListener listener) {
        this.longClickListener = listener;
    }

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

        // Format quantity: show as integer if whole number
        double qty = currentStock.getQuantity();
        if (qty == (int) qty) {
            holder.tvQuantity.setText(String.format(Locale.getDefault(), "%d shares", (int) qty));
        } else {
            holder.tvQuantity.setText(String.format(Locale.getDefault(), "%.2f shares", qty));
        }

        double invested = qty * currentStock.getAverageBuyPrice();
        holder.tvInvested.setText(String.format(Locale.getDefault(), "Avg: \u20B9%.2f", currentStock.getAverageBuyPrice()));

        // Broker tag
        holder.tvBroker.setText(currentStock.getBrokerName());

        // Live price from batch-fetched map
        Double livePrice = livePrices.get(currentStock.getTicker());

        if (livePrice != null && livePrice > 0) {
            double currentVal = qty * livePrice;
            double pnl = currentVal - invested;
            double pnlPercent = invested > 0 ? (pnl / invested) * 100 : 0;

            holder.tvLivePrice.setText(String.format(Locale.getDefault(), "\u20B9%.2f", livePrice));
            holder.tvPnl.setText(String.format(Locale.getDefault(), "%s\u20B9%.2f (%.2f%%)",
                    pnl >= 0 ? "+" : "", pnl, pnlPercent));
            holder.tvCurrentVal.setText(String.format(Locale.getDefault(), "\u20B9%.2f", currentVal));

            int colorRes = pnl >= 0 ? R.color.accent_green : R.color.accent_red;
            int color = ContextCompat.getColor(holder.itemView.getContext(), colorRes);
            holder.tvPnl.setTextColor(color);
            holder.tvLivePrice.setTextColor(color);
        } else {
            holder.tvLivePrice.setText("--");
            holder.tvPnl.setText("Awaiting price");
            holder.tvCurrentVal.setText(String.format(Locale.getDefault(), "\u20B9%.2f", invested));
            holder.tvPnl.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_secondary));
            holder.tvLivePrice.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_secondary));
        }

        // Long-click for delete
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onStockLongClick(currentStock, position);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() { return stocks.size(); }

    public void setStocks(List<Stock> stocks) {
        this.stocks = stocks;
        notifyDataSetChanged();
    }

    public void setPrices(Map<String, Double> prices) {
        this.livePrices = prices;
        notifyDataSetChanged();
    }

    public double getTotalCurrentValue() {
        double total = 0;
        for (Stock s : stocks) {
            Double price = livePrices.get(s.getTicker());
            if (price != null && price > 0) {
                total += s.getQuantity() * price;
            } else {
                total += s.getQuantity() * s.getAverageBuyPrice();
            }
        }
        return total;
    }

    static class StockHolder extends RecyclerView.ViewHolder {
        final TextView tvTicker, tvQuantity, tvInvested, tvLivePrice, tvPnl, tvCurrentVal, tvBroker;

        StockHolder(View itemView) {
            super(itemView);
            tvTicker = itemView.findViewById(R.id.tvTicker);
            tvQuantity = itemView.findViewById(R.id.tvHoldings);
            tvInvested = itemView.findViewById(R.id.tvInvested);
            tvLivePrice = itemView.findViewById(R.id.tvLivePrice);
            tvPnl = itemView.findViewById(R.id.tvPnL);
            tvCurrentVal = itemView.findViewById(R.id.tvCurrentVal);
            tvBroker = itemView.findViewById(R.id.tvBroker);
        }
    }
}
