package com.fco.autoglxh.service;

/**
 * HotkeyListener - Callback khi nhận phím tắt toàn hệ thống từ {@link GlobalHotkeyService}.
 *
 * Được gọi trên <b>worker thread</b> của hotkey service (KHÔNG phải EDT).
 * Nếu UI cập nhật Swing, phải tự bọc trong {@code SwingUtilities.invokeLater(...)}.
 *
 * Mapping id (xem {@link com.fco.autoglxh.native_api.Win32Api}):
 * - {@code HOTKEY_ID_START_PAUSE} (F9) → App toggle bot start/pause
 * - {@code HOTKEY_ID_STOP} (F10) → App stop bot
 */
public interface HotkeyListener {

    /**
     * Phím tắt vừa được nhấn.
     *
     * @param hotkeyId ID của phím tắt (HOTKEY_ID_START_PAUSE hoặc HOTKEY_ID_STOP)
     */
    void onHotkeyPressed(int hotkeyId);
}
