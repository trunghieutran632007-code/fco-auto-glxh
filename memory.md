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
- [x] Commit: `chore: init maven project structure` ✅ (2687e54)

---

## 🏗️ Phase 1: Windows API Layer (JNA Wrapper)

> Tầng nền tảng – gọi Windows API qua JNA. Các tầng trên sẽ phụ thuộc vào đây.

- [x] **Win32Api.java** (`native_api/`)
  - [x] Khai báo JNA interface cho `User32` (FindWindow, PostMessage, GetClientRect, IsWindow, RegisterHotKey, UnregisterHotKey)
  - [x] Khai báo JNA interface mở rộng `User32Ex` cho `PrintWindow`
  - [x] Khai báo các hằng số Windows (WM_LBUTTONDOWN, WM_LBUTTONUP, MK_LBUTTON, PW_RENDERFULLCONTENT,...)
  - [x] Viết helper method: `findGameWindow(title)` → trả về HWND
  - [x] Viết helper method: `postClick(hwnd, x, y)` → gửi WM_LBUTTONDOWN + WM_LBUTTONUP
  - [x] Test thủ công: tìm cửa sổ Notepad, gửi click → phản hồi OK (click gửi thành công qua `ManualTest`; caret không nhảy vì Notepad dùng child EDIT control — không phải lỗi code, sẽ verify với nút FCO ở phase sau)

- [x] **WindowCapture.java** (`native_api/`)
  - [x] Chụp ảnh cửa sổ bằng `PrintWindow` + GDI → trả về `BufferedImage`
  - [x] Method `getPixelColor(hwnd, x, y)` → trả về `Color`
  - [x] Method `captureWindow(hwnd)` → trả về `BufferedImage`
  - [x] Method `isColorMatch()` (2 overloads: từ hwnd hoặc từ BufferedImage có sẵn)
  - [x] Test thủ công: chụp cửa sổ Notepad, lưu ra file `.png` kiểm tra ✅ ảnh OK (target/notepad-capture.png)

- [x] Commit: `feat: add Win32 API wrapper and window capture` ✅ (f95cf6e)
- [x] Commit follow-up: fix #1/#5/#6 (commit `94edced`) + `ManualTest.java` + pom `${exec.mainClass}` (commit này)

---

## 📦 Phase 2: Core Models & Config

> Định nghĩa dữ liệu và quản lý cấu hình.

- [x] **ClickTarget.java** (`model/`)
  - [x] Các trường: `name`, `x`, `y`, `targetColorRGB` (int[3]), `colorTolerance`, `delayMs`, `enabled`
  - [x] Constructor, getters, setters
  - [x] Method `toString()` cho debug/log
  - [x] Helper: `getTargetColor()` / `setTargetColor(Color)` — bridge sang `java.awt.Color` cho `WindowCapture.isColorMatch`

- [x] **AppConfig.java** (`model/`)
  - [x] Các trường: `gameWindowTitle`, `targets` (List<ClickTarget>), `loopDelayMs`, `jitterMs`, `useColorCheck`, `maxMatches`
  - [x] Giá trị mặc định hợp lý (gameWindowTitle = "FC ONLINE", loopDelayMs = 3000, jitterMs = 500, useColorCheck = true, maxMatches = 0 = vô hạn)

- [x] **ConfigManager.java** (`config/`)
  - [x] `load()` → đọc `config.json` bằng Gson, trả về `AppConfig`
  - [x] `save(AppConfig)` → ghi ra `config.json` (pretty-print, UTF-8)
  - [x] Tự tạo `config.json` mặc định nếu file chưa tồn tại
  - [x] Xác định đường dẫn config: cùng thư mục với file JAR (fallback working dir)
  - [x] Xử lý mềm: file rỗng/hỏng → trả default config (không crash)

- [x] Verify: `mvn clean compile` ✅ BUILD SUCCESS + smoke test jshell (round-trip load/save + helper Color) ✅

- [x] Commit: `feat: add models and config manager` ✅

---

## ⚙️ Phase 3: Bot Engine (Core Logic)

> Luồng xử lý chính – trái tim của ứng dụng.

