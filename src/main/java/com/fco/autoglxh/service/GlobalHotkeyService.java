package com.fco.autoglxh.service;

import com.fco.autoglxh.native_api.Win32Api;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinUser.MSG;

/**
 * GlobalHotkeyService - Phím tắt toàn hệ thống (F9/F10) qua {@code RegisterHotKey}.
 *
 * Hoạt động ngay cả khi game FCO hoặc app khác đang active (foreground),
 * vì phím tắt được đăng ký ở mức hệ thống (thread hotkey, hwnd=NULL).
 *
 * Cơ chế:
 * - Trên một worker thread riêng, đăng ký F9 (Start/Pause) + F10 (Stop) bằng
 *   {@link Win32Api#registerHotKey} với {@code hwnd=null} (đăng ký cho thread đó).
 * - Vòng lặp {@code PeekMessage(PM_REMOVE)} để gom các message WM_HOTKEY từ queue
 *   của thread. Khi nhận WM_HOTKEY, lấy id từ {@code msg.wParam} rồi gọi listener.
 * - {@code stop()}: đặt {@code running=false} + interrupt worker → vòng lặp thoát
 *   → {@code finally} unregister (BẮT BUỘC unregister trên cùng thread đã register).
 *
 * Chọn PeekMessage polling (mỗi 10ms) thay vì GetMessage blocking để shutdown
 * race-free (không cần PostThreadMessage + thread-id), và 10ms polling CPU ≈ 0.
 *
 * QUAN TRỌNG: RegisterHotKey/UnregisterHotKey/PeekMessage PHẢI chạy trên cùng
 * worker thread → toàn bộ nằm trong {@link #runLoop()}.
 */
public class GlobalHotkeyService {

    /** PeekMessage flag: gom và xoá message khỏi queue. */
    private static final int PM_REMOVE = 0x0001;
    /** Khoảng poll PeekMessage khi queue rỗng. */
    private static final long POLL_INTERVAL_MS = 10;

    private volatile HotkeyListener listener;
    private volatile boolean running = false;
    private Thread worker;

    /** Trạng thái đăng ký lần start gần nhất (để UI/diagnostic biết F9/F10 có đăng ký thành công không). */
    private volatile boolean f9Registered = false;
    private volatile boolean f10Registered = false;

    public void setListener(HotkeyListener listener) {
        this.listener = listener;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isF9Registered() {
        return f9Registered;
    }

    public boolean isF10Registered() {
        return f10Registered;
    }

    // ==================== Lifecycle ====================

    /**
     * Khởi động worker thread + đăng ký F9/F10. An toàn gọi lại khi đang chạy (bỏ qua).
     */
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        worker = new Thread(this::runLoop, "GlobalHotkey-Listener");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Dừng worker thread + unregister F9/F10. Chờ worker thoát (tối đa 500ms) để
     * đảm bảo unregister xong trước khi trả về (tránh để lại hotkey hệ thống).
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        Thread w = worker;
        if (w != null) {
            w.interrupt();
            // Chờ worker thoát (tránh self-join nếu stop() lỡ gọi từ worker)
            if (w != Thread.currentThread()) {
                try {
                    w.join(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        worker = null;
    }

    // ==================== Worker (message loop) ====================

    private void runLoop() {
        // Đăng ký trên thread này (thread hotkey, hwnd=NULL)
        f9Registered = Win32Api.registerHotKey(
                null, Win32Api.HOTKEY_ID_START_PAUSE, 0, Win32Api.VK_F9);
        f10Registered = Win32Api.registerHotKey(
                null, Win32Api.HOTKEY_ID_STOP, 0, Win32Api.VK_F10);

        System.out.println("[Hotkey] F9 (Start/Pause) registered = " + f9Registered);
        System.out.println("[Hotkey] F10 (Stop) registered = " + f10Registered);
        if (!f9Registered) {
            System.err.println("[Hotkey] CẢNH BÁO: F9 đăng ký thất bại — có thể app khác đang chiếm F9.");
        }
        if (!f10Registered) {
            System.err.println("[Hotkey] CẢNH BÁO: F10 đăng ký thất bại — có thể app khác đang chiếm F10.");
        }

        try {
            MSG msg = new MSG();
            while (running) {
                // Gom hết message hiện có trong queue (PM_REMOVE)
                while (User32.INSTANCE.PeekMessage(msg, null, 0, 0, PM_REMOVE)) {
                    if (msg.message == Win32Api.WM_HOTKEY) {
                        int id = msg.wParam.intValue();
                        fireHotkey(id);
                    }
                    // message khác (rất hiếm khi không có window) → bỏ qua
                }
                // Queue rỗng → ngủ ngắn rồi poll lại (ngắt được để stop nhanh)
                try {
                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } finally {
            // BẮT BUỘC unregister trên cùng thread đã register
            if (f9Registered) {
                Win32Api.unregisterHotKey(null, Win32Api.HOTKEY_ID_START_PAUSE);
            }
            if (f10Registered) {
                Win32Api.unregisterHotKey(null, Win32Api.HOTKEY_ID_STOP);
            }
            f9Registered = false;
            f10Registered = false;
            System.out.println("[Hotkey] Đã unregister F9/F10.");
        }
    }

    private void fireHotkey(int id) {
        HotkeyListener l = listener;
        if (l != null) {
            l.onHotkeyPressed(id);
        }
    }
}
