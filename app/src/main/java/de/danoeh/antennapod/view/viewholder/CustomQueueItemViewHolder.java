package de.danoeh.antennapod.view.viewholder;

import android.os.Build;
import android.text.Layout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.adapter.CoverLoader;
import de.danoeh.antennapod.model.feed.CustomQueueItem;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.ui.common.CircularProgressBar;

/**
 * Holds the view which shows FeedItems.
 */
public class CustomQueueItemViewHolder extends RecyclerView.ViewHolder {
    private static final String TAG = "CustomQueueConfigItemViewHolder";

    private final View container;
    public final ImageView dragHandle;
    private final ImageView placeholder;
    private final ImageView cover;
    private final TextView title;
    private final Spinner order;
    private final Spinner amount;
    private final CircularProgressBar secondaryActionProgress;
    private final TextView separatorIcons;
    private final View leftPadding;
    public final CardView coverHolder;
    public final CheckBox selectCheckBox;

    private final MainActivity activity;
    public final ImageView deleteIcon;
    private CustomQueueItem item;

    public CustomQueueItemViewHolder(MainActivity activity, ViewGroup parent) {
        super(LayoutInflater.from(activity).inflate(R.layout.custom_queue_config_item_list_item, parent, false));
        this.activity = activity;
        container = itemView.findViewById(R.id.container);
        dragHandle = itemView.findViewById(R.id.drag_handle);
        placeholder = itemView.findViewById(R.id.imgPlaceholder);
        cover = itemView.findViewById(R.id.imgvCover);
        title = itemView.findViewById(R.id.feed_title);
        amount = itemView.findViewById(R.id.amount);
        order = itemView.findViewById(R.id.order);
        deleteIcon = itemView.findViewById(R.id.action_delete);
        if (Build.VERSION.SDK_INT >= 23) {
            title.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_FULL);
        }
        separatorIcons = itemView.findViewById(R.id.separatorIcons);
        secondaryActionProgress = itemView.findViewById(R.id.secondaryActionProgress);
        coverHolder = itemView.findViewById(R.id.coverHolder);
        leftPadding = itemView.findViewById(R.id.left_padding);
        itemView.setTag(this);
        selectCheckBox = itemView.findViewById(R.id.selectCheckBox);

        ArrayAdapter<String> amountAdapter = new ArrayAdapter<>(container.getContext(), android.R.layout.simple_spinner_item);
        amountAdapter.setDropDownViewResource( android.R.layout.simple_spinner_dropdown_item );
        amountAdapter.addAll(new String[]{"1 episódio", "2 episódios", "3 episódios"});
        amount.setAdapter(amountAdapter);

        ArrayAdapter<String> orderAdapter = new ArrayAdapter<>(container.getContext(), android.R.layout.simple_spinner_item);
        orderAdapter.setDropDownViewResource( android.R.layout.simple_spinner_dropdown_item );
        orderAdapter.addAll(new String[]{"Mais novo", "Mais antigo"});
        order.setAdapter(orderAdapter);

    }

    public boolean hasSelectedFeed() {
        return item.getFeedObject() != null;
    }

    public void bind(CustomQueueItem item) {
        this.item = item;
        if (item.getFeedObject() == null) {
            placeholder.setVisibility(View.VISIBLE);
            cover.setVisibility(View.GONE);
            title.setText(R.string.click_to_select_feed);
        } else {
            placeholder.setVisibility(View.GONE);
            cover.setVisibility(View.VISIBLE);
            title.setText(item.getFeedObject().getTitle());
            leftPadding.setContentDescription(item.getFeedObject().getTitle());
        }
        amount.setSelection((int) item.getAmount() - 1);

        amount.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                item.setAmount(i + 1);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        order.setSelection(item.getOrder() == SortOrder.DATE_NEW_OLD ? 0 : 1);
        order.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                item.setOrder(i == 0 ? SortOrder.DATE_NEW_OLD : SortOrder.DATE_OLD_NEW);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        if (cover.getVisibility() == View.VISIBLE) {
            new CoverLoader(activity)
                    .withUri(item.getFeedObject().getImageUrl())
                    .withCoverView(cover)
                    .load();
        }
    }

    public CustomQueueItem getItem() {
        return item;
    }
}
