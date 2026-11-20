package com.example.gomokutactics.adapters;

import android.content.Context;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gomokutactics.R;
import com.example.gomokutactics.models.MatchRecord;
import com.example.gomokutactics.utils.GamePrefs;

import java.util.List;

public class MatchHistoryAdapter extends RecyclerView.Adapter<MatchHistoryAdapter.ViewHolder> {

    private final Context context;
    private final List<MatchRecord> items;
    private final OnItemClickListener listener;
    private int position = RecyclerView.NO_POSITION;

    public interface OnItemClickListener {
        void onItemClick(MatchRecord item);
    }

    public MatchHistoryAdapter(Context context, List<MatchRecord> items, OnItemClickListener listener) {
        this.context = context;
        this.items = items;
        this.listener = listener;
    }

    public void updateItems(List<MatchRecord> newItems) {
        this.items.clear();
        this.items.addAll(newItems);
        this.position = RecyclerView.NO_POSITION;
        notifyDataSetChanged();
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_match_history, parent, false);
        return new ViewHolder(v, context);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MatchRecord item = items.get(position);
        String localizedMode = GamePrefs.getLocalizedGameMode(context, item.getGameMode());
        String localizedResult = GamePrefs.getLocalizedResult(context, item.getResult());

        holder.tvModeBadge.setText(context.getString(
                R.string.history_board_badge, localizedMode, item.getBoardSize()));
        holder.tvDetails.setText(context.getString(
                R.string.history_total_moves, item.getTotalMoves()));
        holder.tvDate.setText(item.getPlayedDate());
        holder.tvResult.setText(localizedResult);

        String rawResult = item.getResult();
        int badgeColorRes;
        if (rawResult.contains("THẮNG") || rawResult.contains("WIN")) {
            badgeColorRes = R.color.win_green;
        } else if (rawResult.contains("THUA") || rawResult.contains("LOSS")) {
            badgeColorRes = R.color.lose_red;
        } else {
            badgeColorRes = R.color.draw_amber;
        }
        holder.tvResult.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(ContextCompat.getColor(context, badgeColorRes)));

        holder.itemView.setOnClickListener(v -> listener.onItemClick(item));
        holder.itemView.setOnLongClickListener(v -> {
            int adapterPos = holder.getAdapterPosition();
            if (adapterPos != RecyclerView.NO_POSITION) {
                setPosition(adapterPos);
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnCreateContextMenuListener {
        private final Context context;
        TextView tvModeBadge, tvDetails, tvDate, tvResult;

        public ViewHolder(@NonNull View itemView, Context context) {
            super(itemView);
            this.context = context;
            tvModeBadge = itemView.findViewById(R.id.tvHistoryModeBadge);
            tvDetails = itemView.findViewById(R.id.tvHistoryDetails);
            tvDate = itemView.findViewById(R.id.tvHistoryDate);
            tvResult = itemView.findViewById(R.id.tvHistoryResult);
            itemView.setOnCreateContextMenuListener(this);
        }

        @Override
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
            menu.setHeaderTitle(context.getString(R.string.ctx_menu_title));
            menu.add(0, R.id.ctx_replay_match, 0, context.getString(R.string.ctx_replay));
            menu.add(0, R.id.ctx_share_match, 1, context.getString(R.string.ctx_share));
            menu.add(0, R.id.ctx_delete_match, 2, context.getString(R.string.ctx_delete));
        }
    }
}