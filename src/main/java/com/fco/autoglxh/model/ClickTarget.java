package com.fco.autoglxh.model;

import java.awt.Color;

/**
 * ClickTarget - Lưu thông tin một vị trí bấm trong cửa sổ game.
 *
 * Mỗi ClickTarget đại diện cho một nút bấm (VD: "Tiếp tục", "Tìm trận", "Đã sẵn sàng")
 * cùng với tọa độ tương đối trong client area của cửa sổ game và màu sắc kỳ vọng
 * tại vị trí đó (để kiểm tra trước khi click).
 *
 * Các trường:
 * - name          : Tên nút (hiển thị trong UI và log)
 * - x, y          : Tọa độ TƯƠNG ĐỐI trong client area (không phải tọa độ màn hình)
 * - targetColorRGB: Màu sắc kỳ vọng tại (x, y) dưới dạng mảng [R, G, B]
 * - colorTolerance: Độ chênh lệch màu cho phép (0–255) cho mỗi kênh RGB
 * - delayMs       : Thời gian chờ (ms) sau khi click nút này
 * - enabled       : Bật/tắt từng điểm click
 */
public class ClickTarget {

    private String name;
    private int x;
    private int y;
    private int[] targetColorRGB;
    private int colorTolerance;
    private long delayMs;
    private boolean enabled;

    // ==================== Constructors ====================

    /** Constructor mặc định (cần cho Gson). */
    public ClickTarget() {
        this.targetColorRGB = new int[]{0, 0, 0};
        this.colorTolerance = 10;
        this.delayMs = 500;
        this.enabled = true;
    }

    /**
     * Constructor đầy đủ.
     *
     * @param name           Tên nút
     * @param x              Tọa độ X tương đối
     * @param y              Tọa độ Y tương đối
     * @param targetColorRGB Màu mục tiêu [R, G, B]
     * @param colorTolerance Độ chênh lệch cho phép (0–255)
     * @param delayMs        Thời gian chờ sau khi click (ms)
     * @param enabled        Bật/tắt điểm click
     */
    public ClickTarget(String name, int x, int y, int[] targetColorRGB,
                       int colorTolerance, long delayMs, boolean enabled) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.targetColorRGB = targetColorRGB != null ? targetColorRGB : new int[]{0, 0, 0};
        this.colorTolerance = colorTolerance;
        this.delayMs = delayMs;
        this.enabled = enabled;
    }

    /**
     * Constructor gọn (bật mặc định, tolerance/delay theo default).
     *
     * @param name Tên nút
     * @param x   Tọa độ X
     * @param y   Tọa độ Y
     */
    public ClickTarget(String name, int x, int y) {
        this();
        this.name = name;
        this.x = x;
        this.y = y;
    }

    // ==================== Getters / Setters ====================

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int[] getTargetColorRGB() {
        return targetColorRGB;
    }

    public void setTargetColorRGB(int[] targetColorRGB) {
        this.targetColorRGB = targetColorRGB;
    }

    public int getColorTolerance() {
        return colorTolerance;
    }

    public void setColorTolerance(int colorTolerance) {
        this.colorTolerance = colorTolerance;
    }

    public long getDelayMs() {
        return delayMs;
    }

    public void setDelayMs(long delayMs) {
        this.delayMs = delayMs;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    // ==================== Convenience: Color Helpers ====================

    /**
     * Lấy màu mục tiêu dưới dạng {@link Color} (dùng cho {@code WindowCapture.isColorMatch}).
     *
     * @return Color từ targetColorRGB, hoặc Color.BLACK nếu mảng null/rỗng
     */
    public Color getTargetColor() {
        if (targetColorRGB == null || targetColorRGB.length < 3) {
            return Color.BLACK;
        }
        return new Color(
                targetColorRGB[0] & 0xFF,
                targetColorRGB[1] & 0xFF,
                targetColorRGB[2] & 0xFF
        );
    }

    /**
     * Gán màu mục tiêu từ một {@link Color}.
     *
     * @param color Màu cần đặt
     */
    public void setTargetColor(Color color) {
        if (color == null) {
            this.targetColorRGB = new int[]{0, 0, 0};
            return;
        }
        this.targetColorRGB = new int[]{
                color.getRed(),
                color.getGreen(),
                color.getBlue()
        };
    }

    // ==================== Debug / Log ====================

    @Override
    public String toString() {
        int r = (targetColorRGB != null && targetColorRGB.length >= 1) ? targetColorRGB[0] : 0;
        int g = (targetColorRGB != null && targetColorRGB.length >= 2) ? targetColorRGB[1] : 0;
        int b = (targetColorRGB != null && targetColorRGB.length >= 3) ? targetColorRGB[2] : 0;
        return "ClickTarget{name='" + name + '\'' +
                ", x=" + x +
                ", y=" + y +
                ", color=rgb(" + r + "," + g + "," + b + ")" +
                ", tolerance=" + colorTolerance +
                ", delay=" + delayMs + "ms" +
                ", enabled=" + enabled +
                '}';
    }
}
