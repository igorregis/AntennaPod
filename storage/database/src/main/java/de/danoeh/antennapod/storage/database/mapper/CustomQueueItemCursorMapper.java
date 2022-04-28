package de.danoeh.antennapod.storage.database.mapper;

import android.database.Cursor;

import androidx.annotation.NonNull;

import de.danoeh.antennapod.model.feed.CustomQueueItem;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.storage.database.PodDBAdapter;

/**
 * Converts a {@link Cursor} to a {@link CustomQueueItem} object.
 */
public abstract class CustomQueueItemCursorMapper {

    /**
     * Create a {@link CustomQueueItem} instance from a database row (cursor).
     */
    @NonNull
    public static CustomQueueItem convert(@NonNull Cursor cursor) {

        int indexId = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_ID);
        int indexFeed = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_FEED);
        int indexPosition = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_POSITION);
        int indexAmount = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_AMOUNT);
        int indexOrder = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_SORT_ORDER);

        long id = cursor.getInt(indexId);
        long feed = cursor.getInt(indexFeed);
        long position = cursor.getInt(indexPosition);
        long amount = cursor.getInt(indexAmount);
        SortOrder order = SortOrder.fromCodeString(cursor.getString(indexOrder));

        return new CustomQueueItem(id, feed, position, amount, order);
    }
}
