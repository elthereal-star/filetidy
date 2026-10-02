package dev.filetidy.core;

import dev.filetidy.FiletidyException;
import dev.filetidy.config.TidyConfig;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 生成整理计划：只扫描目录第一层（不递归），避免把已归档到子文件夹的文件再次移动。
 * <p>
 * 判定优先级：大文件分流 → 扩展名规则 → fallback。
 */
public class RuleEngine {

    /** 命中大文件分流时记录在计划里的规则名。 */
    public static final String LARGE_FILES_RULE = "large-files";
    /** 未命中任何扩展名规则时的规则名。 */
    public static final String FALLBACK_RULE = "fallback";

    private final TidyConfig config;
    private final Map<String, DateTimeFormatter> dateFormatters = new HashMap<>();

    public RuleEngine(TidyConfig config) {
        this.config = config;
    }

    public List<MovePlan> plan(Path directory) throws IOException {
        if (directory == null || !Files.isDirectory(directory)) {
            throw new FiletidyException("目录不存在或不是目录: " + directory);
        }
        List<MovePlan> plans = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path file : stream) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                if (HistoryStore.FILE_NAME.equals(file.getFileName().toString())) {
                    continue;
                }
                if (config.isSkipHidden() && isHidden(file)) {
                    continue;
                }
                plans.add(planFor(directory, file));
            }
        }
        return plans;
    }

    private MovePlan planFor(Path directory, Path file) throws IOException {
        if (config.largeFilesEnabled() && Files.size(file) >= config.getLargeFiles().thresholdBytes()) {
            TidyConfig.LargeFiles largeFiles = config.getLargeFiles();
            Path target = directory.resolve(largeFiles.getTarget()).resolve(file.getFileName());
            return new MovePlan(file, target, LARGE_FILES_RULE);
        }
        TidyConfig.Rule rule = matchRule(file);
        if (rule == null) {
            Path target = directory.resolve(config.getFallback()).resolve(file.getFileName());
            return new MovePlan(file, target, FALLBACK_RULE);
        }
        Path targetDir = directory.resolve(rule.getTarget());
        String datePattern = rule.getDatePattern();
        if (datePattern != null && !datePattern.isBlank()) {
            targetDir = targetDir.resolve(formatDate(file, datePattern.trim()));
        }
        return new MovePlan(file, targetDir.resolve(file.getFileName()), rule.getName());
    }

    /** 按扩展名匹配规则，未命中返回 {@code null}。 */
    TidyConfig.Rule matchRule(Path file) {
        String extension = extensionOf(file);
        for (TidyConfig.Rule rule : config.getRules()) {
            if (rule.getExtensions().stream().anyMatch(ext -> ext.equalsIgnoreCase(extension))) {
                return rule;
            }
        }
        return null;
    }

    /** 用文件最后修改时间按 pattern 生成日期子目录名。 */
    private String formatDate(Path file, String pattern) throws IOException {
        DateTimeFormatter formatter = dateFormatters.computeIfAbsent(pattern, DateTimeFormatter::ofPattern);
        ZonedDateTime modified = Files.getLastModifiedTime(file).toInstant().atZone(ZoneId.systemDefault());
        return formatter.format(modified);
    }

    /**
     * 判断是否为隐藏文件。
     * <p>
     * Unix 上点文件天然隐藏，而 Windows 的 {@link Files#isHidden} 只看 DOS 隐藏属性，
     * 若只依赖它，同一份配置在两个平台上行为不一致。这里统一为「点文件或系统隐藏属性」。
     */
    static boolean isHidden(Path file) throws IOException {
        return file.getFileName().toString().startsWith(".") || Files.isHidden(file);
    }

    static String extensionOf(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
