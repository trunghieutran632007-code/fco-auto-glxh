package com.fco.autoglxh.native_api;

import com.sun.jna.Memory;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.platform.win32.WinGDI.*;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * WindowCapture - Chụp ảnh cửa sổ game bằng PrintWindow + GDI.
 *
 * Cho phép chụp nội dung cửa sổ game ngay cả khi cửa sổ bị che bởi cửa sổ khác.
 * Sử dụng để đọc màu pixel tại các tọa độ nút bấm trước khi gửi click.
 *
 * Luồng hoạt động:
 * 1. GetDC → lấy device context của cửa sổ
 * 2. CreateCompatibleDC → tạo memory DC
 * 3. CreateCompatibleBitmap → tạo bitmap tương thích
 * 4. SelectObject → gắn bitmap vào memory DC
 * 5. PrintWindow → render nội dung cửa sổ vào memory DC
 * 6. GetDIBits → đọc pixel data từ bitmap
 * 7. Chuyển đổi pixel data → BufferedImage
 * 8. Cleanup tất cả GDI resources
 */
public class WindowCapture {

    /**
     * Chụp toàn bộ nội dung cửa sổ (client area) thành BufferedImage.
     * Hoạt động ngay cả khi cửa sổ bị che bởi cửa sổ khác (không bị minimize).
     *
     * @param hwnd Handle cửa sổ cần chụp
     * @return BufferedImage chứa nội dung cửa sổ, hoặc null nếu thất bại
     */
    public static BufferedImage captureWindow(HWND hwnd) {
        if (!Win32Api.isWindowValid(hwnd)) {
            return null;
        }

        // Lấy kích thước client area
        RECT rect = Win32Api.getClientRect(hwnd);
        int width = rect.right - rect.left;
        int height = rect.bottom - rect.top;

        if (width <= 0 || height <= 0) {
            return null;
        }

        // === Khởi tạo GDI resources ===
        HDC hdcWindow = User32.INSTANCE.GetDC(hwnd);
        HDC hdcMem = GDI32.INSTANCE.CreateCompatibleDC(hdcWindow);
        HBITMAP hBitmap = GDI32.INSTANCE.CreateCompatibleBitmap(hdcWindow, width, height);
        WinNT.HANDLE oldBitmap = GDI32.INSTANCE.SelectObject(hdcMem, hBitmap);

        try {
            // === Chụp nội dung cửa sổ vào bitmap ===
            // PW_RENDERFULLCONTENT (2) = render đầy đủ nội dung, kể cả khi bị che
            // Nếu không hoạt động (Windows 7), fallback về PW_CLIENTONLY (1)
            boolean success = Win32Api.User32Ex.INSTANCE.PrintWindow(
                    hwnd, hdcMem, Win32Api.PW_RENDERFULLCONTENT
            );

            if (!success) {
                // Fallback: thử với flag 0 (mặc định)
                success = Win32Api.User32Ex.INSTANCE.PrintWindow(hwnd, hdcMem, 0);
            }

            if (!success) {
                return null;
            }

            // === Đọc pixel data từ bitmap bằng GetDIBits ===
            return bitmapToBufferedImage(hdcWindow, hBitmap, width, height);

        } finally {
            // === Cleanup GDI resources (luôn phải cleanup để tránh memory leak) ===
            GDI32.INSTANCE.SelectObject(hdcMem, oldBitmap);
            GDI32.INSTANCE.DeleteObject(hBitmap);
            GDI32.INSTANCE.DeleteDC(hdcMem);
            User32.INSTANCE.ReleaseDC(hwnd, hdcWindow);
        }
    }

    /**
     * Lấy màu pixel tại tọa độ (x, y) trong cửa sổ game.
     * Chụp ảnh cửa sổ rồi đọc pixel từ ảnh.
     *
     * @param hwnd Handle cửa sổ game
     * @param x    Tọa độ X tương đối trong client area
     * @param y    Tọa độ Y tương đối trong client area
     * @return Color tại vị trí (x, y), hoặc null nếu thất bại
     */
    public static Color getPixelColor(HWND hwnd, int x, int y) {
        BufferedImage image = captureWindow(hwnd);
        if (image == null) {
            return null;
        }

        // Kiểm tra tọa độ hợp lệ
        if (x < 0 || x >= image.getWidth() || y < 0 || y >= image.getHeight()) {
            return null;
        }

        int rgb = image.getRGB(x, y);
        return new Color(rgb);
    }

