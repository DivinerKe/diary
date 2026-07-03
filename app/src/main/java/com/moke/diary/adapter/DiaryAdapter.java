package com.moke.diary.adapter;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.moke.diary.R;
import com.moke.diary.model.DiaryWithMedia;
import com.moke.diary.model.Mood;
import com.moke.diary.util.DateUtil;

import java.util.ArrayList;
import java.util.List;

public class DiaryAdapter extends RecyclerView.Adapter<DiaryAdapter.ViewHolder> {

    public interface OnDiaryClickListener {
        void onDiaryClick(DiaryWithMedia diary);
    }

    private final List<DiaryWithMedia> items = new ArrayList<>();
    private final OnDiaryClickListener listener;

    public DiaryAdapter(OnDiaryClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<DiaryWithMedia> diaries) {
        items.clear();
        if (diaries != null) {
            items.addAll(diaries);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_diary, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Context context = holder.itemView.getContext();
        applyThemeToCard(context, holder.diaryCard);

        DiaryWithMedia diary = items.get(position);
        holder.titleText.setText(diary.entry.title);
        holder.moodText.setText(Mood.fromName(diary.entry.mood).display());

        if (diary.entry.encrypted) {
            holder.contentPreview.setText(R.string.encrypted_content);
            holder.encryptedBadge.setVisibility(View.VISIBLE);
        } else {
            holder.contentPreview.setText(diary.entry.content);
            holder.encryptedBadge.setVisibility(View.GONE);
        }

        holder.timeText.setText(context.getString(
                R.string.updated_at, DateUtil.formatFull(diary.entry.updatedAt)));
        holder.cardContainer.setBackgroundColor(diary.entry.backgroundColor);

        int textColor = isLightColor(diary.entry.backgroundColor) ? Color.BLACK : Color.WHITE;
        holder.titleText.setTextColor(textColor);
        holder.contentPreview.setTextColor(isLightColor(diary.entry.backgroundColor)
                ? Color.DKGRAY : Color.LTGRAY);
        holder.timeText.setTextColor(isLightColor(diary.entry.backgroundColor)
                ? Color.GRAY : Color.LTGRAY);

        if (diary.mediaList != null && !diary.mediaList.isEmpty()) {
            holder.mediaCount.setVisibility(View.VISIBLE);
            holder.mediaCount.setText("📎 " + diary.mediaList.size());
        } else {
            holder.mediaCount.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> listener.onDiaryClick(diary));
    }

    private static void applyThemeToCard(Context context, MaterialCardView card) {
        TypedValue corner = new TypedValue();
        TypedValue elevation = new TypedValue();
        if (context.getTheme().resolveAttribute(R.attr.diaryCardCornerRadius, corner, true)) {
            card.setRadius(TypedValue.complexToDimensionPixelSize(
                    corner.data, context.getResources().getDisplayMetrics()));
        }
        if (context.getTheme().resolveAttribute(R.attr.diaryCardElevation, elevation, true)) {
            card.setCardElevation(TypedValue.complexToDimension(
                    elevation.data, context.getResources().getDisplayMetrics()));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static boolean isLightColor(int color) {
        double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color)
                + 0.114 * Color.blue(color)) / 255;
        return luminance > 0.5;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView diaryCard;
        final View cardContainer;
        final TextView titleText;
        final TextView moodText;
        final TextView contentPreview;
        final TextView timeText;
        final TextView encryptedBadge;
        final TextView mediaCount;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            diaryCard = itemView.findViewById(R.id.diaryCard);
            cardContainer = itemView.findViewById(R.id.cardContainer);
            titleText = itemView.findViewById(R.id.titleText);
            moodText = itemView.findViewById(R.id.moodText);
            contentPreview = itemView.findViewById(R.id.contentPreview);
            timeText = itemView.findViewById(R.id.timeText);
            encryptedBadge = itemView.findViewById(R.id.encryptedBadge);
            mediaCount = itemView.findViewById(R.id.mediaCount);
        }
    }
}
