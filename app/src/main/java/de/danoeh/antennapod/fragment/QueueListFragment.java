package de.danoeh.antennapod.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;
import com.leinardi.android.speeddial.SpeedDialView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.adapter.EpisodeItemListAdapter;
import de.danoeh.antennapod.adapter.QueueListRecyclerAdapter;
import de.danoeh.antennapod.core.dialog.ConfirmationDialog;
import de.danoeh.antennapod.core.event.DownloadEvent;
import de.danoeh.antennapod.core.menuhandler.MenuItemUtils;
import de.danoeh.antennapod.core.service.download.DownloadService;
import de.danoeh.antennapod.core.storage.DBReader;
import de.danoeh.antennapod.core.storage.DBWriter;
import de.danoeh.antennapod.core.util.download.FeedUpdateManager;
import de.danoeh.antennapod.event.PlayerStatusEvent;
import de.danoeh.antennapod.event.QueueEvent;
import de.danoeh.antennapod.event.UnreadItemsUpdateEvent;
import de.danoeh.antennapod.fragment.swipeactions.QueueListSwipeActions;
import de.danoeh.antennapod.model.feed.CustomQueue;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.view.EmptyViewHandler;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * Shows all items in the queue.
 */
public class QueueListFragment extends Fragment implements Toolbar.OnMenuItemClickListener,
        EpisodeItemListAdapter.OnSelectModeListener {
    public static final String TAG = "QueueListFragment";
    private static final String KEY_UP_ARROW = "up_arrow";

    private RecyclerView recyclerView;
    private QueueListRecyclerAdapter recyclerAdapter;
    private EmptyViewHandler emptyView;
    private ProgressBar progLoading;
    private MaterialToolbar toolbar;
    private boolean displayUpArrow;

    private List<CustomQueue> queue;

    private boolean isUpdatingFeeds = false;

    private static final String PREFS = "QueueFragment";
    private static final String PREF_SHOW_LOCK_WARNING = "show_lock_warning";

    private Disposable disposable;
    private QueueSwipeActions swipeActions;
    private SharedPreferences prefs;

    private SpeedDialView speedDialView;

    public QueueListFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        prefs = getActivity().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (queue != null) {
            onFragmentLoaded(true);
        }
        loadItems(true);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onPause() {
        super.onPause();
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
    public void onEventMainThread(QueueEvent event) {
        Log.d(TAG, "onEventMainThread() called with: " + "event = [" + event + "]");
        if (queue == null) {
            return;
        } else if (recyclerAdapter == null) {
            loadItems(true);
            return;
        }
        switch(event.action) {
            case ADDED:
                recyclerAdapter.notifyItemInserted(event.position);
                break;
            case IRREVERSIBLE_REMOVED:
            case REMOVED:
                recyclerAdapter.notifyItemRemoved(event.position);
                break;
            case CLEARED:
                queue.clear();
            case SET_QUEUE:
            case SORTED:
                recyclerAdapter.notifyDataSetChanged();
                break;
            case MOVED:
                return;
        }
        onFragmentLoaded(false);
    }

    @SuppressLint("NotifyDataSetChanged")
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEventMainThread(DownloadEvent event) {
        Log.d(TAG, "onEventMainThread() called with DownloadEvent");
        if (recyclerAdapter != null) {
            recyclerAdapter.notifyDataSetChanged();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPlayerStatusChanged(PlayerStatusEvent event) {
        loadItems(false);
        refreshToolbarState();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onUnreadItemsChanged(UnreadItemsUpdateEvent event) {
        // Sent when playback position is reset
        loadItems(false);
        refreshToolbarState();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onKeyUp(KeyEvent event) {
        if (!isAdded() || !isVisible() || !isMenuVisible()) {
            return;
        }
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_T:
                recyclerView.smoothScrollToPosition(0);
                break;
            case KeyEvent.KEYCODE_B:
                recyclerView.smoothScrollToPosition(recyclerAdapter.getItemCount() - 1);
                break;
            default:
                break;
        }
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
        boolean keepSorted = UserPreferences.isQueueKeepSorted();
        toolbar.getMenu().findItem(R.id.queue_lock).setChecked(UserPreferences.isQueueLocked());
        toolbar.getMenu().findItem(R.id.queue_lock).setVisible(!keepSorted);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == R.id.queue_lock) {
            toggleQueueLock();
            return true;
        } else if (itemId == R.id.refresh_item) {
            FeedUpdateManager.runOnceOrAsk(requireContext());
            return true;
        } else if (itemId == R.id.clear_queue) {
            // make sure the user really wants to clear the queue
            ConfirmationDialog conDialog = new ConfirmationDialog(getActivity(),
                    R.string.clear_queue_label,
                    R.string.clear_queue_confirmation_msg) {

                @Override
                public void onConfirmButtonPressed(
                        DialogInterface dialog) {
                    dialog.dismiss();
                    DBWriter.clearQueue();
                }
            };
            conDialog.createNewDialog().show();
            return true;
        } else if (itemId == R.id.action_search) {
            ((MainActivity) getActivity()).loadChildFragment(SearchFragment.newInstance());
            return true;
        } else if (itemId == R.id.new_custom_queue) {
            ((MainActivity) getActivity()).loadChildFragment(new CustomQueueFragment());
        }
        return false;
    }

    private void toggleQueueLock() {
        boolean isLocked = UserPreferences.isQueueLocked();
        if (isLocked) {
            setQueueLocked(false);
        } else {
            boolean shouldShowLockWarning = prefs.getBoolean(PREF_SHOW_LOCK_WARNING, true);
            if (!shouldShowLockWarning) {
                setQueueLocked(true);
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle(R.string.lock_queue);
                builder.setMessage(R.string.queue_lock_warning);

                View view = View.inflate(getContext(), R.layout.checkbox_do_not_show_again, null);
                CheckBox checkDoNotShowAgain = view.findViewById(R.id.checkbox_do_not_show_again);
                builder.setView(view);

                builder.setPositiveButton(R.string.lock_queue, (dialog, which) -> {
                    prefs.edit().putBoolean(PREF_SHOW_LOCK_WARNING, !checkDoNotShowAgain.isChecked()).apply();
                    setQueueLocked(true);
                });
                builder.setNegativeButton(R.string.cancel_label, null);
                builder.show();
            }
        }
    }

    private void setQueueLocked(boolean locked) {
        UserPreferences.setQueueLocked(locked);
        refreshToolbarState();
        if (recyclerAdapter != null) {
            recyclerAdapter.updateDragDropEnabled();
        }
        if (queue.size() == 0) {
            if (locked) {
                ((MainActivity) getActivity()).showSnackbarAbovePlayer(R.string.queue_locked, Snackbar.LENGTH_SHORT);
            } else {
                ((MainActivity) getActivity()).showSnackbarAbovePlayer(R.string.queue_unlocked, Snackbar.LENGTH_SHORT);
            }
        }
    }

    /**
     * This method is called if the user clicks on a sort order menu item.
     *
     * @param sortOrder New sort order.
     */
    private void setSortOrder(SortOrder sortOrder) {
        UserPreferences.setQueueKeepSortedOrder(sortOrder);
        DBWriter.reorderQueue(sortOrder, true);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        Log.d(TAG, "onContextItemSelected() called with: " + "item = [" + item + "]");
        if (!isVisible() || recyclerAdapter == null) {
            return false;
        }
        return false;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View root = inflater.inflate(R.layout.queue_list_fragment, container, false);
        toolbar = root.findViewById(R.id.toolbar);
        toolbar.setOnMenuItemClickListener(this);
        displayUpArrow = getParentFragmentManager().getBackStackEntryCount() != 0;
        if (savedInstanceState != null) {
            displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW);
        }
        ((MainActivity) getActivity()).setupToolbarToggle(toolbar, displayUpArrow);
        toolbar.inflateMenu(R.menu.queue);
        refreshToolbarState();

        recyclerView = root.findViewById(R.id.recyclerView);
        RecyclerView.ItemAnimator animator = recyclerView.getItemAnimator();
        if (animator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        }
        recyclerView.setRecycledViewPool(((MainActivity) getActivity()).getRecycledViewPool());
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
        emptyView.setIcon(R.drawable.ic_playlist_shortcut);
        emptyView.setTitle(R.string.no_items_header_label);
        emptyView.setMessage(R.string.no_items_label);

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
        if (queue != null) {
            if (recyclerAdapter == null) {
                MainActivity activity = (MainActivity) getActivity();
                recyclerAdapter = new QueueListRecyclerAdapter(activity, swipeActions) {
                    @Override
                    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
                        super.onCreateContextMenu(menu, v, menuInfo);
                        MenuItemUtils.setOnClickListeners(menu, QueueListFragment.this::onContextItemSelected);
                    }
                };
                recyclerAdapter.setOnSelectModeListener(this);
                recyclerView.setAdapter(recyclerAdapter);
                emptyView.updateAdapter(recyclerAdapter);
            }
            recyclerAdapter.updateItems(queue);
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
        if (queue == null) {
            emptyView.hide();
            progLoading.setVisibility(View.VISIBLE);
        }
        disposable = Observable.fromCallable(() -> DBReader.getCustomQueues())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(items -> {
                    progLoading.setVisibility(View.GONE);
                    queue = items;
                    queue.add(0, new CustomQueue(-1, getString(R.string.default_queue)));
                    onFragmentLoaded(restoreScrollPosition);
                    if (recyclerAdapter != null) {
                        recyclerAdapter.notifyDataSetChanged();
                    }
                }, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    @Override
    public void onStartSelectMode() {
        swipeActions.detach();
        speedDialView.setVisibility(View.VISIBLE);
        refreshToolbarState();
    }

    @Override
    public void onEndSelectMode() {
        speedDialView.close();
        speedDialView.setVisibility(View.GONE);
        swipeActions.attachTo(recyclerView);
    }

    private class QueueSwipeActions extends QueueListSwipeActions {

        // Position tracking whilst dragging
        int dragFrom = -1;
        int dragTo = -1;

        public QueueSwipeActions() {
            super(ItemTouchHelper.UP | ItemTouchHelper.DOWN, QueueListFragment.this, TAG);
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
            if (from >= queue.size() || to >= queue.size() || from < 0 || to < 0) {
                return false;
            }
            queue.add(to, queue.remove(from));
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
            return false;
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
            DBWriter.moveQueueItem(from, to, true);
        }

    }
}
