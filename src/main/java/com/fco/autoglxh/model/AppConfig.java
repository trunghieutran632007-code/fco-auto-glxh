package com.fco.autoglxh.model;

import java.util.ArrayList;
import java.util.List;

/**
 * AppConfig - Model cấu hình tổng thể của ứng dụng.
 *
 * Lưu trữ các thiết lập do người dùng tùy chỉnh + danh sách các {@link ClickTarget}.
 * Được {@code ConfigManager} đọc/ghi ra file {@code config.json} bằng Gson.
 *
 * Các trường:
 * - gameWindowTitle : Tên cửa sổ game FCO (mặc định: "FC ONLINE")
 * - targets         : Danh sách các ClickTarget
 * - loopDelayMs     : Thời gian chờ giữa mỗi vòng lặp kiểm tra (mặc định: 3000ms)
 * - jitterMs        : Độ ngẫu nhiên thời gian (±ms) để giả lập hành vi người (mặc định: 500ms)
 * - useColorCheck   : Bật/tắt kiểm tra màu sắc trước khi click (mặc định: true)
 * - maxMatches      : Giới hạn số trận tự động (0 = vô hạn) (mặc định: 0)
 */
public class AppConfig {

    private String gameWindowTitle;
    private List<ClickTarget> targets;
    private long loopDelayMs;
    private long jitterMs;
    private boolean useColorCheck;
    private int maxMatches;

    // ==================== Constructors ====================

    /** Constructor mặc định với các giá trị hợp lý (cần cho Gson). */
    public AppConfig() {
        this.gameWindowTitle = "FC ONLINE";
        this.targets = new ArrayList<>();
        this.loopDelayMs = 3000;
        this.jitterMs = 500;
        this.useColorCheck = true;
        this.maxMatches = 0;
    }

    // ==================== Getters / Setters ====================

    public String getGameWindowTitle() {
        return gameWindowTitle;
    }

    public void setGameWindowTitle(String gameWindowTitle) {
        this.gameWindowTitle = gameWindowTitle;
    }

    public List<ClickTarget> getTargets() {
        if (targets == null) {
            targets = new ArrayList<>();
        }
        return targets;
    }

    public void setTargets(List<ClickTarget> targets) {
        this.targets = targets;
    }

    public long getLoopDelayMs() {
        return loopDelayMs;
    }

    public void setLoopDelayMs(long loopDelayMs) {
        this.loopDelayMs = loopDelayMs;
    }

    public long getJitterMs() {
        return jitterMs;
    }

    public void setJitterMs(long jitterMs) {
        this.jitterMs = jitterMs;
    }

    public boolean isUseColorCheck() {
        return useColorCheck;
    }

    public void setUseColorCheck(boolean useColorCheck) {
        this.useColorCheck = useColorCheck;
    }

    public int getMaxMatches() {
        return maxMatches;
    }

    public void setMaxMatches(int maxMatches) {
        this.maxMatches = maxMatches;
    }

    // ==================== Debug / Log ====================

    @Override
    public String toString() {
        return "AppConfig{gameWindowTitle='" + gameWindowTitle + '\'' +
                ", targets=" + getTargets().size() + " item(s)" +
                ", loopDelayMs=" + loopDelayMs +
                ", jitterMs=" + jitterMs +
                ", useColorCheck=" + useColorCheck +
                ", maxMatches=" + maxMatches + (maxMatches == 0 ? " (vô hạn)" : "") +
                '}';
    }
}
