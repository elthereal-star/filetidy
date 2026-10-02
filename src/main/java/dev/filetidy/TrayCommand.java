package dev.filetidy;

import dev.filetidy.config.TidyConfig;
import dev.filetidy.core.TidyService;
import dev.filetidy.tray.TrayApp;
import dev.filetidy.tray.TraySupport;
import dev.filetidy.util.Ansi;
import dev.filetidy.watch.FolderWatcher;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "tray",
        mixinStandardHelpOptions = true,
        description = "常驻系统托盘，后台自动整理新文件")
public class TrayCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "要监听的目录")
    private Path directory;

    @Option(names = {"-c", "--config"}, description = "规则配置文件（YAML），缺省使用内置规则")
    private Path config;

    @Option(names = {"--no-duplicates"}, description = "跳过重复文件检测")
    private boolean noDuplicates;

    @Option(names = {"--no-fallback"},
            description = "托盘不可用时直接报错，不退化成为前台监听")
    private boolean noFallback;

    @Override
    public Integer call() throws Exception {
        if (!Files.isDirectory(directory)) {
            throw new FiletidyException("目录不存在或不是目录: " + directory);
        }
        TidyConfig tidyConfig = config != null ? TidyConfig.load(config) : TidyConfig.defaultConfig();
        TidyService tidyService = new TidyService(tidyConfig, !noDuplicates);

        if (!TraySupport.isAvailable()) {
            if (noFallback) {
                throw new FiletidyException("当前环境不支持系统托盘（没有图形界面或无头环境）。"
                        + "可以改用 organize --watch 在终端里监听。");
            }
            // 开机自启场景下，直接退出比悄悄死掉更有害，所以退化成前台监听继续干活
            System.out.println(Ansi.warn("当前环境不支持系统托盘，已退化为前台监听模式（Ctrl+C 退出）。"));
            new FolderWatcher(tidyService, directory).watchUntilInterrupted();
            return Main.EXIT_OK;
        }

        System.out.println(Ansi.ok("filetidy 已常驻系统托盘") + "，正在监听 " + directory);
        System.out.println(Ansi.dim("在托盘图标上右键可以「立即整理 / 暂停监听 / 打开目录 / 退出」。"));
        new TrayApp(tidyService, directory).start();
        return Main.EXIT_OK;
    }
}
