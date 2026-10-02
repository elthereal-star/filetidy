package dev.filetidy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * 在测试里执行命令行并捕获输出。
 * <p>
 * 注意要同时替换 {@code System.out/err} 与 picocli 的输出流：子命令里是直接
 * 写 {@code System.out} 的，只设置 picocli 那一份捕获不到内容。
 */
final class CliHarness {

    private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    int run(String... args) {
        PrintStream previousOut = System.out;
        PrintStream previousErr = System.err;
        stdout.reset();
        stderr.reset();
        PrintStream outStream = new PrintStream(stdout, true, StandardCharsets.UTF_8);
        PrintStream errStream = new PrintStream(stderr, true, StandardCharsets.UTF_8);
        try {
            System.setOut(outStream);
            System.setErr(errStream);
            return Main.createCommandLine()
                    .setOut(new PrintWriter(outStream, true))
                    .setErr(new PrintWriter(errStream, true))
                    .execute(args);
        } finally {
            System.setOut(previousOut);
            System.setErr(previousErr);
        }
    }

    String out() {
        return stdout.toString(StandardCharsets.UTF_8);
    }

    String err() {
        return stderr.toString(StandardCharsets.UTF_8);
    }
}
