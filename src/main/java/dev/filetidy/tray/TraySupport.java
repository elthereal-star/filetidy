package dev.filetidy.tray;

import java.awt.GraphicsEnvironment;
import java.awt.SystemTray;

/**
 * 探测当前环境能不能显示系统托盘。
 * <p>
 * 无头环境（CI、纯 SSH）以及裁剪掉 AWT 的 native image 都会返回 false，
 * 由调用方决定是报错还是退化成前台监听。
 */
public final class TraySupport {

    private TraySupport() {
    }

    public static boolean isAvailable() {
        try {
            return !GraphicsEnvironment.isHeadless() && SystemTray.isSupported();
        } catch (Exception | LinkageError e) {
            // native image 里没有 AWT 时会抛 NoClassDefFoundError，按不可用处理
            return false;
        }
    }
}
