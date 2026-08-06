# 🧠 Project Memory — FCO Auto GLXH

> File này dùng để theo dõi tiến độ dự án, liệt kê các công việc cần làm.
> Bất kỳ ai (Hieu, AI Agent, collaborator) đều có thể nhìn vào đây để biết trạng thái hiện tại và công việc tiếp theo.

---

## 📌 Thông tin dự án

| Key | Value |
|---|---|
| **Tên dự án** | FCO Auto GLXH |
| **Mô tả** | Java app tự động đá Giả Lập Xếp Hạng trong EA Sports FC Online bằng PostMessage API |
| **Ngôn ngữ** | Java 21+ |
| **Build tool** | Maven |
| **Thư mục** | `C:\Users\Hieu\Documents\GitHub\fco-auto-glxh` |
| **Cơ chế chính** | PostMessage (Background Window Messaging) qua JNA |
| **Kế hoạch chi tiết** | [IMPLEMENTATION_PLAN.md](./IMPLEMENTATION_PLAN.md) |

---

## 📋 Quy ước trạng thái

| Ký hiệu | Ý nghĩa |
|---|---|
| `[ ]` | Chưa bắt đầu |
| `[/]` | Đang làm |
| `[x]` | Hoàn thành |
| `[!]` | Bị chặn / cần hỗ trợ |
| `[~]` | Tạm hoãn / cần xem lại |

---

## 🔧 Phase 0: Khởi tạo dự án

- [x] Tạo repo GitHub trên GitHub Desktop
- [x] Tạo file `IMPLEMENTATION_PLAN.md`
- [x] Tạo file `memory.md` (file này)
- [x] Tạo file `pom.xml` (Maven config, dependencies: JNA, FlatLaf, Gson, maven-shade-plugin)
- [x] Tạo cấu trúc thư mục `src/main/java/com/fco/autoglxh/`
- [x] Verify `mvn clean compile` chạy thành công ✅ BUILD SUCCESS
- [ ] Commit: `chore: init maven project structure`

---

## 🏗️ Phase 1: Windows API Layer (JNA Wrapper)

> Tầng nền tảng – gọi Windows API qua JNA. Các tầng trên sẽ phụ thuộc vào đây.

- [x] **Win32Api.java** (`native_api/`)
  - [x] Khai báo JNA interface cho `User32` (FindWindow, PostMessage, GetClientRect, IsWindow, RegisterHotKey, UnregisterHotKey)
  - [x] Khai báo JNA interface mở rộng `User32Ex` cho `PrintWindow`
  - [x] Khai báo các hằng số Windows (WM_LBUTTONDOWN, WM_LBUTTONUP, MK_LBUTTON, PW_RENDERFULLCONTENT,...)
  - [x] Viết helper method: `findGameWindow(title)` → trả về HWND
  - [x] Viết helper method: `postClick(hwnd, x, y)` → gửi WM_LBUTTONDOWN + WM_LBUTTONUP
  - [ ] Test thủ công: tìm cửa sổ Notepad, gửi click → xem có phản hồi không

- [x] **WindowCapture.java** (`native_api/`)
  - [x] Chụp ảnh cửa sổ bằng `PrintWindow` + GDI → trả về `BufferedImage`
  - [x] Method `getPixelColor(hwnd, x, y)` → trả về `Color`
  - [x] Method `captureWindow(hwnd)` → trả về `BufferedImage`
  - [x] Method `isColorMatch()` (2 overloads: từ hwnd hoặc từ BufferedImage có sẵn)
  - [ ] Test thủ công: chụp cửa sổ Notepad, lưu ra file `.png` kiểm tra

- [ ] Commit: `feat: add Win32 API wrapper and window capture`

---

## 📦 Phase 2: Core Models & Config

> Định nghĩa dữ liệu và quản lý cấu hình.

- [ ] **ClickTarget.java** (`model/`)
  - [ ] Các trường: `name`, `x`, `y`, `targetColorRGB` (int[3]), `colorTolerance`, `delayMs`, `enabled`
  - [ ] Constructor, getters, setters
  - [ ] Method `toString()` cho debug/log

