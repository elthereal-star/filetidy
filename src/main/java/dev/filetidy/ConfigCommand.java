package dev.filetidy;

import dev.filetidy.config.TidyConfig;
import dev.filetidy.util.Ansi;
import dev.filetidy.util.TextWidth;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "config",
        mixinStandardHelpOptions = true,
        description = "打印生效的整理规则，便于确认 YAML 写得对不对")
public class ConfigCommand implements Callable<Integer> {

    @Option(names = {"-c", "--config"}, description = "规则配置文件（YAML），缺省展示内置规则")
    private Path config;

    @Override
    public Integer call() throws Exception {
        TidyConfig tidyConfig;
        String source;
        if (config != null) {
            tidyConfig = TidyConfig.load(config);
            source = config.toString();
        } else {
            tidyConfig = TidyConfig.defaultConfig();
            source = "内置规则 rules-default.yml";
        }
        System.out.println(Ansi.bold("生效规则") + Ansi.dim("（" + source + "）"));
        System.out.println();
        printRules(tidyConfig);
        System.out.println();
        printOptions(tidyConfig);
        return Main.EXIT_OK;
    }

    private void printRules(TidyConfig tidyConfig) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"名称", "目标目录", "日期归档", "扩展名"});
        for (TidyConfig.Rule rule : tidyConfig.getRules()) {
            rows.add(new String[]{
                    rule.getName(),
                    rule.getTarget(),
                    rule.getDatePattern() == null || rule.getDatePattern().isBlank() ? "-" : rule.getDatePattern(),
                    String.join(", ", rule.getExtensions())});
        }
        int[] widths = new int[rows.getFirst().length];
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                widths[i] = Math.max(widths[i], TextWidth.width(row[i]));
            }
        }
        for (String[] row : rows) {
            StringBuilder line = new StringBuilder("  ");
            for (int i = 0; i < row.length - 1; i++) {
                line.append(TextWidth.padRight(row[i], widths[i])).append("   ");
            }
            line.append(row[row.length - 1]);
            System.out.println(row == rows.getFirst() ? Ansi.dim(line.toString()) : line.toString());
        }
    }

    private void printOptions(TidyConfig tidyConfig) {
        System.out.println("  " + TextWidth.padRight("fallback", 12) + tidyConfig.getFallback()
                + Ansi.dim("（未命中上面任何规则时）"));
        System.out.println("  " + TextWidth.padRight("skipHidden", 12) + tidyConfig.isSkipHidden()
                + Ansi.dim(tidyConfig.isSkipHidden() ? "（点文件与系统隐藏文件会被跳过）" : "（隐藏文件也会被整理）"));
        if (tidyConfig.largeFilesEnabled()) {
            TidyConfig.LargeFiles largeFiles = tidyConfig.getLargeFiles();
            System.out.println("  " + TextWidth.padRight("largeFiles", 12)
                    + "达到 " + dev.filetidy.util.Size.format(largeFiles.thresholdBytes())
                    + " 归入 " + largeFiles.getTarget());
        } else {
            System.out.println("  " + TextWidth.padRight("largeFiles", 12) + Ansi.dim("未启用"));
        }
        if (tidyConfig.duplicatesEnabled()) {
            TidyConfig.Duplicates duplicates = tidyConfig.getDuplicates();
            System.out.println("  " + TextWidth.padRight("duplicates", 12)
                    + "action=" + duplicates.normalizedAction()
                    + ", keep=" + duplicates.normalizedKeep()
                    + ", target=" + duplicates.getTarget());
        } else {
            System.out.println("  " + TextWidth.padRight("duplicates", 12) + Ansi.dim("未启用"));
        }
    }
}
