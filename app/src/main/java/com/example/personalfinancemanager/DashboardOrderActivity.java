package com.example.personalfinancemanager;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Drag-to-reorder UI for the four dashboard cards. Uses
 * {@link ItemTouchHelper} with UP|DOWN so the user can grab a row by its
 * drag handle and slide it into a new position. The order is saved on
 * every swap; MainActivity reads it in onCreate and re-applies.
 */
public class DashboardOrderActivity extends AppCompatActivity {

    private Adapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard_order);

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        RecyclerView recycler = findViewById(R.id.recyclerOrder);
        recycler.setLayoutManager(new LinearLayoutManager(this));

        List<String> order = DashboardOrderController.loadOrder(this);
        adapter = new Adapter(new ArrayList<>(order));
        recycler.setAdapter(adapter);

        ItemTouchHelper.Callback cb = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv,
                                  @NonNull RecyclerView.ViewHolder vh,
                                  @NonNull RecyclerView.ViewHolder target) {
                int from = vh.getBindingAdapterPosition();
                int to = target.getBindingAdapterPosition();
                Collections.swap(adapter.items, from, to);
                adapter.notifyItemMoved(from, to);
                DashboardOrderController.saveOrder(
                        DashboardOrderActivity.this, adapter.items);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
                /* no-op */
            }

            @Override
            public boolean isLongPressDragEnabled() {
                // We trigger drag from the handle's touch listener, not
                // long-press on the whole row — otherwise a user could
                // accidentally grab a card while scrolling the list.
                return false;
            }
        };
        ItemTouchHelper helper = new ItemTouchHelper(cb);
        helper.attachToRecyclerView(recycler);
        adapter.setDragStarter(helper::startDrag);
    }

    // ── adapter ─────────────────────────────────────────────────────────

    private static final class Adapter extends RecyclerView.Adapter<Adapter.VH> {

        interface DragStarter { void start(RecyclerView.ViewHolder vh); }

        final List<String> items;
        private DragStarter dragStarter;

        Adapter(List<String> items) { this.items = items; }

        void setDragStarter(DragStarter starter) { this.dragStarter = starter; }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_dashboard_card, parent, false);
            return new VH(v);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            String key = items.get(position);
            h.label.setText(DashboardOrderController.labelResFor(key));
            h.handle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                        && dragStarter != null) {
                    dragStarter.start(h);
                }
                return false;
            });
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView label; ImageView handle;
            VH(@NonNull View v) {
                super(v);
                label  = v.findViewById(R.id.tvLabel);
                handle = v.findViewById(R.id.dragHandle);
            }
        }
    }
}
