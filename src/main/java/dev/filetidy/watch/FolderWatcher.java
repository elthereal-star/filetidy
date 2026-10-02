package dev.filetidy.watch;

import dev.filetidy.core.TidyService;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/**
 * 监听目录的新文件事件。收到事件后做全量重扫而不是只处理事件文件，
 * 这样天然规避了浏览器分块下载产生的重复/半截事件。
 */
public class FolderWatcher {

    private static final long POLL_TIMEOUT_MILLIS = 500;
    private static final long SETTLE_MILLIS = 500;

    private final TidyService tidyService;
    private final Path directory;
    private final BooleanSupplier running;

    public FolderWatcher(TidyService tidyService, Path directory) {
        this(tidyService, directory, () -> true);
    }

    /**
     * @param running 返回 false 时监听循环退出，供系统托盘的暂停/退出使用
     */
    public FolderWatcher(TidyService tidyService, Path directory, BooleanSupplier running) {
        this.tidyService = tidyService;
        this.directory = directory;
        this.running = running;
    }

    /** 在终端里持续监听，直到进程被中断。 */
    public void watchUntilInterrupted() throws IOException, InterruptedException {
        System.out.println("正在监听 " + directory + "（Ctrl+C 退出）");
        watch(moved -> System.out.println("已整理 " + moved + " 个新文件"));
    }

    /**
     * 阻塞式监听循环。
     * <p>
     * 用 {@code poll} 而不是 {@code take}，好让 {@code running} 能被定期检查，
     * 否则托盘上的「暂停」要等到下一个文件事件才生效。
     *
     * @param onBatch 每批整理完成后的回调，参数是本次移动的文件数；可为 null
     * @return 整个过程累计移动的文件数
     */
    public int watch(IntConsumer onBatch) throws IOException, InterruptedException {
        int total = 0;
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            directory.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
            while (running.getAsBoolean()) {
                WatchKey key = watchService.poll(POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                if (key == null) {
                    continue;
                }
                key.pollEvents();
                key.reset();
                // 等一会儿让文件写完（浏览器下载大文件会连续触发事件）
                Thread.sleep(SETTLE_MILLIS);
                if (!running.getAsBoolean()) {
                    break;
                }
                int moved = tidyService.organize(directory);
                if (moved > 0) {
                    total += moved;
                    if (onBatch != null) {
                        onBatch.accept(moved);
                    }
                }
            }
        }
        return total;
    }
}
