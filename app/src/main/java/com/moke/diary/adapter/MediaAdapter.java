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

public class MediaAdapter extends RecyclerView.Adapter<MediaAdapter.ViewHolder> {

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
