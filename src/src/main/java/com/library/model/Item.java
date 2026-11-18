package com.library.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Abstract catalogue item. The JSON "type" field (see {@link ItemType}) lets
 * {@code ItemFactory} rebuild the right subclass when loading.
 * DRAFT of the shared contract: Chioma owns this class and may extend it.
 */
public abstract class Item {
    private String itemId;
    private ItemType type;
    private String title;
    private String category;
    private int publicationYear;
    private boolean retired;
    private List<Copy> copies = new ArrayList<>();

    protected Item() { } // Gson

    protected Item(String itemId, ItemType type, String title, String category, int publicationYear, int initialCopies) {
        this.itemId = itemId;
        this.type = type;
        this.title = title;
        this.category = category;
        this.publicationYear = publicationYear;
        addCopies(initialCopies);
    }

    /** Base loan period in days. 0 means "not borrowable" (reference items). Belongs to the TYPE, not to a service. */
    public abstract int getBaseLoanPeriodDays();

    /** Author/creator used by search; null when the type has none. */
    public String getAuthor() { return null; }

    public boolean isBorrowable() { return !retired && getBaseLoanPeriodDays() > 0; }

    public final Copy addCopy() {
        Copy c = new Copy(itemId + "-C" + (copies.size() + 1));
        copies.add(c);
        return c;
    }

    public final void addCopies(int count) {
        if (count < 0) throw new IllegalArgumentException("count must be >= 0");
        for (int i = 0; i < count; i++) addCopy();
    }

    public Optional<Copy> findCopy(String copyId) {
        return copies.stream().filter(c -> c.getCopyId().equals(copyId)).findFirst();
    }

    public Optional<Copy> findAvailableCopy() {
        return copies.stream().filter(Copy::isAvailable).findFirst();
    }

    public long countAvailable() { return copies.stream().filter(Copy::isAvailable).count(); }

    /** Copies that still exist for lending (not LOST / RETIRED); used for "2 of 3 copies available". */
    public long countActiveCopies() {
        return copies.stream().filter(c -> c.getStatus() != CopyStatus.LOST && c.getStatus() != CopyStatus.RETIRED).count();
    }

    public String getItemId() { return itemId; }
    public ItemType getType() { return type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public int getPublicationYear() { return publicationYear; }
    public void setPublicationYear(int year) { this.publicationYear = year; }
    public boolean isRetired() { return retired; }
    public List<Copy> getCopies() { return Collections.unmodifiableList(copies); }

    /** Retires the item and every copy that is not currently on loan. */
    public void retire() {
        retired = true;
        for (Copy c : copies) {
            if (c.getStatus() != CopyStatus.ON_LOAN && c.getStatus() != CopyStatus.RETIRED) c.moveTo(CopyStatus.RETIRED);
        }
    }
}
