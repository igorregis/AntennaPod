package de.danoeh.antennapod.fragment;


import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.adapter.EpisodeItemListAdapter;
import de.danoeh.antennapod.adapter.FeedSearchResultAdapter;
import de.danoeh.antennapod.core.event.DownloadEvent;
import de.danoeh.antennapod.core.event.DownloaderUpdate;
import de.danoeh.antennapod.core.menuhandler.MenuItemUtils;
import de.danoeh.antennapod.core.storage.FeedSearcher;
import de.danoeh.antennapod.core.util.FeedItemUtil;
import de.danoeh.antennapod.event.FeedItemEvent;
import de.danoeh.antennapod.event.PlayerStatusEvent;
import de.danoeh.antennapod.event.UnreadItemsUpdateEvent;
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent;
import de.danoeh.antennapod.menuhandler.FeedItemMenuHandler;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import de.danoeh.antennapod.storage.database.mapper.FeedCursorMapper;
import de.danoeh.antennapod.view.EmptyViewHandler;
import de.danoeh.antennapod.view.EpisodeItemListRecyclerView;
import de.danoeh.antennapod.view.viewholder.EpisodeItemViewHolder;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * List all feeds and allow user to filter.
 */
public class FeedSelectionFragment extends Fragment {
    public static final String PODCAST_ID = "";
    public static final String FEED_SELECTION = "feedSelection";
    public static final String POSITION_TO_CONFIGURE = "customIdToConfigure";
    private static final String TAG = "FeedSelectionFragment";
    private static final String ARG_QUERY = "query";
    private static final String ARG_FEED = "feed";
    private static final String ARG_FEED_NAME = "feedName";
    private static final int SEARCH_DEBOUNCE_INTERVAL = 1500;
    private static int customQueueItemToConfigure;

    private FeedSearchResultAdapter adapterFeeds;
    private Disposable disposable;
    private ProgressBar progressBar;
    private EmptyViewHandler emptyViewHandler;
    private Chip chip;
    private SearchView searchView;
    private Handler automaticSearchDebouncer;
    private long lastQueryChange = 0;

    /**
     * Create a new SearchFragment that searches all feeds.
     * @param customQueueItemToConfigure
     */
    public static FeedSelectionFragment newInstance(int customQueueItemToConfigure) {
        FeedSelectionFragment fragment = new FeedSelectionFragment();
        fragment.customQueueItemToConfigure = customQueueItemToConfigure;
        Bundle args = new Bundle();
        args.putLong(ARG_FEED, 0);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        automaticSearchDebouncer = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onStop() {
        super.onStop();
        if (disposable != null) {
            disposable.dispose();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.feed_selection_fragment, container, false);
        setupToolbar(layout.findViewById(R.id.toolbar));
        progressBar = layout.findViewById(R.id.progressBar);

        RecyclerView recyclerViewFeeds = layout.findViewById(R.id.recyclerViewFeeds);
        LinearLayoutManager layoutManagerFeeds = new LinearLayoutManager(getActivity());
        layoutManagerFeeds.setOrientation(RecyclerView.HORIZONTAL);
        recyclerViewFeeds.setLayoutManager(layoutManagerFeeds);
        adapterFeeds = new FeedSearchResultAdapter((MainActivity) getActivity(), true);
        adapterFeeds.setPositionToConfigure(customQueueItemToConfigure);
        recyclerViewFeeds.setAdapter(adapterFeeds);

        emptyViewHandler = new EmptyViewHandler(getContext());
        emptyViewHandler.attachToRecyclerView(recyclerViewFeeds);
        emptyViewHandler.setIcon(R.drawable.ic_search);
        emptyViewHandler.setTitle(R.string.search_status_no_results);
        emptyViewHandler.setMessage(R.string.type_to_search);
        emptyViewHandler.updateAdapter(adapterFeeds);
//        EventBus.getDefault().register(this);

        chip = layout.findViewById(R.id.feed_title_chip);
        chip.setOnCloseIconClickListener(v -> {
            getArguments().putLong(ARG_FEED, 0);
            searchWithProgressBar();
        });
        chip.setVisibility((getArguments().getLong(ARG_FEED, 0) == 0) ? View.GONE : View.VISIBLE);
        chip.setText(getArguments().getString(ARG_FEED_NAME, ""));
        search();
        searchView.setOnQueryTextFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                showInputMethod(view.findFocus());
            }
        });
        return layout;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        EventBus.getDefault().unregister(this);
    }

    private void setupToolbar(Toolbar toolbar) {
        toolbar.setTitle(R.string.search_label);
        toolbar.setNavigationOnClickListener(v -> getParentFragmentManager().popBackStack());
        toolbar.inflateMenu(R.menu.search);

        MenuItem item = toolbar.getMenu().findItem(R.id.action_search);
        item.expandActionView();
        searchView = (SearchView) item.getActionView();
        searchView.setQueryHint(getString(R.string.search_label));
        searchView.requestFocus();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                searchView.clearFocus();
                searchWithProgressBar();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                automaticSearchDebouncer.removeCallbacksAndMessages(null);
                if (s.isEmpty() || s.endsWith(" ") || (lastQueryChange != 0
                        && System.currentTimeMillis() > lastQueryChange + SEARCH_DEBOUNCE_INTERVAL)) {
                    search();
                } else {
                    automaticSearchDebouncer.postDelayed(() -> {
                        search();
                        lastQueryChange = 0; // Don't search instantly with first symbol after some pause
                    }, SEARCH_DEBOUNCE_INTERVAL / 2);
                }
                lastQueryChange = System.currentTimeMillis();
                return false;
            }
        });
        item.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                getParentFragmentManager().popBackStack();
                return true;
            }
        });
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        return false;
    }

    private void searchWithProgressBar() {
        progressBar.setVisibility(View.VISIBLE);
        emptyViewHandler.hide();
        search();
    }

    private void search() {
        if (disposable != null) {
            disposable.dispose();
        }
        chip.setVisibility((getArguments().getLong(ARG_FEED, 0) == 0) ? View.GONE : View.VISIBLE);
        disposable = Observable.fromCallable(this::performSearch)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(results -> {
                    progressBar.setVisibility(View.GONE);
                    adapterFeeds.updateData(results);
                    emptyViewHandler.updateVisibility();
                    if (searchView.getQuery().toString().isEmpty()) {
                        emptyViewHandler.setMessage(R.string.type_to_search);
                    } else {
                        emptyViewHandler.setMessage(getString(R.string.no_results_for_query, searchView.getQuery()));
                    }
                }, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    @NonNull
    private List<Feed> performSearch() {
        String query = searchView.getQuery().toString();
        if (query.isEmpty()) {
            Cursor feedsCursor = PodDBAdapter.getInstance().getAllFeedsCursor();
            List<Feed> feeds = new ArrayList<>(feedsCursor.getCount());
            while (feedsCursor.moveToNext()){
                feeds.add(FeedCursorMapper.convert(feedsCursor));
            }
            feedsCursor.close();
            return feeds;
        }
        return FeedSearcher.searchFeeds(query);
    }
    
    private void showInputMethod(View view) {
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(view, 0);
        }
    }
}