    /**
     * So sánh màu pixel tại tọa độ (x, y) với màu mục tiêu.
     * Cho phép độ chênh lệch (tolerance) cho mỗi kênh R, G, B.
     *
     * @param hwnd         Handle cửa sổ game
     * @param x            Tọa độ X
     * @param y            Tọa độ Y
     * @param targetColor  Màu mục tiêu cần so sánh
     * @param tolerance    Độ chênh lệch cho phép (0–255) cho mỗi kênh RGB
     * @return true nếu màu pixel khớp với màu mục tiêu (trong phạm vi tolerance)
     */
    public static boolean isColorMatch(HWND hwnd, int x, int y, Color targetColor, int tolerance) {
        Color actualColor = getPixelColor(hwnd, x, y);
        if (actualColor == null || targetColor == null) {
            return false;
        }

        return Math.abs(actualColor.getRed() - targetColor.getRed()) <= tolerance
                && Math.abs(actualColor.getGreen() - targetColor.getGreen()) <= tolerance
                && Math.abs(actualColor.getBlue() - targetColor.getBlue()) <= tolerance;
    }

    /**
     * So sánh màu pixel tại tọa độ (x, y) với màu mục tiêu,
     * sử dụng ảnh chụp đã có sẵn (tránh chụp lại nhiều lần trong 1 vòng lặp).
     *
     * @param image        Ảnh chụp cửa sổ (đã chụp trước đó bằng captureWindow)
     * @param x            Tọa độ X
     * @param y            Tọa độ Y
     * @param targetColor  Màu mục tiêu cần so sánh
     * @param tolerance    Độ chênh lệch cho phép (0–255)
     * @return true nếu màu pixel khớp
     */
    public static boolean isColorMatch(BufferedImage image, int x, int y, Color targetColor, int tolerance) {
        if (image == null || targetColor == null) {
            return false;
        }
        if (x < 0 || x >= image.getWidth() || y < 0 || y >= image.getHeight()) {
            return false;
        }

        Color actualColor = new Color(image.getRGB(x, y));

        return Math.abs(actualColor.getRed() - targetColor.getRed()) <= tolerance
                && Math.abs(actualColor.getGreen() - targetColor.getGreen()) <= tolerance
                && Math.abs(actualColor.getBlue() - targetColor.getBlue()) <= tolerance;
    }

    // ==================== Private Helper Methods ====================

    /**
     * Chuyển đổi HBITMAP thành BufferedImage bằng GetDIBits.
     *
     * @param hdc     Device context nguồn
     * @param hBitmap Handle bitmap cần chuyển đổi
     * @param width   Chiều rộng ảnh
     * @param height  Chiều cao ảnh
     * @return BufferedImage, hoặc null nếu thất bại
     */
    private static BufferedImage bitmapToBufferedImage(HDC hdc, HBITMAP hBitmap, int width, int height) {
        // Cấu hình BITMAPINFOHEADER
        BITMAPINFO bmi = new BITMAPINFO();
        bmi.bmiHeader.biWidth = width;
        bmi.bmiHeader.biHeight = -height; // Số âm = top-down (dòng đầu tiên ở trên cùng)
        bmi.bmiHeader.biPlanes = 1;
        bmi.bmiHeader.biBitCount = 32;    // 32-bit BGRA
        bmi.bmiHeader.biCompression = WinGDI.BI_RGB;
        bmi.bmiHeader.biSizeImage = width * height * 4;

        // Cấp phát bộ nhớ cho pixel data
        Memory buffer = new Memory((long) width * height * 4);

        // Đọc pixel data từ bitmap
        int scanLines = GDI32.INSTANCE.GetDIBits(
                hdc, hBitmap, 0, height, buffer, bmi, WinGDI.DIB_RGB_COLORS
        );

        if (scanLines == 0) {
            return null;
        }

        // Chuyển đổi pixel data (BGRA) thành BufferedImage (ARGB)
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = new int[width * height];

        for (int i = 0; i < pixels.length; i++) {
            int offset = i * 4;
            int b = buffer.getByte(offset) & 0xFF;
            int g = buffer.getByte(offset + 1) & 0xFF;
            int r = buffer.getByte(offset + 2) & 0xFF;
            int a = 255; // Mặc định alpha = 255 (không trong suốt)
            pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }

        image.setRGB(0, 0, width, height, pixels, 0, width);
        return image;
    }
}
