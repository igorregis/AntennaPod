package de.danoeh.antennapod.adapter;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.fragment.FeedSelectionFragment;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.fragment.FeedItemlistFragment;
import de.danoeh.antennapod.ui.common.SquareImageView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class FeedSearchResultAdapter extends RecyclerView.Adapter<FeedSearchResultAdapter.Holder> {

    private final WeakReference<MainActivity> mainActivityRef;

    private int positionToConfigure;
    private boolean backOnClick = false;
    private final List<Feed> data = new ArrayList<>();

    public FeedSearchResultAdapter(MainActivity mainActivity) {
        this.mainActivityRef = new WeakReference<>(mainActivity);
    }

    public FeedSearchResultAdapter(MainActivity mainActivity, boolean backOnClick) {
        this.mainActivityRef = new WeakReference<>(mainActivity);
        this.backOnClick = backOnClick;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void updateData(List<Feed> newData) {
        data.clear();
        data.addAll(newData);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View convertView = View.inflate(mainActivityRef.get(), R.layout.searchlist_item_feed, null);
        return new Holder(convertView);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        final Feed podcast = data.get(position);
        holder.imageView.setContentDescription(podcast.getTitle());
        if (backOnClick) {
            holder.imageView.setOnClickListener(v -> {
                if (mainActivityRef.get() != null) {
                    Bundle bundle = new Bundle();
                    bundle.putLong(FeedSelectionFragment.PODCAST_ID, podcast.getId());
                    bundle.putInt(FeedSelectionFragment.POSITION_TO_CONFIGURE, positionToConfigure);
                    mainActivityRef.get().getSupportFragmentManager().setFragmentResult(FeedSelectionFragment.FEED_SELECTION, bundle);
                    mainActivityRef.get().onBackPressed();
                }
            });

        }else {
            holder.imageView.setOnClickListener(v ->
                    mainActivityRef.get().loadChildFragment(FeedItemlistFragment.newInstance(podcast.getId())));
        }

        Glide.with(mainActivityRef.get())
                .load(podcast.getImageUrl())
                .apply(new RequestOptions()
                        .placeholder(R.color.light_gray)
                        .fitCenter()
                        .dontAnimate())
                .into(holder.imageView);
    }

    @Override
    public long getItemId(int position) {
        return data.get(position).getId();
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    public void setPositionToConfigure(int positionToConfigure) {
        this.positionToConfigure = positionToConfigure;
    }

    static class Holder extends RecyclerView.ViewHolder {
        SquareImageView imageView;

        public Holder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.discovery_cover);
            imageView.setDirection(SquareImageView.DIRECTION_HEIGHT);
        }
    }
}
