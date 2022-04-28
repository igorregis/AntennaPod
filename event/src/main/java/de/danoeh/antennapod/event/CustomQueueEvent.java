package de.danoeh.antennapod.event;

import androidx.annotation.Nullable;

import java.util.List;

import de.danoeh.antennapod.model.feed.CustomQueueItem;

public class CustomQueueEvent {

    public enum Action {
        ADDED, ADDED_ITEMS, SET_QUEUE, REMOVED, IRREVERSIBLE_REMOVED, CLEARED, DELETED_MEDIA, SORTED, MOVED
    }

    public final Action action;
    public final CustomQueueItem item;
    public final int position;
    public final List<CustomQueueItem> items;


    private CustomQueueEvent(Action action,
                             @Nullable CustomQueueItem item,
                             @Nullable List<CustomQueueItem> items,
                             int position) {
        this.action = action;
        this.item = item;
        this.items = items;
        this.position = position;
    }

    public static CustomQueueEvent added(CustomQueueItem item, int position) {
        return new CustomQueueEvent(Action.ADDED, item, null, position);
    }

    public static CustomQueueEvent setQueue(List<CustomQueueItem> queue) {
        return new CustomQueueEvent(Action.SET_QUEUE, null, queue, -1);
    }

    public static CustomQueueEvent removed(CustomQueueItem item) {
        return new CustomQueueEvent(Action.REMOVED, item, null, -1);
    }

    public static CustomQueueEvent irreversibleRemoved(CustomQueueItem item) {
        return new CustomQueueEvent(Action.IRREVERSIBLE_REMOVED, item, null, -1);
    }

    public static CustomQueueEvent cleared() {
        return new CustomQueueEvent(Action.CLEARED, null, null, -1);
    }

    public static CustomQueueEvent sorted(List<CustomQueueItem> sortedQueue) {
        return new CustomQueueEvent(Action.SORTED, null, sortedQueue, -1);
    }

    public static CustomQueueEvent moved(CustomQueueItem item, int newPosition) {
        return new CustomQueueEvent(Action.MOVED, item, null, newPosition);
    }
}
