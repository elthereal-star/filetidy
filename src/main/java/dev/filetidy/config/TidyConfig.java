package dev.filetidy.config;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 整理规则配置，与 YAML 结构一一对应（SnakeYAML 按 JavaBean  setter 绑定）。
 */
public class TidyConfig {

    private List<Rule> rules = new ArrayList<>();
    /** 未命中任何规则时归入的文件夹名 */
    private String fallback = "其他";
    private boolean skipHidden = true;

    public static TidyConfig load(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return new Yaml().loadAs(in, TidyConfig.class);
        }
    }

    public static TidyConfig defaultConfig() {
        try (InputStream in = TidyConfig.class.getResourceAsStream("/rules-default.yml")) {
            if (in == null) {
                throw new IllegalStateException("内置规则文件 rules-default.yml 缺失");
            }
            return new Yaml().loadAs(in, TidyConfig.class);
        } catch (IOException e) {
            throw new IllegalStateException("读取内置规则失败", e);
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
