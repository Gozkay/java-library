package com.library.model;

/** In-library use only: loan period 0, so borrowing must throw ItemNotBorrowableException. */
public class ReferenceBook extends Book {
    protected ReferenceBook() { } // Gson

    public ReferenceBook(String itemId, String title, String category, int year, int copies,
                         String isbn, String author, String publisher, int pages) {
        super(itemId, ItemType.REFERENCE_BOOK, title, category, year, copies, isbn, author, publisher, pages);
    }

    @Override public int getBaseLoanPeriodDays() { return 0; }
}
