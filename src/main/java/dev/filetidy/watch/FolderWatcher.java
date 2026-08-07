package dev.filetidy.watch;

import dev.filetidy.core.FileMover;
import dev.filetidy.core.MovePlan;
import dev.filetidy.core.RuleEngine;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;

/**
 * 监听目录的新文件事件。收到事件后做全量重扫而不是只处理事件文件，
 * 这样天然规避了浏览器分块下载产生的重复/半截事件。
 */
public class FolderWatcher {

    private final RuleEngine ruleEngine;
    private final FileMover fileMover;

    public FolderWatcher(RuleEngine ruleEngine, FileMover fileMover) {
        this.ruleEngine = ruleEngine;
        this.fileMover = fileMover;
    }

    public void watch(Path directory) throws IOException, InterruptedException {
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            directory.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
            System.out.println("正在监听 " + directory + "（Ctrl+C 退出）");
            while (true) {
                WatchKey key = watchService.take();
                key.pollEvents();
                key.reset();
                // 等一会儿让文件写完（浏览器下载大文件会连续触发事件）
                Thread.sleep(500);
                List<MovePlan> plans = ruleEngine.plan(directory);
                fileMover.execute(plans);
            }
        }
    }
}
