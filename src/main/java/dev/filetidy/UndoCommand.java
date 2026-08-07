package dev.filetidy;

import dev.filetidy.core.UndoService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "undo", description = "撤销上一次整理（按整理记录反向移动回来）")
public class UndoCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "之前整理过的目录")
    private Path directory;

    @Override
    public Integer call() throws Exception {
        int undone = new UndoService().undoLastRun(directory);
        System.out.println(undone > 0 ? "已撤销 " + undone + " 个移动" : "没有可撤销的记录");
        return 0;
    }
}
