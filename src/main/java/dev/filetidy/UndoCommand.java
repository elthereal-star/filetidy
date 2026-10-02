package dev.filetidy;

import dev.filetidy.core.UndoResult;
import dev.filetidy.core.UndoService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "undo",
        mixinStandardHelpOptions = true,
        description = "撤销上一次整理（按整理记录反向移动回来）")
public class UndoCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "之前整理过的目录")
    private Path directory;

    @Override
    public Integer call() throws Exception {
        if (!Files.isDirectory(directory)) {
            throw new FiletidyException("目录不存在或不是目录: " + directory);
        }
        UndoResult result = new UndoService().undoLastRun(directory);
        if (result.nothingToDo()) {
            System.out.println("没有可撤销的记录");
        } else {
            System.out.println("已撤销 " + result.undone() + " 个移动");
        }
        if (result.skipped() > 0) {
            System.out.println("有 " + result.skipped() + " 个移动无法撤销"
                    + "（原位置已存在同名文件，或文件已被移走），记录已保留，处理后可重试 undo。");
        }
        return Main.EXIT_OK;
    }
}
