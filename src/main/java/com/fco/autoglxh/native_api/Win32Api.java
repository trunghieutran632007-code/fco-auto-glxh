package com.fco.autoglxh.native_api;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.win32.StdCallLibrary;

/**
 * Win32Api - Lớp wrapper gọi các Windows API cần thiết qua JNA.
 *
 * Cung cấp các method tiện ích để:
 * - Tìm cửa sổ game FCO (FindWindow)
 * - Gửi click chuột đến cửa sổ game (PostMessage)
 * - Lấy kích thước cửa sổ (GetClientRect)
 * - Kiểm tra cửa sổ còn tồn tại (IsWindow)
 * - Đăng ký phím tắt toàn hệ thống (RegisterHotKey / UnregisterHotKey)
 */
public class Win32Api {

    // ==================== Windows Message Constants ====================

    /** Chuột trái nhấn xuống */
    public static final int WM_LBUTTONDOWN = 0x0201;
    /** Chuột trái thả ra */
    public static final int WM_LBUTTONUP = 0x0202;
    /** Cờ cho biết nút chuột trái đang được giữ */
    public static final int MK_LBUTTON = 0x0001;
    /** Message hotkey */
    public static final int WM_HOTKEY = 0x0312;

    /** Thời gian chờ giữa LBUTTONDOWN và LBUTTONUP (ms) */
    private static final int CLICK_HOLD_DELAY_MS = 50;

    // ==================== Custom User32 Extension ====================

    /**
     * Mở rộng User32 để thêm PrintWindow (có thể chưa có sẵn trong JNA platform).
     */
    public interface User32Ex extends StdCallLibrary {
        User32Ex INSTANCE = Native.load("user32", User32Ex.class);

        /**
         * Chụp nội dung cửa sổ vào device context.
         *
         * @param hwnd   Handle cửa sổ cần chụp
         * @param hdcBlt Device context đích
         * @param nFlags Cờ: 0 = chỉ client area, 2 = PW_RENDERFULLCONTENT
         * @return true nếu thành công
         */
        boolean PrintWindow(HWND hwnd, HDC hdcBlt, int nFlags);
    }

    // ==================== PrintWindow Flags ====================

    /** Chỉ chụp client area */
    public static final int PW_CLIENTONLY = 0x1;
    /** Render toàn bộ nội dung (bao gồm cả khi bị che) - Windows 8.1+ */
    public static final int PW_RENDERFULLCONTENT = 0x2;

    // ==================== Window Finding ====================

    /**
     * Tìm cửa sổ game theo tên (window title).
     *
     * @param windowTitle Tên cửa sổ game (VD: "FC ONLINE")
     * @return HWND nếu tìm thấy, null nếu không
     */
    public static HWND findGameWindow(String windowTitle) {
        return User32.INSTANCE.FindWindow(null, windowTitle);
    }

    /**
     * Kiểm tra cửa sổ có còn tồn tại và hợp lệ không.
     *
     * @param hwnd Handle cửa sổ cần kiểm tra
     * @return true nếu cửa sổ còn sống
     */
    public static boolean isWindowValid(HWND hwnd) {
        return hwnd != null && User32.INSTANCE.IsWindow(hwnd);
    }

    // ==================== Mouse Click via PostMessage ====================

