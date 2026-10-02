package dev.filetidy.core;

import dev.filetidy.config.TidyConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 按内容哈希找出重复文件。
 * <p>
 * 比对范围 = 本次待整理的文件 + 已经归档到各规则目标目录里的文件。
 * 这样"同一个文件下载了两次"这种情况也能被发现。
 * <p>
 * <b>安全边界</b>：只会搬动「本次待整理」的文件；已经归档好的文件永远只报告、不移动，
 * 避免工具擅自改动用户早已整理好的目录结构。
 */
public final class DuplicateDetector {

    /** 重复副本被改写到重复目录时记录的规则名。 */
    public static final String RULE_NAME = "duplicates";

    private static final int BUFFER_SIZE = 64 * 1024;

    public DetectResult detect(Path directory, TidyConfig config, List<MovePlan> plans) throws IOException {
        if (!config.duplicatesEnabled()) {
            return new DetectResult(plans, List.of());
        }
        TidyConfig.Duplicates duplicates = config.getDuplicates();

        Map<Path, MovePlan> pendingByPath = new LinkedHashMap<>();
        for (MovePlan plan : plans) {
            pendingByPath.put(plan.source().toAbsolutePath().normalize(), plan);
        }

        List<Path> candidates = new ArrayList<>(pendingByPath.keySet());
        for (Path archived : archivedFiles(directory, config)) {
            Path key = archived.toAbsolutePath().normalize();
            if (!pendingByPath.containsKey(key)) {
                candidates.add(key);
            }
        }

        List<DuplicateGroup> groups = new ArrayList<>();
        Map<Path, Path> redirectTargets = new LinkedHashMap<>();

        for (List<Path> sameSize : groupBySize(candidates)) {
            if (sameSize.size() < 2) {
                continue;
            }
            for (List<Path> sameContent : groupByContent(sameSize)) {
                if (sameContent.size() < 2) {
                    continue;
                }
                Path keeper = pickKeeper(sameContent, duplicates.keepsOldest());
                List<Path> redundant = sameContent.stream().filter(path -> !path.equals(keeper)).toList();
                groups.add(new DuplicateGroup(keeper, redundant));
                if (duplicates.movesRedundantCopies()) {
                    for (Path path : redundant) {
                        MovePlan plan = pendingByPath.get(path);
                        if (plan == null) {
                            // 已归档的文件不动，只报告
                            continue;
                        }
                        redirectTargets.put(path,
                                directory.resolve(duplicates.getTarget()).resolve(plan.source().getFileName()));
                    }
                }
            }
        }

        // 按原顺序重建计划，被判定为重复副本的改写目标目录
        List<MovePlan> adjusted = new ArrayList<>(plans.size());
        for (MovePlan plan : plans) {
            Path key = plan.source().toAbsolutePath().normalize();
            Path redirect = redirectTargets.get(key);
            adjusted.add(redirect == null ? plan : new MovePlan(plan.source(), redirect, RULE_NAME));
        }
        return new DetectResult(adjusted, groups);
    }

    /** 按文件大小预分组——只有大小相同的文件才值得去算哈希。 */
    private List<List<Path>> groupBySize(List<Path> candidates) {
        Map<Long, List<Path>> bySize = new LinkedHashMap<>();
        for (Path file : candidates) {
            long size;
            try {
                size = Files.size(file);
            } catch (IOException e) {
                continue;
            }
            if (size <= 0) {
                // 空文件彼此都"相同"，算重复没有意义
                continue;
            }
            bySize.computeIfAbsent(size, key -> new ArrayList<>()).add(file);
        }
        return new ArrayList<>(bySize.values());
    }

    private List<List<Path>> groupByContent(List<Path> sameSize) {
        Map<String, List<Path>> byHash = new LinkedHashMap<>();
        for (Path file : sameSize) {
            String hash;
            try {
                hash = sha256(file);
            } catch (IOException e) {
                continue;
            }
            byHash.computeIfAbsent(hash, key -> new ArrayList<>()).add(file);
        }
        return new ArrayList<>(byHash.values());
    }

    /**
     * 收集已经归档的文件：各规则 target、fallback、大文件目录下的全部文件（含日期子目录）。
     * 重复目录本身要排除，否则历次挪进去的副本会被反复当成新发现。
     */
    private List<Path> archivedFiles(Path directory, TidyConfig config) throws IOException {
        Set<String> targets = new LinkedHashSet<>();
        for (TidyConfig.Rule rule : config.getRules()) {
            targets.add(rule.getTarget());
        }
        targets.add(config.getFallback());
        if (config.largeFilesEnabled()) {
            targets.add(config.getLargeFiles().getTarget());
        }
        if (config.duplicatesEnabled() && config.getDuplicates().getTarget() != null) {
            targets.remove(config.getDuplicates().getTarget());
        }

        List<Path> files = new ArrayList<>();
        for (String target : targets) {
            Path dir = directory.resolve(target);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.filter(Files::isRegularFile)
                        .filter(path -> !HistoryStore.FILE_NAME.equals(path.getFileName().toString()))
                        .forEach(files::add);
            }
        }
        return files;
    }

    /** 按保留策略挑出保留者；时间相同时用路径排序兜底，保证结果稳定。 */
    private Path pickKeeper(List<Path> group, boolean keepOldest) {
        return group.stream()
                .min(Comparator
                        .comparingLong((Path path) -> keepOldest ? lastModified(path) : -lastModified(path))
                        .thenComparing(Path::toString))
                .orElseThrow();
    }

    private long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    static String sha256(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream in = Files.newInputStream(file)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte b : digest.digest()) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }
}
