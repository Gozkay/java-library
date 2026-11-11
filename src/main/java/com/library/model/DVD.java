package com.library.model;

public class DVD extends Item {
    private String director;
    private int runtimeMinutes;

    protected DVD() { } // Gson

    public DVD(String itemId, String title, String category, int year, int copies, String director, int runtimeMinutes) {
        super(itemId, ItemType.DVD, title, category, year, copies);
        this.director = director;
        this.runtimeMinutes = runtimeMinutes;
    }

    @Override public int getBaseLoanPeriodDays() { return 5; }
    @Override public String getAuthor() { return director; }
    public String getDirector() { return director; }
    public int getRuntimeMinutes() { return runtimeMinutes; }
}