- [x] **AutoBotService.java** (`service/`) + `BotState.java` + `BotListener.java`
  - [x] Enum `BotState`: `IDLE`, `RUNNING`, `PAUSED`, `STOPPED` (file riêng trong `service/`)
  - [x] Chạy trên background thread (dedicated daemon `Thread`, mỗi start() tạo thread mới)
  - [x] Vòng lặp chính:
    - [x] Kiểm tra `BotState` → nếu PAUSED thì sleep 100ms chunk, nếu STOPPED thì thoát
    - [x] Gọi `Win32Api.findGameWindow()` → kiểm tra cửa sổ game còn sống không (không crash khi mất)
    - [x] Chụp ảnh cửa sổ game bằng `WindowCapture` (1 lần/vòng, reuse cho mọi target)
    - [x] Duyệt từng `ClickTarget` đã enabled:
      - [x] Nếu `useColorCheck`: so sánh pixel color tại (x,y) với `targetColorRGB` ± `tolerance` (dùng `isColorMatch(BufferedImage,...)`)
      - [x] Nếu khớp (hoặc không dùng color check): gửi `PostMessage` click (`Win32Api.postClick`)
    - [x] Áp dụng delay + random jitter (per-target `delayMs ± jitterMs` + inter-cycle `loopDelayMs ± jitterMs`)
  - [x] Đếm số trận đã xử lý (`matchCount`) — **proxy**: đếm số VÒNG có ít nhất 1 click (bot không có tín hiệu "kết thúc trận" rõ ràng; có thể refine sau)
  - [x] Đếm thời gian chạy (`elapsedMs`) — loại trừ thời gian pause (activeMs tích lũy + runningSinceMs)
  - [x] Callback/Listener (`BotListener`: `onLog`, `onStateChange`, `onStatsUpdate`, `onGameWindowStatus`) — gọi trên worker thread, UI Phase 5 tự `invokeLater`
  - [x] Các method điều khiển: `start()`, `pause()`, `resume()`, `stop()` + `sleepInterruptible` (chunk 50ms, ngắt được)

- [x] Verify: `mvn clean compile` ✅ + smoke test jshell (start→pause→resume→stop, no-window path êm, elapsed trừ pause đúng, matchCount=0) ✅

- [x] Commit: `feat: add auto bot service engine` ✅

---

## ⌨️ Phase 4: Global Hotkeys

> Phím tắt toàn hệ thống – hoạt động ngay cả khi game đang active.

- [x] **GlobalHotkeyService.java** (`service/`) + `HotkeyListener.java`
  - [x] Đăng ký F9 = Start/Pause toggle (`HOTKEY_ID_START_PAUSE`, `VK_F9`, modifiers=0)
  - [x] Đăng ký F10 = Stop (`HOTKEY_ID_STOP`, `VK_F10`)
  - [x] Chạy message loop trên thread riêng — dùng **PeekMessage polling** (PM_REMOVE, 10ms) thay vì GetMessage blocking (shutdown race-free, không cần PostThreadMessage/thread-id)
  - [x] Callback khi nhận `WM_HOTKEY` — `HotkeyListener.onHotkeyPressed(int id)`, gọi trên worker thread (UI Phase 6 tự `invokeLater`)
  - [x] Cleanup: `UnregisterHotKey` trong `finally` của worker (BẮT BUỘC cùng thread đã register); `stop()` interrupt + `join(500)` chờ unregister xong
  - [x] Test thủ công: nhấn F9/F10 khi đang ở cửa sổ khác → xem log (xem phần verify)
  - [x] Không cần sửa `Win32Api.java` — JNA `User32` đã có sẵn `PeekMessage`/`GetMessage`/`PostThreadMessage` + `WinUser.MSG`; `Win32Api` đã có sẵn `registerHotKey`/`unregisterHotKey` + constants từ Phase 1

- [x] Verify: `mvn clean compile` ✅ + smoke test jshell (F9/F10 registered=true, worker chạy loop OK, stop sạch + unregister OK + flags reset) ✅
- [x] **Test thủ công F9/F10 thật (bấm phím)** ✅ — chạy `hotkey-test.jsh` (window 30s, bấm liên tục xen kẽ), listener fire: F9 = 44 lần, F10 = 43 lần, hoạt động cả khi cửa sổ khác đang active. **Lưu ý test**: output jshell hiển thị cho AI chứ KHÔNG realtime cho người dùng → khi test hotkey phải bấm liên tục cả window, đừng canh dòng "TEST BAT DAU"

