package com.fco.autoglxh;

import com.fco.autoglxh.native_api.Win32Api;
import com.fco.autoglxh.native_api.WindowCapture;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * ManualTest - Test thủ công wrapper Win32Api & WindowCapture với Notepad.
 *
 * Cách chạy (PowerShell):
 *   mvn --% exec:java -Dexec.mainClass=com.fco.autoglxh.ManualTest
 * (cmd.exe thì bỏ --%)
 *
 * - Nếu KHÔNG có title arg: tự tìm Notepad qua EnumWindows (title chứa "notepad").
 * - Nếu có title arg: tìm chính xác bằng FindWindow:
 *     mvn --% exec:java -Dexec.mainClass=com.fco.autoglxh.ManualTest "-Dexec.args=<title>"
 *
 * Test sẽ:
 *   - In HWND + kích thước client area.
 *   - Chụp cửa sổ, lưu ảnh ra target/notepad-capture.png.
 *   - In màu pixel tại điểm giữa.
 *   - Gửi click (wrapper postClick) vào giữa cửa sổ -> xem caret có nhảy không.
 */
public class ManualTest {

    private static final String OUTPUT_PNG = "target/notepad-capture.png";

    private record WinInfo(HWND hwnd, String title) {}

    public static void main(String[] args) {
        System.out.println("=== FCO Auto GLXH — Manual Test (Notepad) ===");

        String title = (args != null && args.length > 0 && !args[0].isBlank()) ? args[0] : null;

        HWND hwnd;
        if (title != null) {
            hwnd = Win32Api.findGameWindow(title);
            System.out.println("[Find] Title arg=\"" + title + "\" -> HWND=" + hwnd);
        } else {
            System.out.println("[Find] Tự tìm Notepad qua EnumWindows (title chứa \"notepad\")...");
            WinInfo info = findNotepadWindow();
            hwnd = (info == null) ? null : info.hwnd();
        }

        if (!Win32Api.isWindowValid(hwnd)) {
            System.err.println(">>> KHÔNG tìm thấy cửa sổ Notepad hợp lệ.");
            System.exit(1);
        }

        int w = Win32Api.getClientWidth(hwnd);
        int h = Win32Api.getClientHeight(hwnd);
        int cx = w / 2;
        int cy = h / 2;
        System.out.println("[Window] HWND=" + hwnd + " | client=" + w + "x" + h
                + " | click target=(" + cx + "," + cy + ")");

        try {
            BufferedImage img = WindowCapture.captureWindow(hwnd);
            if (img == null) {
                System.err.println(">>> captureWindow trả null — kiểm tra PrintWindow/GDI.");
            } else {
                File out = new File(OUTPUT_PNG);
                if (out.getParentFile() != null) {
                    out.getParentFile().mkdirs();
                }
                ImageIO.write(img, "png", out);
                System.out.println("[Capture] " + img.getWidth() + "x" + img.getHeight()
                        + " -> " + out.getAbsolutePath());
            }
        } catch (Exception e) {
            System.err.println(">>> Lỗi chụp/lưu ảnh: " + e.getMessage());
            e.printStackTrace();
        }

        Color color = WindowCapture.getPixelColor(hwnd, cx, cy);
        if (color != null) {
            System.out.printf("[Pixel] (%d,%d) -> R=%d G=%d B=%d (#%02x%02x%02x)%n",
                    cx, cy, color.getRed(), color.getGreen(), color.getBlue(),
                    color.getRed(), color.getGreen(), color.getBlue());
        } else {
            System.err.println(">>> getPixelColor trả null — capture có thể đã fail.");
        }

        System.out.println("[Click] Gửi postClick(" + cx + "," + cy + ") — quan sát Notepad...");
        Win32Api.postClick(hwnd, cx, cy);
        System.out.println("[Click] Đã gửi. Caret Notepad có nhảy đến (" + cx + "," + cy + ") không?");

        System.out.println();
        System.out.println(">>> Lưu ý: Notepad Win11 mới (WinUI 3) vùng soạn thảo KHÔNG phải EDIT control chuẩn,");
        System.out.println("    nên click qua PostMessage CÓ THỂ không di chuyển caret.");
        System.out.println("    Nếu cần verify click rõ ràng, dùng Notepad cũ / WordPad / Calculator classic.");
    }

    /**
     * Duyệt EnumWindows, tìm cửa sổ visible có title chứa "notepad" (không phân biệt hoa thường).
     * Nếu không thấy match, in ra tất cả cửa sổ visible có title để người dùng tự lấy title chính xác.
     *
     * @return WinInfo của cửa sổ Notepad, hoặc null nếu không thấy.
     */
    private static WinInfo findNotepadWindow() {
        List<WinInfo> all = new ArrayList<>();
        User32.INSTANCE.EnumWindows(new User32.WNDENUMPROC() {
            @Override
            public boolean callback(HWND h, Pointer data) {
                if (User32.INSTANCE.IsWindowVisible(h)) {
                    char[] buf = new char[512];
                    int len = User32.INSTANCE.GetWindowText(h, buf, 512);
                    if (len > 0) {
                        all.add(new WinInfo(h, new String(buf, 0, len)));
                    }
                }
                return true;
            }
        }, null);

        for (WinInfo wi : all) {
            if (wi.title().toLowerCase().contains("notepad")) {
                System.out.println("[Find] match: \"" + wi.title() + "\" -> " + wi.hwnd());
                return wi;
            }
        }

        System.out.println("[List] Không có cửa sổ nào chứa \"notepad\". Các cửa sổ visible có title:");
        for (WinInfo wi : all) {
            System.out.println("    HWND=" + wi.hwnd() + "  title=\"" + wi.title() + "\"");
        }
        System.out.println("    -> Nếu thấy Notepad trong list, chạy lại với -Dexec.args=<title chính xác>.");
        return null;
    }
}
