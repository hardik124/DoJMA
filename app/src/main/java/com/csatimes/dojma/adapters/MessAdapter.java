package com.csatimes.dojma.adapters;

import android.content.Intent;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import com.csatimes.dojma.R;
import com.csatimes.dojma.activities.viewImage;
import com.csatimes.dojma.models.MessItem;
import com.csatimes.dojma.viewholders.MessItemViewHolder;

import io.realm.RealmList;

/**
 * Created by vikramaditya on 22/12/16.
 */

public class MessAdapter extends RecyclerView.Adapter<MessItemViewHolder> {

    private RealmList<MessItem> messItems;

    public MessAdapter(RealmList<MessItem> messItems) {
        this.messItems = messItems;
    }

    @Override
    public MessItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new MessItemViewHolder(View.inflate(parent.getContext(), R.layout.item_format_mess_menu, null));
    }

    @Override
    public void onBindViewHolder(final MessItemViewHolder holder, final int position) {
        holder.title.setText(messItems.get(position).getTitle());
        holder.image.setImageURI(messItems.get(position).getImageUrl());
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent openImage = new Intent(holder.itemView.getContext(), viewImage.class);
                openImage.putExtra("Title", messItems.get(position).getTitle());
                openImage.putExtra("ImageUrl", messItems.get(position).getImageUrl());
                holder.itemView.getContext().startActivity(openImage);
            }
        });
    }

    @Override
    public int getItemCount() {
        return messItems.size();
    }
}
