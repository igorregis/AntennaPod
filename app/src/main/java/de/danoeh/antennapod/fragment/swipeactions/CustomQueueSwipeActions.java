package de.danoeh.antennapod.fragment.swipeactions;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.danoeh.antennapod.view.viewholder.CustomQueueItemViewHolder;

public class CustomQueueSwipeActions extends ItemTouchHelper.SimpleCallback implements LifecycleObserver {
    public static final String PREF_NAME = "SwipeActionsPrefs";
    public static final String KEY_PREFIX_SWIPEACTIONS = "PrefSwipeActions";

    public static final List<SwipeAction> swipeActions = Collections.unmodifiableList(
            Arrays.asList(new AddToQueueSwipeAction(), new RemoveFromInboxSwipeAction(),
                    new StartDownloadSwipeAction(), new MarkFavoriteSwipeAction(),
                    new RemoveFromQueueSwipeAction())
    );

    private final Fragment fragment;
    private final String tag;

    SwipeActions.Actions actions;
    boolean swipeOutEnabled = true;
    int swipedOutTo = 0;
    private final ItemTouchHelper itemTouchHelper = new ItemTouchHelper(this);

    public CustomQueueSwipeActions(int dragDirs, Fragment fragment, String tag) {
        super(dragDirs, ItemTouchHelper.RIGHT | ItemTouchHelper.LEFT);
        this.fragment = fragment;
        this.tag = tag;
        reloadPreference();
        fragment.getLifecycle().addObserver(this);
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    public void reloadPreference() {
        actions = getPrefs(fragment.requireContext(), tag);
    }

    public CustomQueueSwipeActions attachTo(RecyclerView recyclerView) {
        itemTouchHelper.attachToRecyclerView(recyclerView);
        return this;
    }

    public void detach() {
        itemTouchHelper.attachToRecyclerView(null);
    }

    private static SwipeActions.Actions getPrefs(Context context, String tag, String defaultActions) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String prefsString = prefs.getString(KEY_PREFIX_SWIPEACTIONS + tag, defaultActions);

        return new SwipeActions.Actions(prefsString);
    }

    private static SwipeActions.Actions getPrefs(Context context, String tag) {
        return getPrefs(context, tag, "");
    }

    public static boolean isSwipeActionEnabled(Context context, String tag) {
        return false;
    }

    private boolean isSwipeActionEnabled() {
        return isSwipeActionEnabled(fragment.requireContext(), tag);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView,
                          @NonNull RecyclerView.ViewHolder viewHolder,
                          @NonNull RecyclerView.ViewHolder target) {
        return false;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int swipeDir) {
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                            @NonNull RecyclerView.ViewHolder viewHolder,
                            float dx, float dy, int actionState, boolean isCurrentlyActive) {
        super.onChildDraw(c, recyclerView, viewHolder, dx, dy, actionState, isCurrentlyActive);
    }

    @Override
    public float getSwipeEscapeVelocity(float defaultValue) {
        return swipeOutEnabled ? defaultValue * 1.5f : Float.MAX_VALUE;
    }

    @Override
    public float getSwipeVelocityThreshold(float defaultValue) {
        return swipeOutEnabled ? defaultValue * 0.6f : 0;
    }

    @Override
    public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
        return swipeOutEnabled ? 0.6f : 1.0f;
    }

    @Override
    public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        super.clearView(recyclerView, viewHolder);

        if (swipedOutTo != 0) {
            onSwiped(viewHolder, swipedOutTo);
            swipedOutTo = 0;
        }
    }

    @Override
    public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        if (!isSwipeActionEnabled()) {
            return makeMovementFlags(getDragDirs(recyclerView, viewHolder), 0);
        } else {
            return super.getMovementFlags(recyclerView, viewHolder);
        }
    }

    public void startDrag(CustomQueueItemViewHolder holder) {
        itemTouchHelper.startDrag(holder);
    }
}