    /**
     * Gửi click chuột trái đến cửa sổ game tại tọa độ (x, y) tương đối.
     * Sử dụng PostMessage nên KHÔNG chiếm con trỏ chuột thật.
     *
     * @param hwnd Handle cửa sổ game
     * @param x    Tọa độ X tương đối trong cửa sổ (client area)
     * @param y    Tọa độ Y tương đối trong cửa sổ (client area)
     */
    public static void postClick(HWND hwnd, int x, int y) {
        LPARAM lParam = makeLParam(x, y);
        WPARAM wParamDown = new WPARAM(MK_LBUTTON);
        WPARAM wParamUp = new WPARAM(0);

        // Gửi nhấn chuột trái xuống
        User32.INSTANCE.PostMessage(hwnd, WM_LBUTTONDOWN, wParamDown, lParam);

        // Chờ một chút để mô phỏng thời gian giữ chuột (giống người thật)
        try {
            Thread.sleep(CLICK_HOLD_DELAY_MS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        // Gửi thả chuột trái
        User32.INSTANCE.PostMessage(hwnd, WM_LBUTTONUP, wParamUp, lParam);
    }

    // ==================== Window Size ====================

    /**
     * Lấy kích thước vùng client (vùng hiển thị game, không bao gồm title bar và viền).
     *
     * @param hwnd Handle cửa sổ game
     * @return RECT chứa kích thước (left, top luôn = 0; right = width, bottom = height)
     */
    public static RECT getClientRect(HWND hwnd) {
        RECT rect = new RECT();
        User32.INSTANCE.GetClientRect(hwnd, rect);
        return rect;
    }

    /**
     * Lấy chiều rộng vùng client.
     */
    public static int getClientWidth(HWND hwnd) {
        RECT rect = getClientRect(hwnd);
        return rect.right - rect.left;
    }

    /**
     * Lấy chiều cao vùng client.
     */
    public static int getClientHeight(HWND hwnd) {
        RECT rect = getClientRect(hwnd);
        return rect.bottom - rect.top;
    }

    // ==================== Global Hotkeys ====================

    /**
     * Đăng ký phím tắt toàn hệ thống.
     *
     * @param hwnd Handle cửa sổ nhận message WM_HOTKEY (null = thread hiện tại)
     * @param id   ID duy nhất cho phím tắt
     * @param modifiers Tổ hợp phím bổ sung (0 = không có, MOD_ALT, MOD_CONTROL,...)
     * @param vk   Virtual key code (VD: VK_F9 = 0x78, VK_F10 = 0x79)
     * @return true nếu đăng ký thành công
     */
    public static boolean registerHotKey(HWND hwnd, int id, int modifiers, int vk) {
        return User32.INSTANCE.RegisterHotKey(hwnd, id, modifiers, vk);
    }

    /**
     * Hủy đăng ký phím tắt toàn hệ thống.
     *
     * @param hwnd Handle cửa sổ đã đăng ký
     * @param id   ID phím tắt cần hủy
     * @return true nếu hủy thành công
     */
    public static boolean unregisterHotKey(HWND hwnd, int id) {
        if (hwnd == null) {
            return User32.INSTANCE.UnregisterHotKey(null, id);
        }
        return User32.INSTANCE.UnregisterHotKey(hwnd.getPointer(), id);
    }

    // ==================== Hotkey IDs & Virtual Keys ====================

    /** ID phím tắt F9 (Start/Pause) */
    public static final int HOTKEY_ID_START_PAUSE = 1;
    /** ID phím tắt F10 (Stop) */
    public static final int HOTKEY_ID_STOP = 2;

    /** Virtual key: F9 */
    public static final int VK_F9 = 0x78;
    /** Virtual key: F10 */
    public static final int VK_F10 = 0x79;

    // ==================== Helper Methods ====================

    /**
     * Tạo LPARAM từ tọa độ (x, y).
     * Format: LPARAM = (y << 16) | (x & 0xFFFF)
     * Đây là cách Windows đóng gói tọa độ chuột vào LPARAM.
     */
    public static LPARAM makeLParam(int x, int y) {
        return new LPARAM(((long) y << 16) | (x & 0xFFFF));
    }

    /**
     * Lấy Device Context (DC) của cửa sổ.
     */
    public static HDC getDC(HWND hwnd) {
        return User32.INSTANCE.GetDC(hwnd);
    }

    /**
     * Giải phóng Device Context (DC) của cửa sổ.
     */
    public static void releaseDC(HWND hwnd, HDC hdc) {
        User32.INSTANCE.ReleaseDC(hwnd, hdc);
    }
}
