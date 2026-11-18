package com.library.model;

public class Magazine extends Item {
    private int issueNumber;
    private String month;

    protected Magazine() { } // Gson

    public Magazine(String itemId, String title, String category, int year, int copies, int issueNumber, String month) {
        super(itemId, ItemType.MAGAZINE, title, category, year, copies);
        this.issueNumber = issueNumber;
        this.month = month;
    }

    @Override public int getBaseLoanPeriodDays() { return 7; }
    public int getIssueNumber() { return issueNumber; }
    public String getMonth() { return month; }
}
