
package com.library.event;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Listens for library events and appends them to data/activity.log.
 */
public class ActivityLog implements Consumer<LibraryEvent> {

    private final Path logFile;

    public ActivityLog(Path dataDirectory) {
        Objects.requireNonNull(dataDirectory, "Data directory cannot be null");
        this.logFile = dataDirectory.resolve("activity.log");
    }

    /**
     * Receives a library event and appends it to the activity log.
     */
    @Override
    public synchronized void accept(LibraryEvent event) {
        Objects.requireNonNull(event, "Event cannot be null");

        String logEntry = String.join(" | ",
                event.occurredAt().toString(),
                event.type().name(),
                String.valueOf(event.memberId()),
                String.valueOf(event.itemId()),
                String.valueOf(event.loanId()),
                event.message().replace("\n", " ").replace("\r", " ")
        );

        try {
            Files.createDirectories(logFile.getParent());

            Files.writeString(
                    logFile,
                    logEntry + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not write to the library activity log", e
            );
        }
    }
}
