
package com.library.factory;

import com.library.model.*;

public class ItemFactory {

    public Item createItem(
            String type,
            String id,
            String title,
            String category,
            int year,
            int copies,
            String isbn,
            String author,
            String publisher,
            int pages,
            int issueNumber,
            String month,
            String director,
            int runtimeMinutes
    ) {
        if (type == null) {
            throw new IllegalArgumentException("Item type is required.");
        }

        switch (type.trim().toLowerCase()) {
            case "book":
                return new Book(id, title, category, year, copies,
                        isbn, author, publisher, pages);

            case "magazine":
                return new Magazine(id, title, category, year, copies,
                        issueNumber, month);

            case "dvd":
                return new DVD(id, title, category, year, copies,
                        director, runtimeMinutes);

            case "referencebook":
            case "reference book":
                return new ReferenceBook(id, title, category, year, copies,
                        isbn, author, publisher, pages);

            default:
                throw new IllegalArgumentException(
                        "Unknown item type: " + type);
        }
    }

    public static Class<? extends Item> classFor(ItemType type) {
        if (type == null) {
            throw new IllegalArgumentException("Item type is required.");
        }

        switch (type) {
            case BOOK:
                return Book.class;

            case MAGAZINE:
                return Magazine.class;

            case DVD:
                return DVD.class;

            case REFERENCE_BOOK:
                return ReferenceBook.class;

            default:
                throw new IllegalArgumentException(
                        "Unsupported item type: " + type);
        }
    }
}
