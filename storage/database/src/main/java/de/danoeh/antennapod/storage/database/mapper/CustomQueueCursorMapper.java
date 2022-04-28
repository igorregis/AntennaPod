package de.danoeh.antennapod.storage.database.mapper;

import android.database.Cursor;

import androidx.annotation.NonNull;

import de.danoeh.antennapod.model.feed.CustomQueue;
import de.danoeh.antennapod.storage.database.PodDBAdapter;

/**
 * Converts a {@link Cursor} to a {@link CustomQueue} object.
 */
public abstract class CustomQueueCursorMapper {

    /**
     * Create a {@link CustomQueue} instance from a database row (cursor).
     */
    @NonNull
    public static CustomQueue convert(@NonNull Cursor cursor) {

        int indexId = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_ID);
        int indexFeed = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_CUSTOM_QUEUE_NAME);

        long id = cursor.getInt(indexId);
        String title = cursor.getString(indexFeed);

        return new CustomQueue(id, title);
    }
}
