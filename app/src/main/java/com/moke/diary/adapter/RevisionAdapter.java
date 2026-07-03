package com.moke.diary.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.moke.diary.R;
import com.moke.diary.model.DiaryRevision;
import com.moke.diary.model.Mood;
import com.moke.diary.util.DateUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 日记修订历史时间线适配器。
 * 按时间倒序展示每次修订的操作类型、变更详情或标题预览。
 */
public class RevisionAdapter extends RecyclerView.Adapter<RevisionAdapter.ViewHolder> {

    private final List<DiaryRevision> items = new ArrayList<>();

    /** 替换修订记录列表并刷新。 */
    public void setItems(List<DiaryRevision> revisions) {
        items.clear();
        if (revisions != null) {
            items.addAll(revisions);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_revision, parent, false);
        return new ViewHolder(view);
    }

    /**
     * 绑定单条修订记录：有变更日志时展示详细 diff，否则展示标题与心情预览；
     * 列表末项（最早记录）标记为「创建」，其余为「编辑」。
     */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DiaryRevision revision = items.get(position);
        boolean isFirst = position == items.size() - 1;

        if (!TextUtils.isEmpty(revision.changeLog)) {
            holder.revisionAction.setText(isFirst
                    ? holder.itemView.getContext().getString(R.string.revision_created)
                    : holder.itemView.getContext().getString(R.string.revision_edited));
            holder.revisionDetails.setText(revision.changeLog);
            holder.revisionDetails.setVisibility(View.VISIBLE);
            holder.revisionPreview.setVisibility(View.GONE);
        } else {
            holder.revisionAction.setText(isFirst
                    ? holder.itemView.getContext().getString(R.string.revision_created)
                    : holder.itemView.getContext().getString(R.string.revision_edited));
            holder.revisionPreview.setText(revision.title + " · "
                    + Mood.fromName(revision.mood).display());
            holder.revisionPreview.setVisibility(View.VISIBLE);
            holder.revisionDetails.setVisibility(View.GONE);
        }

        holder.revisionTime.setText(DateUtil.formatFull(revision.revisedAt));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView revisionAction;
        final TextView revisionTime;
        final TextView revisionPreview;
        final TextView revisionDetails;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            revisionAction = itemView.findViewById(R.id.revisionAction);
            revisionTime = itemView.findViewById(R.id.revisionTime);
            revisionPreview = itemView.findViewById(R.id.revisionPreview);
            revisionDetails = itemView.findViewById(R.id.revisionDetails);
        }
    }
}
