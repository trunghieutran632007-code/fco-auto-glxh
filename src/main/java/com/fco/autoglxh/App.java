package com.fco.autoglxh;

import com.fco.autoglxh.config.ConfigManager;
import com.fco.autoglxh.model.AppConfig;
import com.fco.autoglxh.service.AutoBotService;
import com.fco.autoglxh.service.GlobalHotkeyService;
import com.fco.autoglxh.ui.MainFrame;
import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.SwingUtilities;

/**
 * FCO Auto GLXH - Entry Point.
 *
 * Ứng dụng tự động đá Giả Lập Xếp Hạng (GLXH) trong EA Sports FC Online
 * sử dụng PostMessage API (Background Window Messaging).
 *
 * Luồng khởi động:
 * <ol>
 *   <li>Thiết lập FlatLaf Dark Theme.</li>
 *   <li>Load cấu hình bằng {@link ConfigManager} (tự tạo config.json mặc định nếu thiếu).</li>
 *   <li>Khởi tạo {@link AutoBotService} (engine) + {@link GlobalHotkeyService} (F9/F10).</li>
 *   <li>Mở {@link MainFrame} — MainFrame tự đăng ký làm listener của cả 2 service.</li>
 *   <li>Start hotkey service; đăng ký shutdown hook để dọn tài nguyên khi JVM tắt.</li>
 * </ol>
 *
 * Toàn bộ chạy trên EDT vì FlatLaf/Swing yêu cầu.
 */
public class App {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // 1) Dark theme (phải set trước khi tạo component)
            FlatDarkLaf.setup();

            // 2) Cấu hình
            ConfigManager configManager = new ConfigManager();
            AppConfig config = configManager.load();

            // 3) Services
            AutoBotService botService = new AutoBotService(config);
            GlobalHotkeyService hotkeyService = new GlobalHotkeyService();

            // 4) UI (MainFrame tự set listener cho botService + hotkeyService)
            MainFrame frame = new MainFrame(config, configManager, botService, hotkeyService);

            // 5) Bật lắng nghe phím tắt toàn hệ thống (F9/F10)
            hotkeyService.start();

            // 6) Dọn tài nguyên khi JVM tắt (stop() của cả 2 service đều idempotent)
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                botService.stop();
                hotkeyService.stop();
            }, "App-Shutdown"));

            frame.setVisible(true);
        });
    }
}
