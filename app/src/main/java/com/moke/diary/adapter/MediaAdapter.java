package com.moke.diary.adapter;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.moke.diary.R;
import com.moke.diary.model.MediaAttachment;
import com.moke.diary.model.MediaType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 媒体附件列表适配器。
 * 支持只读预览与编辑模式：编辑模式下可删除附件，点击条目调起系统应用打开媒体文件。
 */
public class MediaAdapter extends RecyclerView.Adapter<MediaAdapter.ViewHolder> {

    /** 编辑模式下移除附件的回调。 */
    public interface OnMediaRemoveListener {
        void onRemove(int position, MediaAttachment attachment);
    }

    private final List<MediaAttachment> items = new ArrayList<>();
    private final boolean editable;
    private OnMediaRemoveListener removeListener;

    public MediaAdapter(boolean editable) {
        this.editable = editable;
    }

    public void setRemoveListener(OnMediaRemoveListener listener) {
        this.removeListener = listener;
    }

    /** 替换附件列表并刷新。 */
    public void setItems(List<MediaAttachment> attachments) {
        items.clear();
        if (attachments != null) {
            items.addAll(attachments);
        }
        notifyDataSetChanged();
    }

    public List<MediaAttachment> getItems() {
        return new ArrayList<>(items);
    }

    /** 追加一条新附件并通知插入。 */
    public void addItem(MediaAttachment attachment) {
        items.add(attachment);
        notifyItemInserted(items.size() - 1);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_media, parent, false);
        return new ViewHolder(view);
    }

    /**
     * 绑定附件信息：图片显示缩略图，音频/视频显示对应图标；
     * 编辑模式显示删除按钮，点击条目通过 FileProvider 打开外部查看器。
     */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MediaAttachment attachment = items.get(position);
        holder.mediaName.setText(attachment.fileName);
        holder.mediaType.setText(getTypeLabel(attachment.mediaType));

        MediaType type = MediaType.valueOf(attachment.mediaType);
        if (type == MediaType.IMAGE) {
            holder.mediaThumbnail.setImageBitmap(
                    BitmapFactory.decodeFile(attachment.filePath));
        } else if (type == MediaType.AUDIO) {
            holder.mediaThumbnail.setImageResource(android.R.drawable.ic_btn_speak_now);
        } else {
            holder.mediaThumbnail.setImageResource(android.R.drawable.ic_media_play);
        }

        holder.btnRemove.setVisibility(editable ? View.VISIBLE : View.GONE);
        holder.btnRemove.setOnClickListener(v -> {
            if (removeListener != null) {
                removeListener.onRemove(holder.getBindingAdapterPosition(), attachment);
            }
        });

        holder.itemView.setOnClickListener(v -> openMedia(holder, attachment, type));
    }

    /** 通过 FileProvider 生成 URI 并调起系统应用预览图片、音频或视频。 */
    private void openMedia(ViewHolder holder, MediaAttachment attachment, MediaType type) {
        File file = new File(attachment.filePath);
        if (!file.exists()) {
            Toast.makeText(holder.itemView.getContext(), "文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = FileProvider.getUriForFile(
                holder.itemView.getContext(),
                holder.itemView.getContext().getPackageName() + ".fileprovider",
                file
        );
        String mime;
        switch (type) {
            case IMAGE:
                mime = "image/*";
                break;
            case AUDIO:
                mime = "audio/*";
                break;
            default:
                mime = "video/*";
                break;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mime);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        holder.itemView.getContext().startActivity(intent);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String getTypeLabel(String typeName) {
        switch (MediaType.valueOf(typeName)) {
            case IMAGE:
                return "照片";
            case AUDIO:
                return "音频";
            case VIDEO:
                return "视频";
            default:
                return typeName;
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView mediaThumbnail;
        final TextView mediaName;
        final TextView mediaType;
        final ImageButton btnRemove;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            mediaThumbnail = itemView.findViewById(R.id.mediaThumbnail);
            mediaName = itemView.findViewById(R.id.mediaName);
            mediaType = itemView.findViewById(R.id.mediaType);
            btnRemove = itemView.findViewById(R.id.btnRemove);
        }
    }
}
