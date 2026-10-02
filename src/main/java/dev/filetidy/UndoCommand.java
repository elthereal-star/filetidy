package dev.filetidy;

import dev.filetidy.core.RunSummary;
import dev.filetidy.core.UndoResult;
import dev.filetidy.core.UndoService;
import dev.filetidy.util.Ansi;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "undo",
        mixinStandardHelpOptions = true,
        description = "撤销上一次整理（按整理记录反向移动回来）")
public class UndoCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "之前整理过的目录")
    private Path directory;

    @Option(names = {"--list"}, description = "只列出历史整理批次，不做撤销")
    private boolean list;

    @Option(names = {"--dry-run"}, description = "预览将要撤销的内容，不实际移动文件")
    private boolean dryRun;

    @Override
    public Integer call() throws Exception {
        if (!Files.isDirectory(directory)) {
            throw new FiletidyException("目录不存在或不是目录: " + directory);
        }
        UndoService undoService = new UndoService();
        if (list) {
            printRuns(undoService.listRuns(directory));
            return Main.EXIT_OK;
        }

        UndoResult result = undoService.undoLastRun(directory, dryRun);
        if (dryRun) {
            printPreview(result);
            return Main.EXIT_OK;
        }
        if (result.nothingToDo()) {
            System.out.println(Ansi.dim("没有可撤销的记录"));
        } else {
            System.out.println(Ansi.ok("已撤销 " + result.undone() + " 个移动"));
        }
        if (result.skipped() > 0) {
            System.out.println(Ansi.warn("有 " + result.skipped() + " 个移动无法撤销")
                    + "（原位置已存在同名文件，或文件已被移走），记录已保留，处理后可重试 undo。");
        }
        return Main.EXIT_OK;
    }

    private void printRuns(List<RunSummary> runs) {
        if (runs.isEmpty()) {
            System.out.println(Ansi.dim("没有历史整理记录"));
            return;
        }
        System.out.println(Ansi.bold("历史整理批次") + Ansi.dim("（共 " + runs.size() + " 批，最早的排在前面）"));
        System.out.println();
        for (int i = 0; i < runs.size(); i++) {
            RunSummary run = runs.get(i);
            String marker = i == runs.size() - 1 ? Ansi.dim("  ← 最近一次，可被 undo 撤销") : "";
            System.out.printf("  #%-3d %s   %2d 个移动%s%n", i + 1, run.label(), run.moves(), marker);
        }
    }

    private void printPreview(UndoResult result) {
        if (result.nothingToDo()) {
            System.out.println(Ansi.dim("没有可撤销的记录"));
            return;
        }
        System.out.println(Ansi.bold("将撤销最近一次整理：") + result.undone() + " 个移动"
                + Ansi.dim("（预览模式，未做任何改动）"));
        if (result.skipped() > 0) {
            System.out.println(Ansi.warn("有 " + result.skipped() + " 个移动无法撤销")
                    + "（原位置已存在同名文件，或文件已被移走）。");
        }
    }
}
