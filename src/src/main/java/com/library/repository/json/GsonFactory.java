package com.library.repository.json;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.library.factory.ItemFactory;
import com.library.model.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The one place where Gson is configured:
 * dates are ISO-8601 strings ("2026-10-29"), money is a BigDecimal written as a STRING ("200.00"),
 * and Item / User subclasses are rebuilt through the factory using the stored "type" / "role" field.
 */
public final class GsonFactory {
    private GsonFactory() { }

    public static Gson create() {
        return new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(LocalDate.class, new LocalDateAdapter().nullSafe())
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter().nullSafe())
                .registerTypeAdapter(BigDecimal.class, new BigDecimalAdapter().nullSafe())
                .registerTypeAdapterFactory(new PolymorphicAdapterFactory<>(Item.class, GsonFactory::resolveItemClass))
                .registerTypeAdapterFactory(new PolymorphicAdapterFactory<>(User.class, GsonFactory::resolveUserClass))
                .create();
    }

    private static Class<? extends Item> resolveItemClass(JsonObject json) {
        JsonElement t = json.get("type");
        if (t == null || !t.isJsonPrimitive()) throw new JsonParseException("item is missing its \"type\" field");
        try {
            return ItemFactory.classFor(ItemType.valueOf(t.getAsString()));
        } catch (IllegalArgumentException e) {
            throw new JsonParseException("unknown item type \"" + t.getAsString() + "\"");
        }
    }

    private static Class<? extends User> resolveUserClass(JsonObject json) {
        JsonElement r = json.get("role");
        if (r == null || !r.isJsonPrimitive()) throw new JsonParseException("user is missing its \"role\" field");
        try {
            return Role.valueOf(r.getAsString()) == Role.MEMBER ? Member.class : User.class;
        } catch (IllegalArgumentException e) {
            throw new JsonParseException("unknown role \"" + r.getAsString() + "\"");
        }
    }

    private static final class LocalDateAdapter extends com.google.gson.TypeAdapter<LocalDate> {
        @Override public void write(JsonWriter out, LocalDate v) throws IOException { out.value(v.toString()); }
        @Override public LocalDate read(JsonReader in) throws IOException { return LocalDate.parse(in.nextString()); }
    }

    private static final class LocalDateTimeAdapter extends com.google.gson.TypeAdapter<LocalDateTime> {
        @Override public void write(JsonWriter out, LocalDateTime v) throws IOException { out.value(v.toString()); }
        @Override public LocalDateTime read(JsonReader in) throws IOException { return LocalDateTime.parse(in.nextString()); }
    }

    /** Written as a string so no precision is ever lost to a JSON number; reading also accepts a plain number. */
    private static final class BigDecimalAdapter extends com.google.gson.TypeAdapter<BigDecimal> {
        @Override public void write(JsonWriter out, BigDecimal v) throws IOException { out.value(v.toPlainString()); }
        @Override public BigDecimal read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) { in.nextNull(); return null; }
            return new BigDecimal(in.nextString());
        }
    }
}
