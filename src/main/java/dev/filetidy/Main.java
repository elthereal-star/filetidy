package dev.filetidy;

import dev.filetidy.util.ConsoleOutput;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "filetidy",
        mixinStandardHelpOptions = true,
        version = "0.1.0",
        description = "按规则自动整理文件夹的命令行小工具",
        subcommands = {OrganizeCommand.class, UndoCommand.class})
public class Main implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    public static void main(String[] args) {
        ConsoleOutput.install();
        System.exit(new CommandLine(new Main()).execute(args));
    }
}