- [x] Commit: `feat: add global hotkey support (F9/F10)` ✅

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
| 2026-08-06 | Review Phase 1 + fix #1 (makeLParam cast long), #5 (PrintWindow: PW_CLIENTONLY + PW_RENDERFULLCONTENT), #6 (perf getByteArray). Thêm `ManualTest.java` test Notepad qua EnumWindows. Pom: mainClass -> `${exec.mainClass}` (override bằng -Dexec.mainClass). PowerShell cần `--%` cho tham số -D. Test thủ công: capture PNG ✅, click gửi OK (caret không nhảy do Notepad dùng child EDIT control). |
| 2026-08-08 | Hoàn thành Phase 2 (Core Models & Config). Thêm `model/ClickTarget.java`, `model/AppConfig.java`, `config/ConfigManager.java`. ClickTarget có helper `getTargetColor()/setTargetColor(Color)` bridge sang `java.awt.Color` để dùng trực tiếp cho `WindowCapture.isColorMatch`. ConfigManager: Gson pretty-print UTF-8, tự tạo default nếu thiếu, resolve path = thư mục JAR (fallback working dir), xử lý mềm file hỏng → trả default. Verify: `mvn clean compile` ✅ + smoke test jshell round-trip (load/save/target color) ✅. Chú ý: console Windows in tiếng Việt bị mojibake (codepage không UTF-8) — chỉ là hiển thị test, code/console thực tế khi chạy GUI sẽ OK. |
| 2026-08-08 | Hoàn thành Phase 3 (Bot Engine). Thêm `service/AutoBotService.java`, `service/BotState.java` (enum), `service/BotListener.java` (interface). Worker = daemon Thread, state volatile, sleep interruptible (chunk 50ms) để stop/pause phản hồi nhanh. Capture 1 lần/vòng rồi reuse qua `isColorMatch(BufferedImage,...)`. `matchCount` = PROXY: đếm số vòng có ≥1 click (chưa có tín hiệu "kết thúc trận"; open question để refine). elapsedMs loại trừ thời gian pause (activeMs + runningSinceMs). Listener 4 callback, gọi trên worker thread (UI Phase 5 tự `invokeLater`). Verify: `mvn clean compile` ✅ + smoke test jshell start→pause→resume→stop với window giả (no-window path êm, không crash, state transitions `[RUNNING,PAUSED,RUNNING,STOPPED]`, elapsed trừ pause đúng = 1907ms cho 1200+600 run, matchCount=0) ✅. Lưu ý: JNA native-access WARNING (Java 21+ restricted method) là cảnh báo, không lỗi — nếu muốn tắt thì thêm JVM arg `--enable-native-access=ALL-UNNAMED`. |
| 2026-08-08 | Hoàn thành Phase 4 (Global Hotkeys). Thêm `service/GlobalHotkeyService.java`, `service/HotkeyListener.java`. Dùng PeekMessage polling (PM_REMOVE, 10ms) thay vì GetMessage blocking → shutdown race-free (không cần PostThreadMessage + thread-id). Register/unregister/PeekMessage đều chạy trên cùng worker thread (bắt buộc cho thread hotkey hwnd=NULL). `stop()` interrupt + join(500) chờ unregister xong trong `finally`. KHÔNG sửa `Win32Api.java` (JNA User32 đã có sẵn PeekMessage/GetMessage/PostThreadMessage + WinUser.MSG; Win32Api đã có registerHotKey/unregisterHotKey + WM_HOTKEY/HOTKEY_ID_*/VK_F9/VK_F10 từ Phase 1). Verify: `mvn clean compile` ✅ + smoke test jshell (F9/F10 registered=true, worker loop OK, stop sạch + unregister + flags reset) ✅. **Còn 1 test thủ công F9/F10 thật (bấm phím → listener fire) chưa chạy** — cần Hieu chạy `hotkey-test.jsh` và bấm F9/F10 trong 4s. |
| 2026-08-08 | Test thủ công F9/F10 thật ✅ PASS. Chạy `hotkey-test.jsh` qua jshell (classpath = `target/classes` + JNA jars từ `mvn dependency:build-classpath`), window 30s, bấm liên tục xen kẽ → F9 fire 44 lần, F10 fire 43 lần, chạy nền OK cả khi cửa sổ khác active. **Bài học test hotkey**: output jshell hiện cho AI chứ không realtime cho người dùng → phải bấm liên tục suốt window, đừng canh dòng log. Phase 4 hoàn tất + đã commit. Bước tiếp theo: Phase 5 (UI) — bắt đầu từ lát cắt 5a (App.java thật + MainFrame khung: Dashboard + Log + nút ▶/⏸/⏹ + nối AutoBotService & F9/F10) để có app chạy được và test end-to-end sớm. |

---

## ❓ Câu hỏi mở / Cần thống nhất

- [ ] Tên cửa sổ game FCO chính xác là gì? (Cần mở game lên kiểm tra title bar)
- [ ] Các nút cần click trong GLXH cụ thể là những nút nào? (Tiếp tục, Tìm trận, Đã sẵn sàng,...?)
- [ ] Có cần hỗ trợ nhiều độ phân giải màn hình không? (1920x1080, 1366x768,...)
- [ ] Có muốn thêm tính năng tự động tắt bot sau X trận không?
- [ ] Có muốn thêm âm thanh thông báo khi bot gặp lỗi không?
