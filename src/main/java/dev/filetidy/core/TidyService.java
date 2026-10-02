package dev.filetidy.core;

import dev.filetidy.config.TidyConfig;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * 一次整理的完整流程：生成计划（含可选的重复检测）→ 执行移动。
 * <p>
 * 命令行、watch 模式与系统托盘共用同一套流程，避免三处各写一遍。
 */
public class TidyService {

    private final TidyConfig config;
    private final RuleEngine ruleEngine;
    private final DuplicateDetector duplicateDetector = new DuplicateDetector();
    private final FileMover fileMover = new FileMover();
    private final boolean detectDuplicates;

    public TidyService(TidyConfig config, boolean detectDuplicates) {
        this.config = config;
        this.detectDuplicates = detectDuplicates;
        this.ruleEngine = new RuleEngine(config);
    }

    public TidyConfig config() {
        return config;
    }

    /** 生成整理计划；重复检测关闭时直接返回规则引擎的结果。 */
    public DetectResult plan(Path directory) throws IOException {
        List<MovePlan> plans = ruleEngine.plan(directory);
        if (!detectDuplicates || !config.duplicatesEnabled()) {
            return new DetectResult(plans, List.of());
        }
        return duplicateDetector.detect(directory, config, plans);
    }

    /**
     * 立即整理一次。
     *
     * @return 实际移动的文件数量
     */
    public int organize(Path directory) throws IOException {
        DetectResult result = plan(directory);
        return execute(directory, result.plans());
    }

    /**
     * 执行已经生成好的计划。
     * <p>
     * 拆出来是为了让调用方能先把计划展示给用户（dry-run 预览、重复提示），
     * 确认无误后再真正动手。
     *
     * @return 实际移动的文件数量
     */
    public int execute(Path directory, List<MovePlan> plans) throws IOException {
        return fileMover.execute(directory, plans);
    }
}
