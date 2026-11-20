package com.example.gomokutactics.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gomokutactics.R;
import com.example.gomokutactics.models.BoardTheme;

import java.util.List;

public class SkinAdapter extends RecyclerView.Adapter<SkinAdapter.ViewHolder> {

    private final Context context;
    private final List<BoardTheme> themes;
    private int selectedId;
    private final OnSkinSelectListener listener;

    public interface OnSkinSelectListener {
        void onSkinSelected(BoardTheme theme);
    }

    public SkinAdapter(Context context, List<BoardTheme> themes, int selectedId, OnSkinSelectListener listener) {
        this.context = context;
        this.themes = themes;
        this.selectedId = selectedId;
        this.listener = listener;
    }

    public void setSelectedId(int selectedId) {
        this.selectedId = selectedId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_skin, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BoardTheme theme = themes.get(position);
        holder.viewPreview.setBackgroundColor(theme.getBoardBgColor());
        holder.tvName.setText(theme.getName());
        holder.tvPieces.setText(theme.getDescription());
        holder.tvBadge.setVisibility(theme.getId() == selectedId ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> listener.onSkinSelected(theme));
    }

    @Override
    public int getItemCount() { return themes.size(); }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        View viewPreview;
        TextView tvName, tvPieces, tvBadge;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            viewPreview = itemView.findViewById(R.id.viewColorPreview);
            tvName = itemView.findViewById(R.id.tvSkinName);
            tvPieces = itemView.findViewById(R.id.tvSkinPieces);
            tvBadge = itemView.findViewById(R.id.tvSkinSelectedBadge);
        }
    }
}