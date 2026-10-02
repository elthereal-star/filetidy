package dev.filetidy.config;

import dev.filetidy.FiletidyException;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 整理规则配置，与 YAML 结构一一对应（SnakeYAML 按 JavaBean setter 绑定）。
 */
public class TidyConfig {

    private List<Rule> rules = new ArrayList<>();
    /** 未命中任何规则时归入的文件夹名 */
    private String fallback = "其他";
    private boolean skipHidden = true;

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
     * 校验配置完整性。缺字段时提前报错，避免整理到一半才发现规则是坏的。
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
        }
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

    public static class Rule {
        private String name;
        private String target;
        private List<String> extensions = new ArrayList<>();

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
    }
}
