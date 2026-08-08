package com.fco.autoglxh.service;

import com.fco.autoglxh.model.AppConfig;
import com.fco.autoglxh.model.ClickTarget;
import com.fco.autoglxh.native_api.Win32Api;
import com.fco.autoglxh.native_api.WindowCapture;
import com.sun.jna.platform.win32.WinDef.HWND;

import java.awt.image.BufferedImage;
import java.util.concurrent.ThreadLocalRandom;

/**
 * AutoBotService - Engine chính của bot (chạy trên background thread).
 *
 * Luồng hoạt động mỗi vòng lặp (cycle):
 * <ol>
 *   <li>Kiểm tra {@code maxMatches}: nếu đã đạt giới hạn → dừng.</li>
 *   <li>Tìm cửa sổ game bằng {@link Win32Api#findGameWindow(String)}; nếu không hợp lệ
 *       → log + chờ, bỏ qua vòng (không crash).</li>
 *   <li>Chụp cửa sổ 1 LẦN bằng {@link WindowCapture#captureWindow(HWND)} (tránh GDI call lặp).</li>
 *   <li>Duyệt các {@link ClickTarget} đã enabled (theo thứ tự):
 *     <ul>
 *       <li>Nếu {@code useColorCheck} = true → so màu pixel (x,y) với targetColor ± tolerance;
 *           khớp thì click, không khớp thì bỏ qua target đó.</li>
 *       <li>Nếu {@code useColorCheck} = false → click trực tiếp.</li>
 *       <li>Sau mỗi click → ngủ {@code target.delayMs ± jitter} (ngắt được).</li>
 *     </ul>
 *   </li>
 *   <li>Nếu vòng có ít nhất 1 click → {@code matchCount++} (proxy cho "số trận").</li>
 *   <li>Ngủ {@code loopDelayMs ± jitter} trước khi sang vòng tiếp theo.</li>
 * </ol>
 *
 * Thread-safety:
 * - {@code state} là {@code volatile}; worker đọc trực tiếp.
 * - matchCount là {@code volatile} (single writer = worker; start() reset trước khi worker chạy).
 * - Trường thời gian (activeMs/runningSinceMs) được truy cập qua các method {@code synchronized}
 *   để đồng bộ giữa worker và UI đọc.
 * - Listener được gọi NGOÀI synchronized block (tránh deadlock), trên worker thread.
 */
public class AutoBotService {

    private final AppConfig config;
    private volatile BotListener listener;

    private volatile BotState state = BotState.IDLE;

    /** Worker thread (mỗi lần start tạo thread mới). */
    private Thread worker;

    // ==================== Stats ====================

    /** Số vòng lặp có ít nhất 1 click (proxy cho "số trận đã xử lý"). */
    private volatile int matchCount = 0;

    /** Thời gian chạy tích lũy (ms), chưa tính khoảng đang chạy hiện tại. */
    private long activeMs = 0;
    /** Mốc bắt đầu khoảng chạy hiện tại (ms), 0 khi không chạy. */
    private long runningSinceMs = 0;
    /** Giá trị elapsed cuối cùng khi stop (để UI hiển thị ổn định sau khi dừng). */
    private volatile long finalElapsedMs = 0;

    // ==================== Constructor ====================

    /**
     * @param config Cấu hình (tham chiếu live — UI có thể mutate, bot đọc mỗi vòng)
     */
    public AutoBotService(AppConfig config) {
        this.config = config;
    }

    public void setListener(BotListener listener) {
        this.listener = listener;
    }

    public BotState getState() {
        return state;
    }

    public int getMatchCount() {
        return matchCount;
    }

    /**
     * @return Thời gian chạy tích lũy (ms) trừ thời gian pause.
     */
    public synchronized long getElapsedMs() {
        if (state == BotState.RUNNING) {
            return activeMs + (System.currentTimeMillis() - runningSinceMs);
        }
        return (state == BotState.STOPPED) ? finalElapsedMs : activeMs;
    }

    // ==================== Control ====================

    /**
     * Bắt đầu phiên mới (từ IDLE/STOPPED). Nếu đang PAUSED → resume.
     * Nếu đang RUNNING → bỏ qua.
     */
    public synchronized void start() {
        if (state == BotState.RUNNING) {
            return;
        }
        if (state == BotState.PAUSED) {
            resume();
            return;
        }
        // IDLE hoặc STOPPED → khởi động phiên mới
        matchCount = 0;
        activeMs = 0;
        finalElapsedMs = 0;
        runningSinceMs = System.currentTimeMillis();
        setState(BotState.RUNNING);

        worker = new Thread(this::runLoop, "AutoBot-Worker");
        worker.setDaemon(true);
        worker.start();
        log("Bot BẮT ĐẦU (windowTitle=\"" + config.getGameWindowTitle() + "\")");
    }

    /** Tạm dừng (chỉ có hiệu lực khi RUNNING). */
    public synchronized void pause() {
        if (state != BotState.RUNNING) {
            return;
        }
        activeMs += System.currentTimeMillis() - runningSinceMs;
        runningSinceMs = 0;
        setState(BotState.PAUSED);
        log("Bot TẠM DỪNG");
    }

    /** Tiếp tục sau khi pause (chỉ có hiệu lực khi PAUSED). */
    public synchronized void resume() {
        if (state != BotState.PAUSED) {
            return;
        }
        runningSinceMs = System.currentTimeMillis();
        setState(BotState.RUNNING);
        log("Bot TIẾP TỤC");
    }

