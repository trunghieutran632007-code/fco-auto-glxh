package com.fco.autoglxh.ui;

import com.fco.autoglxh.config.ConfigManager;
import com.fco.autoglxh.model.AppConfig;
import com.fco.autoglxh.native_api.Win32Api;
import com.fco.autoglxh.service.AutoBotService;
import com.fco.autoglxh.service.BotListener;
import com.fco.autoglxh.service.BotState;
import com.fco.autoglxh.service.GlobalHotkeyService;
import com.fco.autoglxh.service.HotkeyListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * MainFrame - Giao diện chính (Phase 5a: Dashboard + Log + điều khiển).
 *
 * Phạm vi 5a:
 * <ul>
 *   <li><b>Dashboard</b>: đèn LED trạng thái, trạng thái cửa sổ game, nút ▶/⏸/⏹,
 *       đồng hồ đếm thời gian, bộ đếm số trận.</li>
 *   <li><b>Log</b>: JTextArea auto-scroll + nút xóa log.</li>
 *   <li><b>Status bar</b>: nhắc phím tắt F9/F10.</li>
 *   <li>Nối sự kiện với {@link AutoBotService} (qua {@link BotListener}) và
 *       {@link GlobalHotkeyService} (qua {@link HotkeyListener}).</li>
 * </ul>
 *
 * Bảng Targets + Settings + CoordinatePickerDialog sẽ bổ sung ở 5b/5c.
 *
 * Threading: mọi callback từ service đến trên worker thread → được bọc
 * {@link SwingUtilities#invokeLater} trước khi chạm Swing component.
 */
public class MainFrame extends JFrame implements BotListener, HotkeyListener {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Màu LED theo trạng thái
    private static final Color LED_IDLE = new Color(0x9E9E9E);    // xám
    private static final Color LED_RUNNING = new Color(0x4CAF50); // xanh lá
    private static final Color LED_PAUSED = new Color(0xFFC107);  // vàng
    private static final Color LED_STOPPED = new Color(0xF44336); // đỏ
    private static final Color OK_GREEN = new Color(0x4CAF50);
    private static final Color WARN_RED = new Color(0xF44336);

    // Dependencies (configManager giữ sẵn cho 5b: lưu settings/targets)
    private final AppConfig config;
    private final ConfigManager configManager;
    private final AutoBotService botService;
    private final GlobalHotkeyService hotkeyService;

    // Widgets
    private final StatusLed led = new StatusLed();
    private final JLabel stateLabel = new JLabel();
    private final JLabel windowStatusLabel = new JLabel();
    private final JLabel elapsedLabel = new JLabel("00:00:00");
    private final JLabel matchLabel = new JLabel("0");
    private final JButton startBtn = new JButton("▶ Bắt đầu");
    private final JButton pauseBtn = new JButton("⏸ Tạm dừng");
    private final JButton stopBtn = new JButton("⏹ Dừng");
    private final JTextArea logArea = new JTextArea();

    // Panels 5b (khởi tạo trong constructor, trước buildUi)
    private TargetsPanel targetsPanel;
    private SettingsPanel settingsPanel;

    // Đồng hồ cập nhật elapsed/matchCount mỗi giây (chạy trên EDT)
    private final Timer statsTimer = new Timer(1000, e -> refreshStats());

    public MainFrame(AppConfig config,
                     ConfigManager configManager,
                     AutoBotService botService,
                     GlobalHotkeyService hotkeyService) {
        super("FCO Auto GLXH");
        this.config = config;
        this.configManager = configManager;
        this.botService = botService;
        this.hotkeyService = hotkeyService;

        // Panels 5b — tạo trước buildUi() vì buildUi() nhúng chúng vào tab.
        // saveAll = commit settings + ghi config.json; appendLog để 2 panel ghi nhật ký.
        this.settingsPanel = new SettingsPanel(config, this::saveAll, this::appendLog);
        this.targetsPanel = new TargetsPanel(config, this::saveAll, this::appendLog);

        buildUi();
        wireActions();

        // Đăng ký làm listener cho cả 2 service
        botService.setListener(this);
        hotkeyService.setListener(this);

        // Trạng thái khởi tạo
        applyState(botService.getState());
        refreshStats();
        setWindowStatus(false, config.getGameWindowTitle(), true);

        appendLog("UI sẵn sàng. Phím tắt: F9 = Bắt đầu/Tạm dừng, F10 = Dừng.");
        appendLog("Cửa sổ game đang tìm: \"" + config.getGameWindowTitle() + "\".");
        appendLog("Đã tải " + config.getTargets().size() + " mục tiêu từ cấu hình.");

        statsTimer.start();
    }

    // ==================== Build UI ====================

    private void buildUi() {
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE); // tự dọn trong windowClosing
        setSize(820, 660);
        setMinimumSize(new Dimension(720, 560));
        setLocationRelativeTo(null);

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(buildDashboard(), BorderLayout.NORTH);
        content.add(buildCenter(), BorderLayout.CENTER);
        content.add(buildStatusBar(), BorderLayout.SOUTH);
        setContentPane(content);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cleanupAndExit();
            }
        });
    }

    private JPanel buildDashboard() {
        JPanel dash = new JPanel(new BorderLayout(16, 8));
        dash.setBorder(BorderFactory.createTitledBorder("Bảng điều khiển"));

        // --- WEST: trạng thái bot + cửa sổ game ---
        JPanel statusCol = new JPanel();
        statusCol.setLayout(new BoxLayout(statusCol, BoxLayout.Y_AXIS));

        JPanel ledRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        led.setPreferredSize(new Dimension(18, 18));
        stateLabel.setFont(stateLabel.getFont().deriveFont(Font.BOLD, 15f));
        ledRow.add(led);
        ledRow.add(stateLabel);
        ledRow.setAlignmentX(LEFT_ALIGNMENT);

        windowStatusLabel.setAlignmentX(LEFT_ALIGNMENT);
        windowStatusLabel.setBorder(BorderFactory.createEmptyBorder(6, 2, 0, 0));

        statusCol.add(ledRow);
        statusCol.add(windowStatusLabel);

        // --- CENTER: đồng hồ + số trận ---
        JPanel statsRow = new JPanel(new GridLayout(1, 2, 16, 0));
        statsRow.add(buildStatBlock("Thời gian chạy", elapsedLabel));
        statsRow.add(buildStatBlock("Số trận", matchLabel));

        // --- EAST: nút điều khiển ---
        JPanel buttons = new JPanel(new GridLayout(3, 1, 0, 6));
        buttons.add(startBtn);
        buttons.add(pauseBtn);
        buttons.add(stopBtn);

        dash.add(statusCol, BorderLayout.WEST);
        dash.add(statsRow, BorderLayout.CENTER);
        dash.add(buttons, BorderLayout.EAST);
        return dash;
    }

    private JPanel buildStatBlock(String caption, JLabel valueLabel) {
        JPanel block = new JPanel();
        block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
        block.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JLabel cap = new JLabel(caption);
        cap.setForeground(new Color(0x9E9E9E));
        cap.setAlignmentX(CENTER_ALIGNMENT);

        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 22f));
        valueLabel.setAlignmentX(CENTER_ALIGNMENT);

        block.add(cap);
        block.add(Box.createVerticalStrut(2));
        block.add(valueLabel);
        return block;
    }

    private JSplitPane buildCenter() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Mục tiêu (Targets)", targetsPanel);
        tabs.addTab("Cài đặt", settingsPanel);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tabs, buildLogPanel());
        split.setResizeWeight(0.62);
        split.setBorder(null);
        return split;
    }

    /**
     * Nguồn persist duy nhất: commit settings (field → config) rồi ghi config.json.
     * Targets đã được sửa trực tiếp trên {@code config.getTargets()}; gọi trước khi
     * lưu để settings mới nhất không bị ghi đè bằng dữ liệu cũ.
     */
    private void saveAll() {
        settingsPanel.commitToConfig();
        configManager.save(config);
    }

    private JPanel buildLogPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(BorderFactory.createTitledBorder("Nhật ký hoạt động"));

        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(logArea);
        panel.add(scroll, BorderLayout.CENTER);

        JButton clearBtn = new JButton("Xóa log");
        clearBtn.addActionListener(e -> logArea.setText(""));
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        south.add(clearBtn);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    private JLabel buildStatusBar() {
        JLabel bar = new JLabel("Phím tắt:  F9 = Bắt đầu / Tạm dừng     F10 = Dừng");
        bar.setForeground(new Color(0x9E9E9E));
        bar.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));
        return bar;
    }

    private void wireActions() {
        startBtn.addActionListener(e -> botService.start());
        pauseBtn.addActionListener(e -> botService.pause());
        stopBtn.addActionListener(e -> botService.stop());
    }

    // ==================== BotListener (worker thread → EDT) ====================

    @Override
    public void onLog(String message) {
        SwingUtilities.invokeLater(() -> appendLog(message));
    }

    @Override
    public void onStateChange(BotState newState) {
        SwingUtilities.invokeLater(() -> applyState(newState));
    }

    @Override
    public void onStatsUpdate(int matchCount, long elapsedMs) {
        SwingUtilities.invokeLater(this::refreshStats);
    }

    @Override
    public void onGameWindowStatus(boolean found, String windowTitle) {
        SwingUtilities.invokeLater(() -> setWindowStatus(found, windowTitle, false));
    }

    // ==================== HotkeyListener (hotkey worker thread) ====================

    @Override
    public void onHotkeyPressed(int hotkeyId) {
        // botService.* thread-safe (synchronized); UI cập nhật qua listener → invokeLater.
        if (hotkeyId == Win32Api.HOTKEY_ID_START_PAUSE) {
            if (botService.getState() == BotState.RUNNING) {
                botService.pause();
            } else {
                botService.start(); // IDLE/STOPPED → chạy mới; PAUSED → resume
            }
        } else if (hotkeyId == Win32Api.HOTKEY_ID_STOP) {
            botService.stop();
        }
    }

    // ==================== UI updates (chạy trên EDT) ====================

    private void applyState(BotState state) {
        switch (state) {
            case IDLE -> {
                led.setColor(LED_IDLE);
                stateLabel.setText("Chưa chạy");
                startBtn.setText("▶ Bắt đầu");
                setButtons(true, false, false);
            }
            case RUNNING -> {
                led.setColor(LED_RUNNING);
                stateLabel.setText("Đang chạy");
                startBtn.setText("▶ Bắt đầu");
                setButtons(false, true, true);
            }
            case PAUSED -> {
                led.setColor(LED_PAUSED);
                stateLabel.setText("Tạm dừng");
                startBtn.setText("▶ Tiếp tục");
                setButtons(true, false, true);
            }
            case STOPPED -> {
                led.setColor(LED_STOPPED);
                stateLabel.setText("Đã dừng");
                startBtn.setText("▶ Bắt đầu");
                setButtons(true, false, false);
            }
        }
    }

    private void setButtons(boolean start, boolean pause, boolean stop) {
        startBtn.setEnabled(start);
        pauseBtn.setEnabled(pause);
        stopBtn.setEnabled(stop);
    }

    private void setWindowStatus(boolean found, String title, boolean unknown) {
        if (unknown) {
            windowStatusLabel.setText("Cửa sổ game: (chưa kiểm tra) — \"" + title + "\"");
            windowStatusLabel.setForeground(new Color(0x9E9E9E));
        } else if (found) {
            windowStatusLabel.setText("Cửa sổ game: Tìm thấy — \"" + title + "\"");
            windowStatusLabel.setForeground(OK_GREEN);
        } else {
            windowStatusLabel.setText("Cửa sổ game: Không tìm thấy — \"" + title + "\"");
            windowStatusLabel.setForeground(WARN_RED);
        }
    }

    private void refreshStats() {
        elapsedLabel.setText(formatElapsed(botService.getElapsedMs()));
        matchLabel.setText(String.valueOf(botService.getMatchCount()));
    }

    private void appendLog(String message) {
        logArea.append("[" + LocalTime.now().format(TIME_FMT) + "] " + message + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private static String formatElapsed(long ms) {
        long totalSec = Math.max(0, ms) / 1000;
        return String.format("%02d:%02d:%02d", totalSec / 3600, (totalSec % 3600) / 60, totalSec % 60);
    }

    private void cleanupAndExit() {
        statsTimer.stop();
        botService.stop();
        hotkeyService.stop();
        dispose();
        System.exit(0);
    }

    // ==================== LED component ====================

    /** Đèn LED tròn đơn giản, đổi màu theo trạng thái bot. */
    private static class StatusLed extends JComponent {
        private Color color = LED_IDLE;

        void setColor(Color c) {
            this.color = c;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int d = Math.min(getWidth(), getHeight()) - 2;
            int x = (getWidth() - d) / 2;
            int y = (getHeight() - d) / 2;
            g2.setColor(color);
            g2.fillOval(x, y, d, d);
            g2.dispose();
        }
    }
}
