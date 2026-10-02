package dev.filetidy.core;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * 从历史里移除指定条目，其余按原顺序写回；若已无任何记录则删除历史文件。
     * <p>
     * 按出现次数移除（而非按值一次性清空），这样即使不同批次出现过完全相同的移动，
     * 也不会误删其他批次的记录。
     */
    public void removeEntries(Path directory, List<HistoryEntry> toRemove) throws IOException {
        if (toRemove.isEmpty()) {
            return;
        }
        Map<HistoryEntry, Integer> quota = new HashMap<>();
        for (HistoryEntry entry : toRemove) {
            quota.merge(entry, 1, Integer::sum);
        }
        List<HistoryEntry> remaining = new ArrayList<>();
        for (HistoryEntry entry : read(directory)) {
            Integer left = quota.get(entry);
            if (left != null && left > 0) {
                quota.put(entry, left - 1);
                continue;
            }
            remaining.add(entry);
        }
        writeAll(directory, remaining);
    }

    private void writeAll(Path directory, List<HistoryEntry> entries) throws IOException {
        Path file = historyFile(directory);
        if (entries.isEmpty()) {
            Files.deleteIfExists(file);
            return;
        }
        StringBuilder content = new StringBuilder();
        for (HistoryEntry entry : entries) {
            content.append(objectMapper.writeValueAsString(entry)).append(System.lineSeparator());
        }
        Files.writeString(file, content.toString(),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private Path historyFile(Path directory) {
        return directory.resolve(FILE_NAME);
    }
}
