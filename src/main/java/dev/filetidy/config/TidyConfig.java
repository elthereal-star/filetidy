package dev.filetidy.config;

import dev.filetidy.FiletidyException;
import dev.filetidy.util.Size;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 整理规则配置，与 YAML 结构一一对应（SnakeYAML 按 JavaBean setter 绑定）。
 */
public class TidyConfig {

    private List<Rule> rules = new ArrayList<>();
    /** 未命中任何规则时归入的文件夹名 */
    private String fallback = "其他";
    private boolean skipHidden = true;
    /** 可选：超过阈值的大文件单独归置 */
    private LargeFiles largeFiles;
    /** 可选：内容重复的文件如何处理 */
    private Duplicates duplicates;

    /**
     * 从 YAML 文件读取配置。文件不存在、语法错误或字段缺失都会抛出
     * {@link FiletidyException}，携带可直接展示给用户的原因。
     */
    public static TidyConfig load(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new FiletidyException("规则配置文件不存在或不是文件: " + path);
        }
        TidyConfig config;
        try (InputStream in = Files.newInputStream(path)) {
            config = new Yaml().loadAs(in, TidyConfig.class);
        } catch (YAMLException e) {
            throw new FiletidyException("规则配置文件解析失败: " + path + "（" + e.getMessage() + "）", e);
        }
        if (config == null) {
            throw new FiletidyException("规则配置文件是空的: " + path);
        }
        config.validate("规则配置文件 " + path);
        return config;
    }

    public static TidyConfig defaultConfig() {
        try (InputStream in = TidyConfig.class.getResourceAsStream("/rules-default.yml")) {
            if (in == null) {
                throw new FiletidyException("内置规则文件 rules-default.yml 缺失");
            }
            TidyConfig config = new Yaml().loadAs(in, TidyConfig.class);
            if (config == null) {
                throw new FiletidyException("内置规则文件 rules-default.yml 为空");
            }
            config.validate("内置规则 rules-default.yml");
            return config;
        } catch (IOException e) {
            throw new FiletidyException("读取内置规则失败", e);
        }
    }

    /**
     * 校验配置完整性。缺字段或写法非法时提前报错，避免整理到一半才发现规则是坏的。
     */
    public void validate(String source) {
        if (rules == null || rules.isEmpty()) {
            throw new FiletidyException(source + " 里没有配置任何 rules，至少需要一条规则。");
        }
        if (fallback == null || fallback.isBlank()) {
            throw new FiletidyException(source + " 的 fallback 不能为空。");
        }
        for (int i = 0; i < rules.size(); i++) {
            Rule rule = rules.get(i);
            String where = source + " 的第 " + (i + 1) + " 条规则";
            if (rule == null) {
                throw new FiletidyException(where + "是空的。");
            }
            if (rule.getName() == null || rule.getName().isBlank()) {
                throw new FiletidyException(where + "缺少 name。");
            }
            if (rule.getTarget() == null || rule.getTarget().isBlank()) {
                throw new FiletidyException(where + "（" + rule.getName() + "）缺少 target。");
            }
            if (rule.getExtensions() == null || rule.getExtensions().isEmpty()) {
                throw new FiletidyException(where + "（" + rule.getName() + "）的 extensions 不能为空。");
            }
            validateDatePattern(rule.getDatePattern(), where + "（" + rule.getName() + "）");
        }
        validateLargeFiles(source);
        validateDuplicates(source);
    }

    private void validateDatePattern(String pattern, String where) {
        if (pattern == null || pattern.isBlank()) {
            return;
        }
        String trimmed = pattern.trim();
        if (trimmed.contains("..") || trimmed.startsWith("/") || trimmed.startsWith("\\")) {
            throw new FiletidyException(where + " 的 datePattern 不能包含 .. 或以路径分隔符开头: " + pattern);
        }
        try {
            DateTimeFormatter.ofPattern(trimmed);
        } catch (IllegalArgumentException e) {
            throw new FiletidyException(where + " 的 datePattern 不是合法的日期格式: " + pattern
                    + "（例如 yyyy-MM 或 yyyy/MM）", e);
        }
    }

    private void validateLargeFiles(String source) {
        if (largeFiles == null) {
            return;
        }
        if (largeFiles.getTarget() == null || largeFiles.getTarget().isBlank()) {
            throw new FiletidyException(source + " 的 largeFiles 缺少 target。");
        }
        try {
            if (largeFiles.thresholdBytes() <= 0) {
                throw new FiletidyException(source + " 的 largeFiles.threshold 必须大于 0。");
            }
        } catch (FiletidyException e) {
            throw new FiletidyException(source + " 的 largeFiles.threshold 无效：" + e.getMessage(), e);
        }
    }

    private void validateDuplicates(String source) {
        if (duplicates == null) {
            return;
        }
        String action = duplicates.normalizedAction();
        if (!List.of(Duplicates.ACTION_REPORT, Duplicates.ACTION_MOVE).contains(action)) {
            throw new FiletidyException(source + " 的 duplicates.action 只能是 "
                    + Duplicates.ACTION_REPORT + " 或 " + Duplicates.ACTION_MOVE + "，实际为: " + duplicates.getAction());
        }
        String keep = duplicates.normalizedKeep();
        if (!List.of(Duplicates.KEEP_OLDEST, Duplicates.KEEP_NEWEST).contains(keep)) {
            throw new FiletidyException(source + " 的 duplicates.keep 只能是 "
                    + Duplicates.KEEP_OLDEST + " 或 " + Duplicates.KEEP_NEWEST + "，实际为: " + duplicates.getKeep());
        }
        if (Duplicates.ACTION_MOVE.equals(action)
                && (duplicates.getTarget() == null || duplicates.getTarget().isBlank())) {
            throw new FiletidyException(source + " 的 duplicates.action 为 move 时必须配置 target。");
        }
    }

    /** 是否启用重复文件检测。 */
    public boolean duplicatesEnabled() {
        return duplicates != null;
    }

    /** 是否启用大文件分流。 */
    public boolean largeFilesEnabled() {
        return largeFiles != null && largeFiles.thresholdBytes() > 0;
    }

    public List<Rule> getRules() {
        return rules;
    }

    public void setRules(List<Rule> rules) {
        this.rules = rules;
    }

    public String getFallback() {
        return fallback;
    }

    public void setFallback(String fallback) {
        this.fallback = fallback;
    }

    public boolean isSkipHidden() {
        return skipHidden;
    }

    public void setSkipHidden(boolean skipHidden) {
        this.skipHidden = skipHidden;
    }

    public LargeFiles getLargeFiles() {
        return largeFiles;
    }

    public void setLargeFiles(LargeFiles largeFiles) {
        this.largeFiles = largeFiles;
    }

    public Duplicates getDuplicates() {
        return duplicates;
    }

    public void setDuplicates(Duplicates duplicates) {
        this.duplicates = duplicates;
    }

    /** 一条归类规则。 */
    public static class Rule {
        private String name;
        private String target;
        private List<String> extensions = new ArrayList<>();
        /** 可选：命中后追加一级日期子目录，如 yyyy-MM */
        private String datePattern;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getTarget() {
            return target;
        }

        public void setTarget(String target) {
            this.target = target;
        }

        public List<String> getExtensions() {
            return extensions;
        }

        public void setExtensions(List<String> extensions) {
            this.extensions = extensions;
        }

        public String getDatePattern() {
            return datePattern;
        }

        public void setDatePattern(String datePattern) {
            this.datePattern = datePattern;
        }
    }

    /** 大文件分流：超过阈值的文件不按扩展名归类，直接进 target。 */
    public static class LargeFiles {
        private String threshold = "100MB";
        private String target = "90-大文件";

        public String getThreshold() {
            return threshold;
        }

        public void setThreshold(String threshold) {
            this.threshold = threshold;
        }

        public String getTarget() {
            return target;
        }

        public void setTarget(String target) {
            this.target = target;
        }

        public long thresholdBytes() {
            return Size.parse(threshold);
        }
    }

    /** 重复文件处理策略。 */
    public static class Duplicates {

        public static final String ACTION_REPORT = "report";
        public static final String ACTION_MOVE = "move";
        public static final String KEEP_OLDEST = "oldest";
        public static final String KEEP_NEWEST = "newest";

        private String action = ACTION_REPORT;
        private String target = "98-重复文件";
        private String keep = KEEP_OLDEST;

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getTarget() {
            return target;
        }

        public void setTarget(String target) {
            this.target = target;
        }

        public String getKeep() {
            return keep;
        }

        public void setKeep(String keep) {
            this.keep = keep;
        }

        /** 归一化后的动作，缺省 report。 */
        public String normalizedAction() {
            return action == null ? ACTION_REPORT : action.trim().toLowerCase(Locale.ROOT);
        }

        /** 归一化后的保留策略，缺省 oldest。 */
        public String normalizedKeep() {
            return keep == null ? KEEP_OLDEST : keep.trim().toLowerCase(Locale.ROOT);
        }

        public boolean movesRedundantCopies() {
            return ACTION_MOVE.equals(normalizedAction());
        }

        public boolean keepsOldest() {
            return KEEP_OLDEST.equals(normalizedKeep());
        }
    }
}
