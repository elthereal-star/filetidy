package dev.filetidy.util;

import dev.filetidy.core.DuplicateGroup;
import dev.filetidy.core.MovePlan;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 把整理计划渲染成给人看的文本。
 */
public final class PlanReport {

    private PlanReport() {
    }

    /**
     * 按目标文件夹分组计数，用于「已整理 N 个文件」下面的明细。
     * <p>
     * 键是相对被整理目录的路径（统一用 {@code /} 分隔，两边平台看着都顺眼），按名称排序。
     */
    public static Map<String, Integer> countByFolder(Path base, List<MovePlan> plans) {
        Map<String, Integer> counts = new TreeMap<>();
        for (MovePlan plan : plans) {
            counts.merge(folderLabel(base, plan.target().getParent()), 1, Integer::sum);
        }
        return counts;
    }

    /** 生成分组明细行，例如 {@code "  01-图片/2026-08   6 个"}。 */
    public static List<String> summaryLines(Path base, List<MovePlan> plans) {
        Map<String, Integer> counts = countByFolder(base, plans);
        int width = counts.keySet().stream().mapToInt(TextWidth::width).max().orElse(0);
        List<String> lines = new ArrayList<>(counts.size());
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            lines.add("  " + TextWidth.padRight(entry.getKey(), width) + "  " + entry.getValue() + " 个");
        }
        return lines;
    }

    /** 生成重复分组的可读描述。 */
    public static List<String> duplicateLines(Path base, List<DuplicateGroup> groups) {
        List<String> lines = new ArrayList<>();
        for (DuplicateGroup group : groups) {
            lines.add("  保留  " + relative(base, group.keeper()));
            for (Path path : group.redundant()) {
                lines.add("    重复  " + relative(base, path));
            }
        }
        return lines;
    }

    /** 把计划渲染成一行，例如 {@code "a.png  ->  01-图片/a.png  (images)"}。 */
    public static String describe(Path base, MovePlan plan) {
        return relative(base, plan.source()) + "  ->  " + relative(base, plan.target()) + "  (" + plan.ruleName() + ")";
    }

    private static String folderLabel(Path base, Path folder) {
        if (folder == null) {
            return ".";
        }
        return relative(base, folder);
    }

    private static String relative(Path base, Path path) {
        if (base == null || path == null) {
            return String.valueOf(path);
        }
        try {
            Path resolved = base.relativize(path);
            String text = resolved.toString();
            if (text.isEmpty() || text.startsWith("..")) {
                return path.toString().replace('\\', '/');
            }
            return text.replace('\\', '/');
        } catch (IllegalArgumentException e) {
            return path.toString().replace('\\', '/');
        }
    }
}
