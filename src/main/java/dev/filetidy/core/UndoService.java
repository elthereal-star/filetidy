package dev.filetidy.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class UndoService {

    private final HistoryStore historyStore = new HistoryStore();

    /**
     * 回滚最近一次整理运行。逆序移动，先移回后放进去的文件，避免命名冲突。
     */
    public int undoLastRun(Path directory) throws IOException {
        List<HistoryEntry> entries = historyStore.read(directory);
        if (entries.isEmpty()) {
            return 0;
        }
        String lastRunId = entries.getLast().runId();
        List<HistoryEntry> toUndo = entries.stream()
                .filter(entry -> entry.runId().equals(lastRunId))
                .toList()
                .reversed();

        int undone = 0;
        for (HistoryEntry entry : toUndo) {
            Path current = Path.of(entry.to());
            Path original = Path.of(entry.from());
            if (Files.exists(current) && !Files.exists(original)) {
                Files.move(current, original);
                undone++;
            }
        }
        historyStore.removeRun(directory, lastRunId);
        return undone;
    }
}
