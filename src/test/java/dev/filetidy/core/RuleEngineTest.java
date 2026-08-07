package dev.filetidy.core;

import dev.filetidy.config.TidyConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleEngineTest {

    @TempDir
    Path dir;

    @Test
    void plansMovesByExtension() throws IOException {
        Files.writeString(dir.resolve("照片.JPG"), "fake image");
        Files.writeString(dir.resolve("报告.pdf"), "fake doc");
        Files.writeString(dir.resolve("未知格式.xyz"), "??");
        Files.createDirectory(dir.resolve("已是子目录"));

        RuleEngine engine = new RuleEngine(TidyConfig.defaultConfig());
        List<MovePlan> plans = engine.plan(dir);

        assertEquals(3, plans.size());
        assertTarget(plans, "照片.JPG", "01-图片");
        assertTarget(plans, "报告.pdf", "02-文档");
        assertTarget(plans, "未知格式.xyz", "99-其他");
    }

    @Test
    void filesWithoutExtensionGoToFallback() throws IOException {
        Files.writeString(dir.resolve("README"), "no extension");

        RuleEngine engine = new RuleEngine(TidyConfig.defaultConfig());
        List<MovePlan> plans = engine.plan(dir);

        assertEquals(1, plans.size());
        assertTarget(plans, "README", "99-其他");
    }

    private void assertTarget(List<MovePlan> plans, String fileName, String folder) {
        MovePlan plan = plans.stream()
                .filter(p -> p.source().getFileName().toString().equals(fileName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少计划: " + fileName));
        assertTrue(plan.target().toString().contains(folder),
                fileName + " 应归入 " + folder + "，实际: " + plan.target());
    }
}
