package dev.filetidy.core;

import dev.filetidy.FiletidyException;
import dev.filetidy.config.TidyConfig;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 只扫描目录第一层（不递归），避免把已归档到子文件夹的文件再次移动。
 */
public class RuleEngine {

    private final TidyConfig config;

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
                if (config.isSkipHidden() && Files.isHidden(file)) {
                    continue;
                }
                plans.add(new MovePlan(file, directory.resolve(targetFolder(file)).resolve(file.getFileName()), ruleName(file)));
            }
        }
        return plans;
    }

    private String ruleName(Path file) {
        String extension = extensionOf(file);
        for (TidyConfig.Rule rule : config.getRules()) {
            if (rule.getExtensions().stream().anyMatch(ext -> ext.equalsIgnoreCase(extension))) {
                return rule.getName();
            }
        }
        return "fallback";
    }

    private String targetFolder(Path file) {
        String name = ruleName(file);
        for (TidyConfig.Rule rule : config.getRules()) {
            if (rule.getName().equals(name)) {
                return rule.getTarget();
            }
        }
        return config.getFallback();
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
