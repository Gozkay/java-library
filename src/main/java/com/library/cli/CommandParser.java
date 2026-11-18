package com.library.cli;

import java.util.ArrayList;
import java.util.List;

/**
 * Tokenizes interactive CLI lines, preserving quoted multi-word arguments
 * (e.g., member add "Ada Lovelace" PREMIUM secret123).
 */
public final class CommandParser {
    private CommandParser() { }

    public record ParsedCommand(String command, List<String> args) {
        public String subcommand() {
            return args.isEmpty() ? "" : args.get(0).toLowerCase();
        }

        public String arg(int index) {
            return index >= 0 && index < args.size() ? args.get(index) : "";
        }

        public List<String> remainingArgs(int startIndex) {
            if (startIndex >= args.size()) {
                return List.of();
            }
            return args.subList(startIndex, args.size());
        }
    }

    public static ParsedCommand parse(String line) {
        if (line == null || line.isBlank()) {
            return new ParsedCommand("", List.of());
        }
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (inQuotes) {
            throw new IllegalArgumentException("Unclosed quote in command: " + line);
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        if (tokens.isEmpty()) {
            return new ParsedCommand("", List.of());
        }
        String cmd = tokens.get(0).toLowerCase();
        List<String> args = tokens.size() > 1 ? List.copyOf(tokens.subList(1, tokens.size())) : List.of();
        return new ParsedCommand(cmd, args);
    }
}
