package dev.filetidy.core;

import java.nio.file.Path;
import java.util.List;

/**
 * 一组内容完全相同的文件。
 *
 * @param keeper    按保留策略选出的保留者
 * @param redundant 其余副本
 */
public record DuplicateGroup(Path keeper, List<Path> redundant) {
}
