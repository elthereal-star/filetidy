package dev.filetidy.core;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 历史记录以 JSON Lines 形式存放在被整理目录下的 .filetidy-history.jsonl。
 */
public class HistoryStore {

    public static final String FILE_NAME = ".filetidy-history.jsonl";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void append(Path directory, HistoryEntry entry) throws IOException {
        String line = objectMapper.writeValueAsString(entry) + System.lineSeparator();
        Files.writeString(historyFile(directory), line,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public List<HistoryEntry> read(Path directory) throws IOException {
        Path file = historyFile(directory);
        List<HistoryEntry> entries = new ArrayList<>();
        if (!Files.exists(file)) {
            return entries;
        }
        for (String line : Files.readAllLines(file)) {
            if (!line.isBlank()) {
                entries.add(objectMapper.readValue(line, HistoryEntry.class));
            }
        }
        return entries;
    }

    public void removeRun(Path directory, String runId) throws IOException {
        Path file = historyFile(directory);
        List<HistoryEntry> remaining = read(directory).stream()
                .filter(entry -> !entry.runId().equals(runId))
                .toList();
        if (remaining.isEmpty()) {
            Files.deleteIfExists(file);
            return;
        }
        StringBuilder content = new StringBuilder();
        for (HistoryEntry entry : remaining) {
            content.append(objectMapper.writeValueAsString(entry)).append(System.lineSeparator());
        }
        Files.writeString(file, content.toString(), StandardOpenOption.TRUNCATE_EXISTING);
    }

    private Path historyFile(Path directory) {
        return directory.resolve(FILE_NAME);
    }
}
