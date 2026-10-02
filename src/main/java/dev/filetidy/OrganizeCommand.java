package dev.filetidy;

import dev.filetidy.config.TidyConfig;
import dev.filetidy.core.FileMover;
import dev.filetidy.core.MovePlan;
import dev.filetidy.core.RuleEngine;
import dev.filetidy.watch.FolderWatcher;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "organize",
        mixinStandardHelpOptions = true,
        description = "按规则整理目录中的文件")
public class OrganizeCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "要整理的目录")
    private Path directory;

    @Option(names = {"-c", "--config"}, description = "规则配置文件（YAML），缺省使用内置规则")
    private Path config;

    @Option(names = {"--dry-run"}, description = "只打印整理计划，不实际移动文件")
    private boolean dryRun;

    @Option(names = {"--watch"}, description = "持续监听目录，新文件落盘自动整理")
    private boolean watch;

    @Override
    public Integer call() throws Exception {
        if (dryRun && watch) {
            throw new FiletidyException("--dry-run 与 --watch 不能同时使用：预览模式下不会真正移动文件，无需监听。");
        }
        TidyConfig tidyConfig = config != null ? TidyConfig.load(config) : TidyConfig.defaultConfig();
        RuleEngine engine = new RuleEngine(tidyConfig);
        FileMover mover = new FileMover();

        List<MovePlan> plans = engine.plan(directory);
        if (plans.isEmpty()) {
            System.out.println("没有需要整理的文件");
        } else if (dryRun) {
            plans.forEach(plan -> System.out.println("[dry-run] " + plan));
            System.out.println("共 " + plans.size() + " 个文件待整理（预览模式，未做任何改动）");
        } else {
            mover.execute(plans);
            System.out.println("已整理 " + plans.size() + " 个文件");
        }

        if (watch) {
            new FolderWatcher(engine, mover).watch(directory);
        }
        return Main.EXIT_OK;
    }
}
