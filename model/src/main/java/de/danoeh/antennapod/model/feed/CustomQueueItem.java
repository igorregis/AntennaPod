package de.danoeh.antennapod.model.feed;

import androidx.annotation.NonNull;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.Serializable;

/**
 * Configuration Item of a custom queue profile.
 */
public class CustomQueueItem implements Serializable {

    private long id;

    private long feed;
    private transient Feed feedObject;

    private long position;

    private long amount;

    private SortOrder order;

    public long getId() {
        return id;
    }

    /**
     * This constructor will be used by DBReader.
     * */
    public CustomQueueItem(long id, long feedId, long position, long amount, SortOrder order) {
        this.id = id;
        this.feed = feedId;
        this.position = position;
        this.amount = amount;
        this.order = order;
    }

    /**
     * This constructor is suposed to be used to create new instances.
     * */
    public CustomQueueItem(long feedId, long position) {
        this.feed = feedId;
        this.position = position;
        this.amount = 1;
        this.order = SortOrder.DATE_NEW_OLD;
    }

    public void setId(long id) {
        this.id = id;
    }
    public void updateFromOther(CustomQueueItem other) {
        if (other.feed > 0) {
            this.feed = other.feed;
        }
    }

    public Feed getFeedObject() {
        return feedObject;
    }

    public void setFeedObject(Feed feedObject) {
        this.feedObject = feedObject;
    }

    public long getFeed() {
        return feed;
    }

    public void setFeed(long feed) {
        this.feed = feed;
    }

    public long getPosition() {
        return position;
    }

    public void setPosition(long position) {
        this.position = position;
    }

    public SortOrder getOrder() {
        return order;
    }

    public void setOrder(SortOrder order) {
        this.order = order;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    @NonNull
    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }
}
