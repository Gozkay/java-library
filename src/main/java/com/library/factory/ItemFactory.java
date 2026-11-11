package com.library.factory;

import com.library.model.*;

/**
 * Owner: Chioma. Gozie supplied {@link #classFor(ItemType)} because the JSON layer needs it to rebuild
 * the right subclass when loading items.json. Chioma adds the create(...) methods used by CatalogueService.
 */
public final class ItemFactory {
    private ItemFactory() { }

    public static Class<? extends Item> classFor(ItemType type) {
        return switch (type) {
            case BOOK -> Book.class;
            case MAGAZINE -> Magazine.class;
            case DVD -> DVD.class;
            case REFERENCE_BOOK -> ReferenceBook.class;
        };
    }
}
