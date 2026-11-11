package com.library.repository.json;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.function.Function;

/**
 * Rebuilds the correct SUBCLASS when loading (Book vs Magazine..., Member vs plain User) by asking a resolver
 * (backed by ItemFactory) which class a JSON object represents. Writing just uses the runtime class, so subclass
 * fields are always saved. Registered for the exact base class only, which avoids Gson's infinite-recursion trap
 * with hierarchy adapters.
 */
final class PolymorphicAdapterFactory<B> implements TypeAdapterFactory {
    private final Class<B> base;
    private final Function<JsonObject, Class<? extends B>> resolver;

    PolymorphicAdapterFactory(Class<B> base, Function<JsonObject, Class<? extends B>> resolver) {
        this.base = base;
        this.resolver = resolver;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        if (type.getRawType() != base) return null;
        TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
        TypeAdapter<JsonElement> tree = gson.getAdapter(JsonElement.class);

        return new TypeAdapter<T>() {
            @Override
            public void write(JsonWriter out, T value) throws IOException {
                Class<?> runtime = value.getClass();
                TypeAdapter<T> adapter = runtime == base ? delegate : (TypeAdapter<T>) gson.getAdapter(runtime);
                adapter.write(out, value);
            }

            @Override
            public T read(JsonReader in) throws IOException {
                JsonElement el = tree.read(in);
                if (!el.isJsonObject()) throw new JsonParseException("Expected a JSON object for " + base.getSimpleName());
                Class<? extends B> target = resolver.apply(el.getAsJsonObject());
                TypeAdapter<T> adapter = target == base ? delegate : (TypeAdapter<T>) gson.getAdapter(target);
                return adapter.fromJsonTree(el);
            }
        }.nullSafe();
    }
}
