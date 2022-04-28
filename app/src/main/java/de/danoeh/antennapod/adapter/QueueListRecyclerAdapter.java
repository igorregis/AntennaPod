package de.danoeh.antennapod.adapter;

import android.annotation.SuppressLint;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.fragment.QueueFragment;
import de.danoeh.antennapod.fragment.swipeactions.QueueListSwipeActions;
import de.danoeh.antennapod.fragment.swipeactions.SwipeActions;
import de.danoeh.antennapod.model.feed.CustomQueue;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.view.viewholder.CustomQueueViewHolder;
import de.danoeh.antennapod.view.viewholder.EpisodeItemViewHolder;

/**
 * List adapter for the queue.
 */
public class QueueListRecyclerAdapter  extends SelectableAdapter<CustomQueueViewHolder>
        implements View.OnCreateContextMenuListener {
    private static final String TAG = "QueueRecyclerAdapter";

    private final QueueListSwipeActions swipeActions;
    private boolean dragDropEnabled;
    private List<CustomQueue> queue;


    public QueueListRecyclerAdapter(MainActivity mainActivity, QueueListSwipeActions swipeActions) {
        super(mainActivity);
        this.swipeActions = swipeActions;
        dragDropEnabled = ! (UserPreferences.isQueueKeepSorted() || UserPreferences.isQueueLocked());
    }

    @SuppressLint("NotifyDataSetChanged")
    public void updateDragDropEnabled() {
        dragDropEnabled = ! (UserPreferences.isQueueKeepSorted() || UserPreferences.isQueueLocked());
        notifyDataSetChanged();
    }

    protected void afterBindViewHolder(CustomQueueViewHolder holder, int pos) {
        if (!dragDropEnabled || inActionMode()) {
            holder.dragHandle.setVisibility(View.GONE);
            holder.dragHandle.setOnTouchListener(null);
        } else {
            holder.dragHandle.setVisibility(View.VISIBLE);
            holder.dragHandle.setOnTouchListener((v1, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    Log.d(TAG, "startDrag()");
                    swipeActions.startDrag(holder);
                }
                return false;
            });
        }

        holder.itemView.setOnClickListener(e -> {
            getActivity().loadChildFragment(new QueueFragment(holder.getItem().getId()));
        } );
    }

    @Override
    public void onCreateContextMenu(final ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.queue_context, menu);
    }

    @NonNull
    @Override
    public CustomQueueViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new CustomQueueViewHolder(getActivity(), parent);
    }

    private MainActivity getActivity() {
        return (MainActivity) activity;
    }

    @Override
    public void onBindViewHolder(@NonNull CustomQueueViewHolder holder, int position) {
        holder.bind(queue.get(position));
        afterBindViewHolder(holder, position);
    }

    @Override
    public int getItemCount() {
        if (queue == null)
            return 0;
        return queue.size();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void updateItems(List<CustomQueue> queue) {
        this.queue = queue;
        notifyDataSetChanged();
    }
}
