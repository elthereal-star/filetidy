package dev.filetidy;

/**
 * 可预期的用户级错误（目录写错、配置有问题等）。
 * <p>
 * 这类异常由 {@link Main} 统一捕获，只向用户打印一行可读的提示，
 * 不暴露 Java 堆栈；非本类型的异常才视为程序缺陷。
 */
public class FiletidyException extends RuntimeException {

    public FiletidyException(String message) {
        super(message);
    }

    public FiletidyException(String message, Throwable cause) {
        super(message, cause);
    }
}
