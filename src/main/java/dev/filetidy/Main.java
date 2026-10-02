package dev.filetidy;

import dev.filetidy.util.Ansi;
import dev.filetidy.util.ConsoleOutput;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.io.PrintWriter;

@Command(name = "filetidy",
        mixinStandardHelpOptions = true,
        version = "0.1.0",
        description = "按规则自动整理文件夹的命令行小工具",
        subcommands = {OrganizeCommand.class, UndoCommand.class, ConfigCommand.class})
public class Main implements Runnable {

    /** 正常结束 */
    public static final int EXIT_OK = 0;
    /** 运行期错误：目录不存在、配置不可用等 */
    public static final int EXIT_ERROR = 1;
    /** 命令行参数错误（由 picocli 返回） */
    public static final int EXIT_USAGE = 2;

    /** 设为 1 时，未预期异常会额外打印堆栈，便于排查。 */
    public static final String DEBUG_ENV = "FILETIDY_DEBUG";

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    public static void main(String[] args) {
        ConsoleOutput.install();
        Ansi.install();
        System.exit(createCommandLine().execute(args));
    }

    /** 组装命令行，供 {@code main} 与测试共用。 */
    static CommandLine createCommandLine() {
        return new CommandLine(new Main())
                .setExecutionExceptionHandler(new FriendlyExceptionHandler());
    }

    /**
     * 把异常翻译成一行可读提示，而不是把 Java 堆栈甩给用户。
     * <p>
     * {@link FiletidyException} 是可预期的输入问题，只打印原因即可；
     * 其他异常视为程序缺陷，额外提示用 {@value #DEBUG_ENV} 拿堆栈。
     */
    static final class FriendlyExceptionHandler implements CommandLine.IExecutionExceptionHandler {

        @Override
        public int handleExecutionException(Exception ex, CommandLine commandLine, CommandLine.ParseResult parseResult) {
            PrintWriter err = commandLine.getErr();
            if (ex instanceof FiletidyException known) {
                err.println(Ansi.error("错误: " + known.getMessage()));
                err.println("提示: 用 --help 查看用法。");
            } else {
                err.println(Ansi.error("错误: 发生未预期的异常（" + ex.getClass().getSimpleName()
                        + (ex.getMessage() == null ? "" : ": " + ex.getMessage()) + "）"));
                if (debugEnabled()) {
                    ex.printStackTrace(err);
                } else {
                    err.println("提示: 这可能是程序缺陷，请设置环境变量 " + DEBUG_ENV + "=1 重跑以打印完整堆栈。");
                }
            }
            err.flush();
            return EXIT_ERROR;
        }

        private boolean debugEnabled() {
            String flag = System.getenv(DEBUG_ENV);
            if (flag == null) {
                flag = System.getProperty(DEBUG_ENV);
            }
            return flag != null && !flag.isBlank() && !"0".equals(flag) && !"false".equalsIgnoreCase(flag);
        }
    }
}
