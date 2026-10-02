package dev.filetidy.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 执行移动计划，并把每次移动写入历史记录供 undo 使用。
 */
public class FileMover {

    private final HistoryStore historyStore = new HistoryStore();

    /**
     * 按计划移动文件，同时把移动记录追加进 {@code directory} 下的历史文件。
     *
     * @param directory 被整理的目录，历史记录写在这里
     * @param plans     计划列表
     * @return 实际移动的文件数量
     */
    public int execute(Path directory, List<MovePlan> plans) throws IOException {
        if (plans == null || plans.isEmpty()) {
            return 0;
        }
        String runId = String.valueOf(System.currentTimeMillis());
        int moved = 0;
        for (MovePlan plan : plans) {
            Files.createDirectories(plan.target().getParent());
            Path resolvedTarget = resolveConflict(plan.target());
            Files.move(plan.source(), resolvedTarget);
            historyStore.append(directory, new HistoryEntry(runId, plan.source().toString(), resolvedTarget.toString()));
            moved++;
        }
        return moved;
    }

    /**
     * 目标已存在时在文件名后追加 " (n)"，保证不覆盖用户文件。
     */
    static Path resolveConflict(Path target) {
        if (!Files.exists(target)) {
            return target;
        }
        String fileName = target.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String ext = dot > 0 ? fileName.substring(dot) : "";
        int n = 1;
        Path candidate;
        do {
            candidate = target.resolveSibling(base + " (" + n + ")" + ext);
            n++;
        } while (Files.exists(candidate));
        return candidate;
    }
}
