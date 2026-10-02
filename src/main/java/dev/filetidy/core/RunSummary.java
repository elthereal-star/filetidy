package dev.filetidy.core;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 一次整理运行的摘要，用于 {@code undo --list}。
 *
 * @param runId 批次标识（历史上是发起整理时的毫秒时间戳）
 * @param time  解析出来的时间，无法解析时为 {@code null}
 * @param moves 该批次移动的文件数
 */
public record RunSummary(String runId, Instant time, int moves) {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /** 人类可读的时间；老数据或异常标识回退成原始 runId。 */
    public String label() {
        return time == null ? runId : FORMATTER.format(time);
    }
}
