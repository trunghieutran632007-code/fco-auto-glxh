package com.fco.autoglxh.service;

/**
 * BotListener - Callback để UI (Phase 5) nhận sự kiện từ {@link AutoBotService}.
 *
 * QUAN TRỌNG: tất cả method đều được gọi trên <b>worker thread</b> của bot,
 * KHÔNG phải EDT. Nếu UI cập nhật Swing component, phải tự bọc trong
 * {@code SwingUtilities.invokeLater(...)} để tránh deadlock / vi phạm EDT.
 *
 * Mặc định tất cả method là no-op (dùng default method) để lớp triển khai
 * chỉ override những callback cần thiết.
 */
public interface BotListener {

    /**
     * Gửi một dòng log (timestamp + hành động).
     * Lưu ý: bot chỉ gửi message gốc, UI tự thêm timestamp/format.
     *
     * @param message Nội dung log
     */
    default void onLog(String message) {}

    /**
     * Trạng thái bot vừa thay đổi (IDLE/RUNNING/PAUSED/STOPPED).
     *
     * @param newState Trạng thái mới
     */
    default void onStateChange(BotState newState) {}

    /**
     * Cập nhật thống kê (gọi sau mỗi vòng lặp và khi stop).
     *
     * @param matchCount Số vòng có click đã xử lý (proxy cho "số trận")
     * @param elapsedMs  Thời gian chạy tích lũy (ms), loại trừ thời gian pause
     */
    default void onStatsUpdate(int matchCount, long elapsedMs) {}

    /**
     * Trạng thái cửa sổ game thay đổi (tìm thấy / mất).
     *
     * @param found       true nếu tìm thấy cửa sổ game hợp lệ
     * @param windowTitle Tên cửa sổ game đang tìm
     */
    default void onGameWindowStatus(boolean found, String windowTitle) {}
}
