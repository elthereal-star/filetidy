package dev.filetidy.tray;

import dev.filetidy.core.TidyService;
import dev.filetidy.watch.FolderWatcher;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 常驻系统托盘：图标 + 右键菜单（立即整理 / 暂停监听 / 打开目录 / 退出）。
 * <p>
 * 监听在后台线程里跑，{@code start()} 会一直阻塞到用户选择退出。
 */
public class TrayApp {

    private static final String TITLE = "filetidy";

    private final TidyService tidyService;
    private final Path directory;
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean exited = new AtomicBoolean(false);
    private final CountDownLatch stopped = new CountDownLatch(1);

    private TrayIcon trayIcon;

    public TrayApp(TidyService tidyService, Path directory) {
        this.tidyService = tidyService;
        this.directory = directory;
    }

    /** 装上托盘图标并开始监听，直到用户选择退出。 */
    public void start() throws Exception {
        EventQueue.invokeAndWait(this::install);
        startWatching();
        stopped.await();
    }

    private void install() {
        PopupMenu menu = new PopupMenu();

        MenuItem organizeNow = new MenuItem("立即整理");
        organizeNow.addActionListener(event -> runInBackground(this::organizeNow));
        menu.add(organizeNow);

        MenuItem togglePause = new MenuItem("暂停监听");
        togglePause.addActionListener(event -> runInBackground(this::togglePause));
        menu.add(togglePause);

        MenuItem openFolder = new MenuItem("打开目录");
        openFolder.addActionListener(event -> runInBackground(this::openFolder));
        menu.add(openFolder);

        menu.addSeparator();
        MenuItem quit = new MenuItem("退出");
        quit.addActionListener(event -> quit());
        menu.add(quit);

        trayIcon = new TrayIcon(createIcon(), TITLE + " — " + directory, menu);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(event -> runInBackground(this::openFolder));
        try {
            SystemTray.getSystemTray().add(trayIcon);
            notify("正在监听 " + directory);
        } catch (Exception e) {
            throw new IllegalStateException("无法添加托盘图标: " + e.getMessage(), e);
        }
    }

    private void startWatching() {
        Thread thread = new Thread(() -> {
            try {
                new FolderWatcher(tidyService, directory, () -> !exited.get() && !paused.get())
                        .watch(moved -> notify("已整理 " + moved + " 个新文件"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                notifyError("监听中断: " + e.getMessage());
            }
        }, "filetidy-watch");
        thread.setDaemon(true);
        thread.start();
    }

    private void organizeNow() {
        try {
            int moved = tidyService.organize(directory);
            notify(moved == 0 ? "没有需要整理的文件" : "已整理 " + moved + " 个文件");
        } catch (Exception e) {
            notifyError("整理失败: " + e.getMessage());
        }
    }

    private void togglePause() {
        boolean nowPaused = !paused.get();
        paused.set(nowPaused);
        if (nowPaused) {
            notify("已暂停监听");
        } else {
            startWatching();
            notify("已恢复监听");
        }
    }

    private void openFolder() {
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                notify("当前平台不支持打开文件夹，请手动前往: " + directory);
                return;
            }
            Desktop.getDesktop().open(directory.toFile());
        } catch (Exception e) {
            notify("打开目录失败: " + e.getMessage());
        }
    }

    private void quit() {
        exited.set(true);
        paused.set(true);
        try {
            SystemTray.getSystemTray().remove(trayIcon);
        } catch (Exception ignored) {
            // 已经移除或从未添加成功，无需处理
        }
        stopped.countDown();
    }

    private void notify(String message) {
        if (trayIcon != null) {
            trayIcon.displayMessage(TITLE, message, TrayIcon.MessageType.INFO);
        }
    }

    private void notifyError(String message) {
        if (trayIcon != null) {
            trayIcon.displayMessage(TITLE, message, TrayIcon.MessageType.ERROR);
        }
    }

    private static void runInBackground(Runnable task) {
        Thread worker = new Thread(task, "filetidy-tray-action");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * 图标用代码画，避免往仓库里塞二进制资源。
     */
    static Image createIcon() {
        int size = 32;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(0x7C, 0x5C, 0xEB));
            graphics.fillRoundRect(3, 3, 15, 10, 5, 5);
            graphics.setColor(new Color(0x4F, 0x6C, 0xF0));
            graphics.fillRoundRect(2, 8, 28, 21, 6, 6);
        } finally {
            graphics.dispose();
        }
        return image;
    }
}
