package com.fco.autoglxh.service;

/**
 * BotState - Các trạng thái của bot engine.
 *
 * Vòng đời: {@link #IDLE} → {@link #RUNNING} ↔ {@link #PAUSED} → {@link #STOPPED}.
 * Từ {@link #STOPPED} có thể gọi start() lại để khởi động phiên mới (quay về {@link #RUNNING}).
 */
public enum BotState {
    /** Chưa chạy (trạng thái khởi tạo / sau khi stop xong). */
    IDLE,
    /** Đang chạy vòng lặp chính. */
    RUNNING,
    /** Tạm dừng (giữ phiên, có thể resume tiếp tục). */
    PAUSED,
    /** Dừng hẳn — kết thúc worker thread, không tiếp tục. */
    STOPPED
}
