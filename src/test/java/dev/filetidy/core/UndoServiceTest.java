package dev.filetidy.core;

import dev.filetidy.config.TidyConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UndoServiceTest {

    @TempDir
    Path dir;

    private void organize() throws IOException {
        RuleEngine engine = new RuleEngine(TidyConfig.defaultConfig());
        new FileMover().execute(dir, engine.plan(dir));
    }

    @Test
    void undoDeletesFoldersEmptiedByTheRollback() throws IOException {
        Files.writeString(dir.resolve("a.png"), "1");

        organize();
        assertTrue(Files.isDirectory(dir.resolve("01-图片")));

        new UndoService().undoLastRun(dir);

        assertFalse(Files.exists(dir.resolve("01-图片")), "搬空后的目标文件夹应被清理");
        assertTrue(Files.exists(dir.resolve("a.png")));
    }

    @Test
    void undoKeepsFoldersThatStillHaveContent() throws IOException {
        Path pictures = dir.resolve("01-图片");
        Files.createDirectories(pictures);
        Files.writeString(pictures.resolve("keep.txt"), "keep");
        Files.writeString(dir.resolve("a.png"), "1");

        organize();
        new UndoService().undoLastRun(dir);

        assertTrue(Files.exists(pictures.resolve("keep.txt")), "非空文件夹不能被删掉");
        assertTrue(Files.exists(dir.resolve("a.png")));
    }

    @Test
    void failedRollbackKeepsHistoryForRetry() throws IOException {
        Files.writeString(dir.resolve("a.png"), "1");
        organize();

        // 在原始位置放一个同名文件，挡住回滚
        Files.writeString(dir.resolve("a.png"), "blocker");
        UndoResult first = new UndoService().undoLastRun(dir);

        assertEquals(0, first.undone());
        assertEquals(1, first.skipped());
        assertTrue(Files.exists(dir.resolve(HistoryStore.FILE_NAME)), "失败的记录必须保留");

        // 清掉冲突后重试
        Files.delete(dir.resolve("a.png"));
        UndoResult second = new UndoService().undoLastRun(dir);

        assertEquals(1, second.undone());
        assertEquals(0, second.skipped());
        assertTrue(Files.exists(dir.resolve("a.png")));
        assertFalse(Files.exists(dir.resolve(HistoryStore.FILE_NAME)), "全部回滚后历史文件应删除");
    }

    @Test
    void undoOnlyRollsBackTheMostRecentRun() throws IOException {
        Files.writeString(dir.resolve("a.png"), "1");
        organize();
        Files.writeString(dir.resolve("b.pdf"), "2");
        organize();

        UndoResult result = new UndoService().undoLastRun(dir);

        assertEquals(1, result.undone());
        assertTrue(Files.exists(dir.resolve("b.pdf")), "上上次整理的 b.pdf 应回到原位");
        assertTrue(Files.exists(dir.resolve("01-图片").resolve("a.png")), "上一次整理的 a.png 应保持不动");

        new UndoService().undoLastRun(dir);
        assertTrue(Files.exists(dir.resolve("a.png")), "再 undo 一次才轮到更早的批次");
    }

    @Test
    void undoOnEmptyDirectoryReportsNothingToDo() throws IOException {
        UndoResult result = new UndoService().undoLastRun(dir);

        assertTrue(result.nothingToDo());
    }
}
