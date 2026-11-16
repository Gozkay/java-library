package com.library.model;
import java.util.List;

public abstract class Item {
    private final String itemId;
    private String title;
    private String category;
    private int publicationYear;
    private boolean retired;

    protected Item(String itemId, String title, String category, int publicationYear) {
        this.itemId = itemId;
        this.title = title;
        this.category = category;
        this.publicationYear = publicationYear;
    }

    public String getItemId() { return itemId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public int getPublicationYear() { return publicationYear; }
    public void setPublicationYear(int publicationYear) { this.publicationYear = publicationYear; }
    public boolean isRetired() { return retired; }
    public void setRetired(boolean retired) { this.retired = retired; }

    public abstract String getItemType();
    public abstract int getBaseLoanDays();
    public abstract List<Copy> getCopies();
}
