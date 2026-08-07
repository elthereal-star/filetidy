package dev.filetidy.core;

/**
 * 一条移动历史。runId 标识同一次整理运行，undo 时按 runId 整体回滚。
 */
public record HistoryEntry(String runId, String from, String to) {
}