- [ ] **AppConfig.java** (`model/`)
  - [ ] Các trường: `gameWindowTitle`, `targets` (List<ClickTarget>), `loopDelayMs`, `jitterMs`, `useColorCheck`, `maxMatches`
  - [ ] Giá trị mặc định hợp lý (gameWindowTitle = "FC ONLINE", loopDelayMs = 3000, jitterMs = 500,...)

- [ ] **ConfigManager.java** (`config/`)
  - [ ] `load()` → đọc `config.json` bằng Gson, trả về `AppConfig`
  - [ ] `save(AppConfig)` → ghi ra `config.json`
  - [ ] Tự tạo `config.json` mặc định nếu file chưa tồn tại
  - [ ] Xác định đường dẫn config: cùng thư mục với file JAR

- [ ] Commit: `feat: add models and config manager`

---

## ⚙️ Phase 3: Bot Engine (Core Logic)

> Luồng xử lý chính – trái tim của ứng dụng.

- [ ] **AutoBotService.java** (`service/`)
  - [ ] Enum `BotState`: `IDLE`, `RUNNING`, `PAUSED`, `STOPPED`
  - [ ] Chạy trên background thread (`ExecutorService` hoặc `Thread`)
  - [ ] Vòng lặp chính:
    - [ ] Kiểm tra `BotState` → nếu PAUSED thì sleep, nếu STOPPED thì thoát
    - [ ] Gọi `Win32Api.findGameWindow()` → kiểm tra cửa sổ game còn sống không
    - [ ] Chụp ảnh cửa sổ game bằng `WindowCapture`
    - [ ] Duyệt từng `ClickTarget` đã enabled:
      - [ ] Nếu `useColorCheck`: so sánh pixel color tại (x,y) với `targetColorRGB` ± `tolerance`
      - [ ] Nếu khớp (hoặc không dùng color check): gửi `PostMessage` click
    - [ ] Áp dụng delay + random jitter
  - [ ] Đếm số trận đã xử lý (`matchCount`)
  - [ ] Đếm thời gian chạy (`elapsedTime`)
  - [ ] Callback/Listener để gửi log + cập nhật UI
  - [ ] Các method điều khiển: `start()`, `pause()`, `resume()`, `stop()`

- [ ] Commit: `feat: add auto bot service engine`

---

## ⌨️ Phase 4: Global Hotkeys

> Phím tắt toàn hệ thống – hoạt động ngay cả khi game đang active.

- [ ] **GlobalHotkeyService.java** (`service/`)
  - [ ] Đăng ký F9 = Start/Pause toggle
  - [ ] Đăng ký F10 = Stop
  - [ ] Chạy message loop trên thread riêng (`GetMessage` / `PeekMessage`)
  - [ ] Callback khi nhận `WM_HOTKEY`
  - [ ] Cleanup: `UnregisterHotKey` khi app tắt
  - [ ] Test thủ công: nhấn F9/F10 khi đang ở cửa sổ khác → xem log có ghi nhận không

- [ ] Commit: `feat: add global hotkey support (F9/F10)`

---

## 🎨 Phase 5: User Interface (Swing GUI)

> Giao diện người dùng – Dark Theme hiện đại.

