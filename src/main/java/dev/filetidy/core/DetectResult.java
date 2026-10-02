package dev.filetidy.core;

import java.util.List;

/**
 * 重复检测之后的结果：可能被改写过的整理计划 + 发现的重组。
 */
public record DetectResult(List<MovePlan> plans, List<DuplicateGroup> groups) {

    public boolean hasDuplicates() {
        return !groups.isEmpty();
    }
}
