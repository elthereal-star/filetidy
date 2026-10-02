package dev.filetidy.core;

import dev.filetidy.config.TidyConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileMoverUndoTest {

    @TempDir
    Path dir;

    @Test
    void executeThenUndoRestoresEverything() throws IOException {
        Files.writeString(dir.resolve("a.png"), "1");
        Files.writeString(dir.resolve("b.zip"), "2");

        RuleEngine engine = new RuleEngine(TidyConfig.defaultConfig());
        FileMover mover = new FileMover();

        List<MovePlan> plans = engine.plan(dir);
        mover.execute(dir, plans);

        assertFalse(Files.exists(dir.resolve("a.png")));
        assertTrue(Files.exists(dir.resolve("01-图片").resolve("a.png")));
        assertTrue(Files.exists(dir.resolve("03-压缩包").resolve("b.zip")));

        UndoResult result = new UndoService().undoLastRun(dir);

        assertEquals(2, result.undone());
        assertEquals(0, result.skipped());
        assertTrue(Files.exists(dir.resolve("a.png")));
        assertTrue(Files.exists(dir.resolve("b.zip")));
    }

    @Test
    void neverOverwritesExistingTarget() throws IOException {
        Path imagesDir = dir.resolve("01-图片");
        Files.createDirectories(imagesDir);
        Files.writeString(imagesDir.resolve("a.png"), "old");
        Files.writeString(dir.resolve("a.png"), "new");

        RuleEngine engine = new RuleEngine(TidyConfig.defaultConfig());
        new FileMover().execute(dir, engine.plan(dir));

        assertEquals("old", Files.readString(imagesDir.resolve("a.png")));
        assertEquals("new", Files.readString(imagesDir.resolve("a (1).png")));
    }
}
