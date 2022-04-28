package de.danoeh.antennapod.fragment;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentResultListener;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.adapter.CustomQueueItemRecyclerAdapter;
import de.danoeh.antennapod.adapter.EpisodeItemListAdapter;
import de.danoeh.antennapod.core.storage.DBReader;
import de.danoeh.antennapod.core.storage.DBWriter;
import de.danoeh.antennapod.core.util.download.FeedUpdateManager;
import de.danoeh.antennapod.event.CustomQueueEvent;
import de.danoeh.antennapod.fragment.swipeactions.CustomQueueSwipeActions;
import de.danoeh.antennapod.model.feed.CustomQueue;
import de.danoeh.antennapod.model.feed.CustomQueueItem;
import de.danoeh.antennapod.view.EmptyViewHandler;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * Configuration screen for a custom queueing.
 */
public class CustomQueueFragment extends Fragment implements Toolbar.OnMenuItemClickListener,
        EpisodeItemListAdapter.OnSelectModeListener {
    public static final String TAG = "CustomQueueFragment";
    private static final String KEY_UP_ARROW = "up_arrow";

    private RecyclerView recyclerView;
    private CustomQueueItemRecyclerAdapter recyclerAdapter;
    private EmptyViewHandler emptyView;
    private ProgressBar progLoading;
    private MaterialToolbar toolbar;
    private boolean displayUpArrow;

    private CustomQueue customQueue;

    private static final String PREFS = "CustomQueueFragment";

    private Disposable disposable;
    private CustomQueueSwipeActions swipeActions;
    private SharedPreferences prefs;

    private long customQueueId;

    /*
     * Used to identify a list change not saved, to warning the user before exit the fragment
     */
    private boolean saved;
    private EditText nameEditText;

    CustomQueueFragment() {
        this(-1);
    }

    public CustomQueueFragment(long customQueueId) {
        this.customQueueId = customQueueId;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        prefs = getActivity().getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        getParentFragmentManager().setFragmentResultListener(FeedSelectionFragment.FEED_SELECTION, this, new FragmentResultListener() {
            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void onFragmentResult(@NonNull String requestKey, @NonNull Bundle bundle) {
                Long result = bundle.getLong(FeedSelectionFragment.PODCAST_ID);
                CustomQueueItem customQueueItem = customQueue.getCustomQueueItems().get(bundle.getInt(FeedSelectionFragment.POSITION_TO_CONFIGURE));
                customQueueItem.setFeed(result);
                customQueueItem.setFeedObject(DBReader.getFeed(result));
                recyclerAdapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        if (customQueue != null) {
            onFragmentLoaded(true);
        }
        loadItems(true);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
        if (disposable != null) {
            disposable.dispose();
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(CustomQueueEvent event) {
        Log.d(TAG, "onEventMainThread() called with: " + "event = [" + event + "]");
        if (customQueue == null) {
            return;
        } else if (recyclerAdapter == null) {
            loadItems(true);
            return;
        }
        switch(event.action) {
            case ADDED:
                customQueue.getCustomQueueItems().add(event.position, event.item);
                recyclerAdapter.notifyItemInserted(event.position);
                break;
            case SET_QUEUE:
            case SORTED: //Deliberate fall-through
                customQueue.setCustomQueueItems(event.items);
                recyclerAdapter.notifyDataSetChanged();
                break;
            case REMOVED:
            case IRREVERSIBLE_REMOVED:
                for(int i = 0; i < customQueue.getCustomQueueItems().size(); i++) {
                    CustomQueueItem item = customQueue.getCustomQueueItems().get(i);
                    if(item != null && item.getId() == event.item.getId()) {
                        customQueue.getCustomQueueItems().remove(i);
                        recyclerAdapter.notifyItemRemoved(i);
                    }
                }
                break;
            case CLEARED:
                customQueue.getCustomQueueItems().clear();
                recyclerAdapter.notifyDataSetChanged();
                break;
            case MOVED:
                return;
        }
        onFragmentLoaded(false);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (recyclerAdapter != null) {
            recyclerAdapter.endSelectMode();
        }
        recyclerAdapter = null;
    }

    private void refreshToolbarState() {
        toolbar.getMenu().findItem(R.id.action_add_item_custom_queue).setEnabled(true);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == R.id.action_add_item_custom_queue) {
            recyclerAdapter.addItem(new CustomQueueItem(customQueueId, recyclerAdapter.getItemCount() - 1));
            return true;
        } else if (itemId == R.id.action_save_custom_queue) {
            saveCustomQueue();
        }
        return false;
    }

    @SuppressLint("NotifyDataSetChanged")
    private void saveCustomQueue() {
        saveCustomQueue(true);
    }

        @SuppressLint("NotifyDataSetChanged")
    private void saveCustomQueue(boolean existScreen) {
        boolean listChanged = false;
        if (customQueue != null && customQueue.getCustomQueueItems() != null) {
            for (int i = 0; i < customQueue.getCustomQueueItems().size(); i++) {
                CustomQueueItem item = customQueue.getCustomQueueItems().get(i);
                if (item.getFeedObject() == null) {
                    customQueue.getCustomQueueItems().remove(i--);
                    listChanged = true;
                }
            }
            if (listChanged) {
                recyclerAdapter.notifyDataSetChanged();
            }
        }

        customQueue.setName(nameEditText.getText().toString());

        if (customQueue.getName() == null) {
            new AlertDialog.Builder(getActivity()).setTitle(R.string.warning).setMessage(R.string.type_name_for_custom_queue).setNeutralButton(android.R.string.ok, null ).show();
            return;
        }
        DBWriter.saveCustomQueue(customQueue);
        DBWriter.rebuildCustomQueues(getActivity(), true);
        if (existScreen){
            recyclerAdapter.notifyDataSetChanged();
            ((MainActivity)getActivity()).onBackPressed();
        }
        saved = true;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View root = inflater.inflate(R.layout.custom_queue_fragment, container, false);
        toolbar = root.findViewById(R.id.toolbar);
        toolbar.setOnMenuItemClickListener(this);
        displayUpArrow = getParentFragmentManager().getBackStackEntryCount() != 0;
        if (savedInstanceState != null) {
            displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW);
        }
        ((MainActivity) getActivity()).setupToolbarToggle(toolbar, displayUpArrow);
        toolbar.inflateMenu(R.menu.custom_queue);
        refreshToolbarState();

        nameEditText = root.findViewById(R.id.queueNameEditText);
        
        recyclerView = root.findViewById(R.id.recyclerView);
        RecyclerView.ItemAnimator animator = recyclerView.getItemAnimator();
        if (animator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        }
        registerForContextMenu(recyclerView);

        SwipeRefreshLayout swipeRefreshLayout = root.findViewById(R.id.swipeRefresh);
        swipeRefreshLayout.setDistanceToTriggerSync(getResources().getInteger(R.integer.swipe_refresh_distance));
        swipeRefreshLayout.setOnRefreshListener(() -> {
            FeedUpdateManager.runOnceOrAsk(requireContext());
            new Handler(Looper.getMainLooper()).postDelayed(() -> swipeRefreshLayout.setRefreshing(false),
                    getResources().getInteger(R.integer.swipe_to_refresh_duration_in_ms));
        });

        swipeActions = new QueueSwipeActions();
        swipeActions.attachTo(recyclerView);

        emptyView = new EmptyViewHandler(getContext());
        emptyView.attachToRecyclerView(recyclerView);
//        emptyView.setIcon(R.drawable.ic_playlist);
        emptyView.setTitle(R.string.no_subscriptions_to_customize_queue_title);
        emptyView.setMessage(R.string.add_subscriptions_to_customize_queue_text);

        progLoading = root.findViewById(R.id.progLoading);
        progLoading.setVisibility(View.VISIBLE);
        return root;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow);
        super.onSaveInstanceState(outState);
    }

    private void onFragmentLoaded(final boolean restoreScrollPosition) {
        if (customQueue != null) {
            if (recyclerAdapter == null) {
                MainActivity activity = (MainActivity) getActivity();
                recyclerAdapter = new CustomQueueItemRecyclerAdapter(activity, swipeActions);
                recyclerAdapter.setOnSelectModeListener(this);
                recyclerView.setAdapter(recyclerAdapter);
                emptyView.updateAdapter(recyclerAdapter);
            }
            recyclerAdapter.updateItems(customQueue.getCustomQueueItems());
            nameEditText.setText(customQueue.getName());
        } else {
            recyclerAdapter = null;
            emptyView.updateAdapter(null);
        }

        // we need to refresh the options menu because it sometimes
        // needs data that may have just been loaded.
        refreshToolbarState();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void loadItems(final boolean restoreScrollPosition) {
        Log.d(TAG, "loadItems()");
        if (disposable != null) {
            disposable.dispose();
        }
        if (customQueue == null) {
            emptyView.updateVisibility();
            progLoading.setVisibility(View.GONE);
        }
        disposable = Observable.fromCallable(() -> DBReader.getCustomQueue(customQueueId))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(customQueue -> {
                    progLoading.setVisibility(View.GONE);
                    this.customQueue = customQueue;
                    onFragmentLoaded(restoreScrollPosition);
                    if (recyclerAdapter != null) {
                        recyclerAdapter.notifyDataSetChanged();
                    }
                }, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    @Override
    public void onStartSelectMode() {
        swipeActions.detach();
        refreshToolbarState();
    }

    @Override
    public void onEndSelectMode() {
        swipeActions.attachTo(recyclerView);
    }

    private class QueueSwipeActions extends CustomQueueSwipeActions {

        // Position tracking whilst dragging
        int dragFrom = -1;
        int dragTo = -1;

        public QueueSwipeActions() {
            super(ItemTouchHelper.UP | ItemTouchHelper.DOWN, CustomQueueFragment.this, TAG);
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder,
                              @NonNull RecyclerView.ViewHolder target) {
            int fromPosition = viewHolder.getBindingAdapterPosition();
            int toPosition = target.getBindingAdapterPosition();

            // Update tracked position
            if (dragFrom == -1) {
                dragFrom =  fromPosition;
            }
            dragTo = toPosition;

            int from = viewHolder.getBindingAdapterPosition();
            int to = target.getBindingAdapterPosition();
            Log.d(TAG, "move(" + from + ", " + to + ") in memory");
            if (from >= customQueue.getCustomQueueItems().size() || to >= customQueue.getCustomQueueItems().size() || from < 0 || to < 0) {
                return false;
            }
            customQueue.getCustomQueueItems().add(to, customQueue.getCustomQueueItems().remove(from));
            for(int i = 0; i < customQueue.getCustomQueueItems().size(); i++) {
                customQueue.getCustomQueueItems().get(i).setPosition(i);
            }
            recyclerAdapter.notifyItemMoved(from, to);
            return true;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            if (disposable != null) {
                disposable.dispose();
            }

            //SwipeActions
            super.onSwiped(viewHolder, direction);
        }

        @Override
        public boolean isLongPressDragEnabled() {
            return true;
        }

        @Override
        public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            // Check if drag finished
            if (dragFrom != -1 && dragTo != -1 && dragFrom != dragTo) {
                reallyMoved(dragFrom, dragTo);
            }

            dragFrom = dragTo = -1;
        }

        private void reallyMoved(int from, int to) {
            // Write drag operation to database
            Log.d(TAG, "Write to database move(" + from + ", " + to + ")");
//            saveCustomQueue(false);
        }

    }
}
