package de.danoeh.antennapod.view.viewholder;

import android.os.Build;
import android.text.Layout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.core.feed.util.PlaybackSpeedUtils;
import de.danoeh.antennapod.core.storage.DBReader;
import de.danoeh.antennapod.core.util.Converter;
import de.danoeh.antennapod.model.feed.CustomQueue;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

/**
 * Holds the view which shows CustomQueue List.
 */
public class CustomQueueViewHolder extends RecyclerView.ViewHolder {
    private static final String TAG = "CustomQueueViewHolder";

    public final ImageView dragHandle;
    private final TextView title;
    private final MainActivity activity;
    private final TextView infoBar;
    private CustomQueue item;

    public CustomQueueViewHolder(MainActivity activity, ViewGroup parent) {
        super(LayoutInflater.from(activity).inflate(R.layout.queue_list_item, parent, false));
        this.activity = activity;
        dragHandle = itemView.findViewById(R.id.drag_handle);
        title = itemView.findViewById(R.id.queue_title);
        infoBar = itemView.findViewById(R.id.queue_tooltip_text);
        if (Build.VERSION.SDK_INT >= 23) {
            title.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_FULL);
        }
        itemView.setTag(this);
    }

    public void bind(CustomQueue item) {
        this.item = item;
        title.setText(item.getName());

        Observable.fromCallable(() -> DBReader.getQueue(item.getId()))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(items -> refreshInfoBar(items)
                , error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    private void refreshInfoBar(List<FeedItem> queue) {
        String info = String.format(Locale.getDefault(), "%d%s",
                queue.size(), activity.getString(R.string.episodes_suffix));
        if (queue.size() > 0) {
            long timeLeft = 0;
            for (FeedItem item : queue) {
                float playbackSpeed = 1;
                if (UserPreferences.timeRespectsSpeed()) {
                    playbackSpeed = PlaybackSpeedUtils.getCurrentPlaybackSpeed(item.getMedia());
                }
                if (item.getMedia() != null) {
                    long itemTimeLeft = item.getMedia().getDuration() - item.getMedia().getPosition();
                    timeLeft += itemTimeLeft / playbackSpeed;
                }
            }
            info += " • ";
            info += activity.getString(R.string.time_left_label);
            info += Converter.getDurationStringLocalized(activity, timeLeft);
        }
        infoBar.setText(info);
    }
    public CustomQueue getItem() {
        return item;
    }
}
