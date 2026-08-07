package dev.filetidy.core;

import java.nio.file.Path;

/**
 * 一次计划中的文件移动：从 source 到 target，ruleName 记录命中的规则便于展示。
 */
public record MovePlan(Path source, Path target, String ruleName) {

    @Override
    public String toString() {
        return source.getFileName() + "  ->  " + target + "  (" + ruleName + ")";
    }
}
