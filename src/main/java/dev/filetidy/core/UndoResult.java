package dev.filetidy.core;

/**
 * 一次 undo 的结果统计。
 *
 * @param undone  成功回滚的移动数量
 * @param skipped 因原位置被占用或文件已不在而无法回滚的数量（记录会被保留以便重试）
 */
public record UndoResult(int undone, int skipped) {

    public static final UndoResult EMPTY = new UndoResult(0, 0);

    /** 历史里没有任何可撤销的记录。 */
    public boolean nothingToDo() {
        return undone == 0 && skipped == 0;
    }
}
