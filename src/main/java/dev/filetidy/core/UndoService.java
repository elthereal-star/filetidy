package dev.filetidy.core;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UndoService {

    private final HistoryStore historyStore = new HistoryStore();

    /**
     * 回滚最近一次整理运行。逆序移动，先移回后放进去的文件，避免命名冲突。
     * <p>
     * 回滚成功的记录会从历史中移除；失败的（原位置已被占用、文件已不在）
     * 原样保留，方便用户处理掉冲突后重试。
     */
    public UndoResult undoLastRun(Path directory) throws IOException {
        return undoLastRun(directory, false);
    }

    /**
     * @param dryRun 为 true 时只统计会发生什么，不移动文件也不改历史
     */
    public UndoResult undoLastRun(Path directory, boolean dryRun) throws IOException {
        List<HistoryEntry> entries = historyStore.read(directory);
        if (entries.isEmpty()) {
            return UndoResult.EMPTY;
        }
        String lastRunId = entries.getLast().runId();
        List<HistoryEntry> toUndo = new ArrayList<>(entries.stream()
                .filter(entry -> entry.runId().equals(lastRunId))
                .toList());
        toUndo = toUndo.reversed();

        Path base = directory.toAbsolutePath().normalize();
        List<HistoryEntry> undone = new ArrayList<>();
        int skipped = 0;
        for (HistoryEntry entry : toUndo) {
            Path current = Path.of(entry.to());
            Path original = Path.of(entry.from());
            if (!Files.exists(current) || Files.exists(original)) {
                skipped++;
                continue;
            }
            undone.add(entry);
            if (dryRun) {
                continue;
            }
            Path parent = original.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.move(current, original);
            pruneEmptiedFolders(current.getParent(), base);
        }
        if (!dryRun) {
            historyStore.removeEntries(directory, undone);
        }
        return new UndoResult(undone.size(), skipped);
    }

    /** 列出历史批次，按发生顺序排列（最早的在前面）。 */
    public List<RunSummary> listRuns(Path directory) throws IOException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (HistoryEntry entry : historyStore.read(directory)) {
            counts.merge(entry.runId(), 1, Integer::sum);
        }
        List<RunSummary> runs = new ArrayList<>(counts.size());
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            runs.add(new RunSummary(entry.getKey(), parseTime(entry.getKey()), entry.getValue()));
        }
        return runs;
    }

    private Instant parseTime(String runId) {
        try {
            return Instant.ofEpochMilli(Long.parseLong(runId));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 删除被搬空的目标文件夹，避免 undo 之后留下一堆空目录。
     * <p>
     * 只删空目录，且不会越过被整理目录本身（那里还放着历史文件）。
     */
    private void pruneEmptiedFolders(Path start, Path base) throws IOException {
        Path current = start == null ? null : start.toAbsolutePath().normalize();
        while (current != null && !current.equals(base) && current.startsWith(base) && isEmptyDirectory(current)) {
            Files.delete(current);
            current = current.getParent();
        }
    }

    private boolean isEmptyDirectory(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            return !stream.iterator().hasNext();
        }
    }
}