    /** Dừng hẳn — kết thúc worker thread. An toàn gọi từ bất kỳ trạng thái nào. */
    public synchronized void stop() {
        if (state == BotState.STOPPED || state == BotState.IDLE) {
            // IDLE: đảm bảo trạng thái dừng dứt khoát
            if (state == BotState.IDLE) {
                setState(BotState.STOPPED);
            }
            return;
        }
        // Đang RUNNING → tính nốt thời gian chạy
        if (runningSinceMs > 0) {
            activeMs += System.currentTimeMillis() - runningSinceMs;
            runningSinceMs = 0;
        }
        finalElapsedMs = activeMs;
        setState(BotState.STOPPED);

        // Ngắt worker để thoát khỏi sleep (không ngắt self khi gọi từ worker)
        if (worker != null && worker != Thread.currentThread()) {
            worker.interrupt();
        }
        log("Bot DỪNG — matchCount=" + matchCount + ", elapsed=" + finalElapsedMs + "ms");
    }

    // ==================== Worker Loop ====================

    private void runLoop() {
        while (state != BotState.STOPPED) {
            if (state == BotState.PAUSED) {
                // Chờ đến khi resume/stop (ngắt được)
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }
            // RUNNING
            runOneCycle();
        }
        // Đảm bảo state STOPPED + stats cuối đã được notify
        notifyStats();
    }

    /**
     * Thực thi một vòng lặp.
     */
    private void runOneCycle() {
        // 1) Kiểm tra giới hạn số trận
        int max = config.getMaxMatches();
        if (max > 0 && matchCount >= max) {
            log("Đạt giới hạn maxMatches=" + max + " → tự động dừng");
            stop();
            return;
        }

        // 2) Tìm cửa sổ game
        String title = config.getGameWindowTitle();
        HWND hwnd = Win32Api.findGameWindow(title);
        if (!Win32Api.isWindowValid(hwnd)) {
            log("Không tìm thấy cửa sổ game \"" + title + "\" — chờ vòng sau");
            notifyWindow(false, title);
            // Ngủ loopDelay rồi thử lại (ngắt được)
            if (sleepInterruptible(jitter(config.getLoopDelayMs()))) {
                return;
            }
            return;
        }
        notifyWindow(true, title);

        // 3) Chụp cửa sổ 1 lần (reuse cho mọi target)
        BufferedImage image = WindowCapture.captureWindow(hwnd);
        if (image == null) {
            log("Không chụp được cửa sổ game — bỏ qua vòng");
            if (sleepInterruptible(jitter(config.getLoopDelayMs()))) {
                return;
            }
            return;
        }

        // 4) Duyệt các target đã enabled
        boolean clickedAny = false;
        for (ClickTarget t : config.getTargets()) {
            if (state != BotState.RUNNING) {
                return; // bị pause/stop giữa chừng → bỏ dở vòng
            }
            if (!t.isEnabled()) {
                continue;
            }

            boolean shouldClick;
            if (config.isUseColorCheck()) {
                shouldClick = WindowCapture.isColorMatch(
                        image, t.getX(), t.getY(), t.getTargetColor(), t.getColorTolerance());
            } else {
                shouldClick = true;
            }

            if (!shouldClick) {
                log("Bỏ qua \"" + t.getName() + "\" (màu không khớp) @ (" + t.getX() + "," + t.getY() + ")");
                continue;
            }

            Win32Api.postClick(hwnd, t.getX(), t.getY());
            clickedAny = true;
            log("Click → \"" + t.getName() + "\" @ (" + t.getX() + "," + t.getY() + ")");
            notifyStats();

            // 5) Delay sau click (ngắt được)
            if (sleepInterruptible(jitter(t.getDelayMs()))) {
                return;
            }
        }

        // 6) Cập nhật matchCount (proxy cho số trận)
        if (clickedAny) {
            matchCount++;
            log("Vòng có click → matchCount = " + matchCount);
        } else {
            log("Vòng không có click nào phù hợp");
        }
        notifyStats();

        // 7) Delay giữa các vòng (ngắt được)
        sleepInterruptible(jitter(config.getLoopDelayMs()));
    }

    // ==================== Helpers ====================

    /**
     * Ngủ {@code ms} (tính cả jitter đã cộng sẵn), ngắt được theo state/interrupt.
     * Ngủ theo chunk 50ms để stop/pause phản hồi nhanh.
     *
     * @return true nếu bị ngắt (state ≠ RUNNING hoặc thread interrupted) → nên thoát vòng
     */
    private boolean sleepInterruptible(long ms) {
        long end = System.currentTimeMillis() + Math.max(0, ms);
        while (true) {
            long now = System.currentTimeMillis();
            if (now >= end) {
                return false;
            }
            if (state != BotState.RUNNING) {
                return true; // bị pause/stop
            }
            try {
                Thread.sleep(Math.min(50L, end - now));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return true;
            }
        }
    }

    /**
     * Cộng độ ngẫu nhiên jitter vào {@code base}: kết quả trong [base - jitter, base + jitter], tối thiểu 0.
     */
    private long jitter(long base) {
        long j = config.getJitterMs();
        if (j <= 0) {
            return Math.max(0, base);
        }
        long delta = ThreadLocalRandom.current().nextLong(-j, j + 1);
        return Math.max(0, base + delta);
    }

    private void setState(BotState newState) {
        this.state = newState;
        BotListener l = listener;
        if (l != null) {
            l.onStateChange(newState);
        }
    }

    private void log(String message) {
        BotListener l = listener;
        if (l != null) {
            l.onLog(message);
        }
    }

    private void notifyStats() {
        BotListener l = listener;
        if (l != null) {
            l.onStatsUpdate(matchCount, getElapsedMs());
        }
    }

    private void notifyWindow(boolean found, String title) {
        BotListener l = listener;
        if (l != null) {
            l.onGameWindowStatus(found, title);
        }
    }
}
