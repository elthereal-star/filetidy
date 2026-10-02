package dev.filetidy;

import dev.filetidy.config.TidyConfig;
import dev.filetidy.core.DetectResult;
import dev.filetidy.core.MovePlan;
import dev.filetidy.core.TidyService;
import dev.filetidy.util.Ansi;
import dev.filetidy.util.PlanReport;
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

    @Option(names = {"--no-duplicates"}, description = "本次跳过重复文件检测")
    private boolean noDuplicates;

    @Option(names = {"-v", "--verbose"}, description = "列出每个文件的去向")
    private boolean verbose;

    @Option(names = {"-q", "--quiet"}, description = "只在出错时输出")
    private boolean quiet;

    @Override
    public Integer call() throws Exception {
        if (dryRun && watch) {
            throw new FiletidyException("--dry-run 与 --watch 不能同时使用：预览模式下不会真正移动文件，无需监听。");
        }
        TidyConfig tidyConfig = config != null ? TidyConfig.load(config) : TidyConfig.defaultConfig();
        TidyService tidyService = new TidyService(tidyConfig, !noDuplicates);

        DetectResult result = tidyService.plan(directory);
        if (dryRun) {
            if (!quiet) {
                printPreview(directory, result);
            }
        } else {
            int moved = result.plans().isEmpty() ? 0 : tidyService.execute(directory, result.plans());
            if (!quiet) {
                printResult(directory, result, moved);
            }
        }

        if (watch) {
            if (!quiet) {
                System.out.println("正在监听 " + directory + "（Ctrl+C 退出）");
            }
            new FolderWatcher(tidyService, directory).watchUntilInterrupted();
        }
        return Main.EXIT_OK;
    }

    private void printPreview(Path base, DetectResult result) {
        List<MovePlan> plans = result.plans();
        if (plans.isEmpty()) {
            System.out.println(Ansi.dim("没有需要整理的文件"));
            printDuplicates(base, result);
            return;
        }
        System.out.println(Ansi.bold("整理计划：共 " + plans.size() + " 个文件")
                + Ansi.dim("（预览模式，不会移动任何文件）"));
        PlanReport.summaryLines(base, plans).forEach(System.out::println);
        printDuplicates(base, result);
        System.out.println();
        plans.forEach(plan -> System.out.println("  " + PlanReport.describe(base, plan)));
    }

    private void printResult(Path base, DetectResult result, int moved) {
        List<MovePlan> plans = result.plans();
        if (moved == 0) {
            System.out.println(Ansi.dim("没有需要整理的文件"));
            printDuplicates(base, result);
            return;
        }
        String hint = verbose ? "" : Ansi.dim("（--verbose 可查看每个文件的去向）");
        System.out.println(Ansi.ok("已整理 " + moved + " 个文件") + hint);
        PlanReport.summaryLines(base, plans).forEach(System.out::println);
        printDuplicates(base, result);
        if (verbose) {
            System.out.println();
            plans.forEach(plan -> System.out.println("  " + PlanReport.describe(base, plan)));
        }
    }

    private void printDuplicates(Path base, DetectResult result) {
        if (!result.hasDuplicates()) {
            return;
        }
        System.out.println();
        System.out.println(Ansi.warn("发现 " + result.groups().size() + " 组内容重复的文件") + Ansi.dim("："));
        PlanReport.duplicateLines(base, result.groups()).forEach(System.out::println);
    }
}
