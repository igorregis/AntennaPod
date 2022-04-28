package de.danoeh.antennapod.model.feed;

import androidx.annotation.NonNull;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * A custom queue profile, for now all we need is the name .
 */
public class CustomQueue implements Serializable {

    private long id;

    private String name;

    private transient List<CustomQueueItem> customQueueItems;

    public long getId() {
        return id;
    }

    /**
     * This constructor will be used by DBReader.
     * */
    public CustomQueue(long id, String name) {
        this.id = id;
        this.name = name;
    }

    public CustomQueue() {
        customQueueItems = new ArrayList<>();
    }

    public void setId(long id) {
        this.id = id;
    }
    public void updateFromOther(CustomQueue other) {
        if (other.name != null) {
            this.name = other.name;
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


    public List<CustomQueueItem> getCustomQueueItems() {
        return customQueueItems;
    }

    public void setCustomQueueItems(List<CustomQueueItem> customQueueItems) {
        this.customQueueItems = customQueueItems;
    }

    @NonNull
    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }
}