- [ ] **MainFrame.java** (`ui/`)
  - [ ] Layout tổng thể (BorderLayout / GridBagLayout)
  - [ ] **Dashboard Panel** (trên cùng):
    - [ ] LED trạng thái bot (IDLE=xám, RUNNING=xanh lá, PAUSED=vàng)
    - [ ] Label trạng thái cửa sổ game
    - [ ] Nút ▶ Bắt đầu / ⏸ Tạm dừng / ⏹ Dừng
    - [ ] Đồng hồ đếm thời gian
    - [ ] Bộ đếm số trận
  - [ ] **Targets Panel** (giữa):
    - [ ] JTable: Tên | X | Y | Màu | Delay | Bật/Tắt
    - [ ] Nút: Thêm / Sửa / Xóa
  - [ ] **Settings Panel** (phải hoặc tab):
    - [ ] TextField: Tên cửa sổ game
    - [ ] Checkbox: Bật kiểm tra màu sắc
    - [ ] Spinner: Loop delay, Jitter, Max matches
  - [ ] **Log Panel** (dưới cùng):
    - [ ] JTextArea scrollable, auto-scroll
    - [ ] Nút xóa log
  - [ ] **Status Bar** (cuối):
    - [ ] Hiển thị phím tắt F9/F10
  - [ ] Kết nối sự kiện UI ↔ AutoBotService

- [ ] **CoordinatePickerDialog.java** (`ui/`)
  - [ ] Chụp ảnh cửa sổ game hiển thị lên JLabel (có scroll nếu ảnh lớn)
  - [ ] Click vào ảnh → lấy tọa độ (x, y) tương đối
  - [ ] Hiển thị preview: tọa độ + ô màu pixel
  - [ ] Nút Xác nhận / Hủy
  - [ ] Trả về `ClickTarget` cho MainFrame

- [ ] Commit: `feat: add main GUI with dark theme`

---

## 🚀 Phase 6: Entry Point & Integration

> Kết nối tất cả lại và chạy thử.

- [ ] **App.java** (root package)
  - [ ] `main()`: thiết lập FlatLaf Dark Theme
  - [ ] Khởi tạo ConfigManager → load config
  - [ ] Khởi tạo AutoBotService
  - [ ] Khởi tạo GlobalHotkeyService
  - [ ] Khởi chạy MainFrame
  - [ ] Shutdown hook: dọn dẹp tài nguyên khi tắt app

- [ ] Build & chạy thử:
  - [ ] `mvn clean package` → không lỗi
  - [ ] `java -jar target/fco-auto-glxh-1.0.0-shaded.jar` → app hiển thị
  - [ ] Test tìm cửa sổ game (nếu game đang mở)
  - [ ] Test lấy tọa độ bằng CoordinatePickerDialog
  - [ ] Test PostMessage click
  - [ ] Test phím tắt F9/F10
  - [ ] Test lưu/đọc config.json

- [ ] Commit: `feat: add app entry point and integrate all components`

---

## 🧹 Phase 7: Polish & Release

- [ ] Viết `README.md` chi tiết (hướng dẫn cài đặt, sử dụng, screenshot)
- [ ] Thêm file `.gitignore` bổ sung (nếu cần)
- [ ] Refactor code, thêm comment/javadoc
- [ ] Xử lý edge case (game bị đóng giữa chừng, mất cửa sổ,...)
- [ ] Tạo release JAR trên GitHub (tag v1.0.0)
- [ ] Publish repo lên GitHub (Public/Private)

---

## 📝 Ghi chú & Quyết định

| Ngày | Ghi chú |
|---|---|
| 2026-08-06 | Khởi tạo dự án, chọn cơ chế PostMessage thay vì Robot để chạy nền |
| 2026-08-06 | Repo tạo tại `C:\Users\Hieu\Documents\GitHub\fco-auto-glxh` |
| 2026-08-06 | Hieu sẽ tự code là chính, AI Agent hỗ trợ khi được yêu cầu |

---

## ❓ Câu hỏi mở / Cần thống nhất

- [ ] Tên cửa sổ game FCO chính xác là gì? (Cần mở game lên kiểm tra title bar)
- [ ] Các nút cần click trong GLXH cụ thể là những nút nào? (Tiếp tục, Tìm trận, Đã sẵn sàng,...?)
- [ ] Có cần hỗ trợ nhiều độ phân giải màn hình không? (1920x1080, 1366x768,...)
- [ ] Có muốn thêm tính năng tự động tắt bot sau X trận không?
- [ ] Có muốn thêm âm thanh thông báo khi bot gặp lỗi không?
