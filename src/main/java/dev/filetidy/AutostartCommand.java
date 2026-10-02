package dev.filetidy;

import dev.filetidy.autostart.AutostartManager;
import dev.filetidy.autostart.LaunchCommand;
import dev.filetidy.util.Ansi;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Command(name = "autostart",
        mixinStandardHelpOptions = true,
        description = "管理开机自启（Windows 启动文件夹 / macOS LaunchAgent / Linux autostart）")
public class AutostartCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "要随开机启动监听的目录")
    private Path directory;

    @Option(names = {"-c", "--config"}, description = "规则配置文件（YAML），缺省使用内置规则")
    private Path config;

    @Option(names = {"-e", "--enable"}, description = "启用开机自启（缺省只查看状态）")
    private boolean enable;

    @Option(names = {"-d", "--disable"}, description = "取消开机自启")
    private boolean disable;

    @Override
    public Integer call() throws Exception {
        if (!Files.isDirectory(directory)) {
            throw new FiletidyException("目录不存在或不是目录: " + directory);
        }
        if (enable && disable) {
            throw new FiletidyException("--enable 与 --disable 不能同时使用。");
        }
        Path target = LaunchCommand.canonical(directory);
        List<String> command = LaunchCommand.current(target, LaunchCommand.canonical(config));
        AutostartManager manager = AutostartManager.forCurrentUser(command);

        if (enable) {
            Path entry = manager.enable();
            System.out.println(Ansi.ok("已启用开机自启") + "（下次登录后生效）");
            System.out.println();
            printDetails(manager, entry);
            System.out.println();
            System.out.println("取消方式: " + Ansi.bold(
                    "filetidy autostart \"" + target + "\" --disable"));
            return Main.EXIT_OK;
        }
        if (disable) {
            boolean removed = manager.disable();
            if (removed) {
                System.out.println(Ansi.ok("已取消开机自启"));
            } else {
                System.out.println(Ansi.dim("本来就没有启用开机自启"));
            }
            return Main.EXIT_OK;
        }

        if (manager.isEnabled()) {
            System.out.println(Ansi.bold("开机自启：") + Ansi.ok("已启用"));
            System.out.println();
            printDetails(manager, manager.entryFile());
        } else {
            System.out.println(Ansi.bold("开机自启：") + Ansi.warn("未启用"));
            System.out.println();
            System.out.println("启用方式: " + Ansi.bold(
                    "filetidy autostart \"" + target + "\" --enable"));
        }
        return Main.EXIT_OK;
    }

    private void printDetails(AutostartManager manager, Path entry) throws Exception {
        System.out.println("  平台  " + manager.platform().name().toLowerCase());
        System.out.println("  位置  " + entry);
        if (manager.isEnabled()) {
            System.out.println("  启动  " + manager.command().stream()
                    .map(arg -> arg.contains(" ") ? "\"" + arg + "\"" : arg)
                    .collect(Collectors.joining(" ")));
        }
    }
}
