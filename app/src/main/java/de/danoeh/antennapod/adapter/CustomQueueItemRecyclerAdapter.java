package de.danoeh.antennapod.adapter;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.fragment.FeedSelectionFragment;
import de.danoeh.antennapod.fragment.swipeactions.CustomQueueSwipeActions;
import de.danoeh.antennapod.model.feed.CustomQueueItem;
import de.danoeh.antennapod.view.viewholder.CustomQueueItemViewHolder;

/**
 * List adapter for the queue.
 */
public class CustomQueueItemRecyclerAdapter extends SelectableAdapter<CustomQueueItemViewHolder> {

    private static final String TAG = "cQueueConfItRecycAdapt";
    private final WeakReference<MainActivity> mainActivityRef;
    private CustomQueueSwipeActions swipeActions;
    private List<CustomQueueItem> feeds = new ArrayList<>();
    private long selectedItem = -1;

    public CustomQueueItemRecyclerAdapter(MainActivity mainActivity, CustomQueueSwipeActions swipeActions) {
        super(mainActivity);
        this.mainActivityRef = new WeakReference<>(mainActivity);
        this.swipeActions = swipeActions;
        setHasStableIds(true);
    }

    public void updateDragDropEnabled() {
        notifyDataSetChanged();
    }

    @SuppressLint({"ClickableViewAccessibility", "NotifyDataSetChanged"})
    protected void afterBindViewHolder(CustomQueueItemViewHolder holder, int pos) {
        if (inActionMode()) {
            holder.dragHandle.setVisibility(View.GONE);
            holder.dragHandle.setOnTouchListener(null);
            holder.coverHolder.setOnTouchListener(null);
        } else {
            holder.dragHandle.setVisibility(View.VISIBLE);
            holder.dragHandle.setOnTouchListener((v1, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    Log.d(TAG, "startDrag()");
                    swipeActions.startDrag(holder);
                }return false;
            });
        }
        holder.deleteIcon.setOnClickListener(v -> {
            feeds.remove(pos);
            notifyDataSetChanged();
        } );
    }

    @SuppressLint("NotifyDataSetChanged")
    public void updateItems(List<CustomQueueItem> items) {
        feeds = items;
        notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void addItem(CustomQueueItem item) {
        feeds.add(item);
        notifyDataSetChanged();
    }

    @Override
    public final int getItemViewType(int position) {
        return R.id.view_type_episode_item;
    }

    @NonNull
    @Override
    public final CustomQueueItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new CustomQueueItemViewHolder(mainActivityRef.get(), parent);
    }

    @Override
    public final void onBindViewHolder(CustomQueueItemViewHolder holder, int pos) {
        // Reset state of recycled views
        holder.coverHolder.setVisibility(View.VISIBLE);
        holder.dragHandle.setVisibility(View.GONE);

        beforeBindViewHolder(holder, pos);

        CustomQueueItem item = feeds.get(pos);
        holder.bind(item);

        holder.itemView.setOnClickListener(v -> {
            MainActivity activity = mainActivityRef.get();
            if (activity != null && !inActionMode() && !holder.hasSelectedFeed()) {
                activity.loadChildFragment(FeedSelectionFragment.newInstance(pos));
            }
        });

        if (inActionMode()) {
            holder.selectCheckBox.setOnClickListener(v -> toggleSelection(holder.getBindingAdapterPosition()));
            holder.selectCheckBox.setChecked(isSelected(pos));
            holder.selectCheckBox.setVisibility(View.VISIBLE);
        } else {
            holder.selectCheckBox.setVisibility(View.GONE);
        }

        afterBindViewHolder(holder, pos);
    }

    protected void beforeBindViewHolder(CustomQueueItemViewHolder holder, int pos) {
    }

    @Override
    public void onViewRecycled(@NonNull CustomQueueItemViewHolder holder) {
        super.onViewRecycled(holder);
        // Set all listeners to null. This is required to prevent leaking fragments that have set a listener.
        // Activity -> recycledViewPool -> EpisodeItemViewHolder -> Listener -> Fragment (can not be garbage collected)
        holder.itemView.setOnClickListener(null);
        holder.itemView.setOnCreateContextMenuListener(null);
        holder.itemView.setOnLongClickListener(null);
        holder.dragHandle.setOnTouchListener(null);
        holder.coverHolder.setOnTouchListener(null);
    }

    @Override
    public long getItemId(int position) {
        CustomQueueItem item = feeds.get(position);
        return item != null ? item.getId() : RecyclerView.NO_POSITION;
    }

    @Override
    public int getItemCount() {
        return feeds.size();
    }

    protected CustomQueueItem getItem(int index) {
        return feeds.get(index);
    }

    protected Activity getActivity() {
        return mainActivityRef.get();
    }
}
