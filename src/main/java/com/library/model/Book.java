package com.library.model;

public class Book extends Item {
    private String isbn;
    private String author;
    private String publisher;
    private int pages;

    protected Book() { } // Gson

    public Book(String itemId, String title, String category, int year, int copies,
                String isbn, String author, String publisher, int pages) {
        this(itemId, ItemType.BOOK, title, category, year, copies, isbn, author, publisher, pages);
    }

    protected Book(String itemId, ItemType type, String title, String category, int year, int copies,
                   String isbn, String author, String publisher, int pages) {
        super(itemId, type, title, category, year, copies);
        this.isbn = isbn;
        this.author = author;
        this.publisher = publisher;
        this.pages = pages;
    }

    @Override public int getBaseLoanPeriodDays() { return 21; }
    @Override public String getAuthor() { return author; }
    public String getIsbn() { return isbn; }
    public String getPublisher() { return publisher; }
    public int getPages() { return pages; }
}
