package dev.filetidy.tray;

import org.junit.jupiter.api.Test;

import java.awt.Image;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TraySupportTest {

    @Test
    void availabilityProbeNeverThrows() {
        // 无头 CI 上应为 false，有桌面的机器上应为 true；这里只要求探测本身不炸
        assertDoesNotThrow(TraySupport::isAvailable);
    }

    @Test
    void iconIsDrawnInMemoryWithoutNeedingADisplay() {
        Image icon = TrayApp.createIcon();

        assertEquals(32, icon.getWidth(null));
        assertEquals(32, icon.getHeight(null));
    }
}
