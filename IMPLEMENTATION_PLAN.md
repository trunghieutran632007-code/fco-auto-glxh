# Implementation Plan - FC Online Auto GLXH (Giả Lập Xếp Hạng) App in Java

Tạo một ứng dụng Java Desktop chuyên nghiệp với giao diện hiện đại (Dark Theme GUI) giúp tự động hóa việc đá chế độ **Giả Lập Xếp Hạng (GLXH)** trong **EA Sports FC Online (FCO)**.

## Cơ chế cốt lõi: **PostMessage (Background Window Messaging)**

Ứng dụng sử dụng Windows API `PostMessage` qua **JNA** để gửi lệnh click chuột **trực tiếp đến cửa sổ game FCO** mà **không cần game ở foreground**. Người dùng có thể thoải mái dùng máy tính (lướt web, xem phim,...) trong khi bot chạy ngầm.

### Luồng hoạt động chính:
1. **Tìm cửa sổ game FCO** bằng `FindWindow` / `EnumWindows` (tìm theo tên cửa sổ).
2. **Chụp ảnh cửa sổ game** bằng `PrintWindow` API để đọc pixel **ngay trên cửa sổ game** (không cần game hiển thị trên màn hình).
3. **Kiểm tra màu sắc pixel** tại tọa độ nút bấm → nếu khớp màu nút (VD: "Tiếp tục" màu xanh lá) thì gửi click.
4. **Gửi click chuột** qua `PostMessage(hwnd, WM_LBUTTONDOWN/UP, ...)` với tọa độ tương đối trong cửa sổ game.
5. **Lặp lại** chu kỳ trên với độ trễ ngẫu nhiên (human-like jitter) cho đến khi người dùng dừng bot.

### Ưu điểm so với Robot:
- ✅ **Không chiếm chuột** – con trỏ chuột của bạn hoàn toàn tự do
- ✅ **Chạy nền** – dùng app khác thoải mái, chỉ cần không minimize game
- ✅ **Không cần game ở foreground** – cửa sổ game có thể bị che bởi cửa sổ khác
- ✅ **Tọa độ tương đối** – tọa độ click dựa trên cửa sổ game, không phải màn hình → không bị lệch khi di chuyển cửa sổ

---

## Yêu cầu khi sử dụng

> **IMPORTANT:**
> 1. Cửa sổ game FC Online **không được minimize** (nhấn nút "—"). Có thể bị che bởi cửa sổ khác nhưng phải còn "sống".
> 2. Nên chạy ứng dụng với quyền **Administrator** nếu game FCO cũng chạy dưới quyền Admin.
> 3. Game nên ở chế độ **Cửa sổ (Windowed)** hoặc **Borderless Windowed** để `PrintWindow` chụp ảnh chính xác nhất.

---

## Proposed Changes

Tạo dự án Maven Java tại thư mục:
`C:\Users\Hieu\Documents\GitHub\fco-auto-glxh`

---

### [Maven Infrastructure]

#### [NEW] pom.xml
- Cấu hình Maven với Java 21+
- Dependencies:
  - **JNA** (`net.java.dev.jna:jna` + `jna-platform`) – Gọi Windows API (`FindWindow`, `PostMessage`, `PrintWindow`, `RegisterHotKey`)
  - **FlatLaf** (`com.formdev:flatlaf`) – Giao diện Dark Theme hiện đại cho Swing
  - **Gson** (`com.google.code.gson:gson`) – Lưu/đọc cấu hình JSON
- Plugin `maven-shade-plugin` để build ra file JAR chạy độc lập (fat JAR)

---

### [Windows API Layer – JNA Wrapper]

#### [NEW] Win32Api.java
- Lớp wrapper gọi các Windows API cần thiết qua JNA:
  - `FindWindow(className, windowName)` – Tìm handle (HWND) của cửa sổ game FCO
  - `PostMessage(hwnd, WM_LBUTTONDOWN/UP, wParam, lParam)` – Gửi click chuột đến cửa sổ game
  - `PrintWindow(hwnd, hdcBlt, flags)` – Chụp ảnh cửa sổ game thành `BufferedImage` để đọc pixel
  - `GetWindowRect(hwnd, rect)` – Lấy kích thước cửa sổ game
  - `GetClientRect(hwnd, rect)` – Lấy kích thước vùng client (vùng hiển thị game thực tế)
  - `IsWindow(hwnd)` – Kiểm tra cửa sổ game còn tồn tại không
  - `RegisterHotKey / UnregisterHotKey` – Đăng ký phím tắt toàn hệ thống

#### [NEW] WindowCapture.java
- Sử dụng `PrintWindow` + GDI (`CreateCompatibleDC`, `CreateCompatibleBitmap`, `BitBlt`) để chụp nội dung cửa sổ game thành `BufferedImage`
- Cho phép đọc màu pixel tại bất kỳ tọa độ nào trong cửa sổ game mà **không cần game ở foreground**

---

### [Core Models]

#### [NEW] ClickTarget.java
- Model lưu thông tin một vị trí bấm:
  - `name` – Tên nút (VD: "Tiếp tục", "Tìm trận", "Đã sẵn sàng")
  - `x, y` – Tọa độ **tương đối trong cửa sổ game** (không phải tọa độ màn hình)
  - `targetColorRGB` – Màu sắc kỳ vọng tại vị trí nút (để kiểm tra trước khi click)
  - `colorTolerance` – Độ chênh lệch màu cho phép (0–255)
  - `delayMs` – Khoảng thời gian chờ sau khi click (ms)
  - `enabled` – Bật/tắt từng điểm click

#### [NEW] AppConfig.java
- Model cấu hình tổng thể:
  - `gameWindowTitle` – Tên cửa sổ game FCO (mặc định: `"FC ONLINE"`)
  - `targets` – Danh sách `ClickTarget`
  - `loopDelayMs` – Thời gian chờ giữa mỗi vòng lặp kiểm tra
  - `jitterMs` – Độ ngẫu nhiên thời gian (±ms) để giả lập hành vi người
  - `useColorCheck` – Bật/tắt kiểm tra màu sắc trước khi click
  - `maxMatches` – Giới hạn số trận tự động (0 = vô hạn)

---

### [Config & Services]

#### [NEW] ConfigManager.java
- Đọc/ghi file `config.json` bằng Gson
- Tự động tạo cấu hình mặc định nếu chưa có file config
- Lưu cấu hình bên cạnh file JAR để tiện dùng lại

#### [NEW] AutoBotService.java
- Luồng chính của bot chạy trên background thread:
  1. Tìm cửa sổ game FCO bằng `FindWindow`
  2. Vòng lặp chính:
     - Chụp ảnh cửa sổ game bằng `WindowCapture`
     - Duyệt qua từng `ClickTarget` đã bật
     - Nếu `useColorCheck` = true → kiểm tra màu pixel tại tọa độ → khớp thì click
     - Nếu `useColorCheck` = false → click trực tiếp theo chu kỳ
     - Gửi `PostMessage(WM_LBUTTONDOWN)` + `PostMessage(WM_LBUTTONUP)` đến cửa sổ game
     - Chờ `delayMs ± jitterMs` rồi tiếp tục
  3. Đếm số trận đã xử lý, thời gian chạy
  4. Ghi log mọi hành động
- Hỗ trợ trạng thái: `IDLE`, `RUNNING`, `PAUSED`, `STOPPED`

#### [NEW] GlobalHotkeyService.java
- Đăng ký phím tắt toàn hệ thống qua `RegisterHotKey` (JNA):
  - **F9** – Bắt đầu / Tạm dừng bot
  - **F10** – Dừng hẳn bot
- Chạy trên thread riêng để lắng nghe `WM_HOTKEY` message
- Hoạt động ngay cả khi game FCO hoặc app khác đang active

---

### [User Interface (Swing GUI)]

#### [NEW] CoordinatePickerDialog.java
- Dialog cho phép người dùng lấy tọa độ nút bấm từ cửa sổ game:
  - Chụp ảnh cửa sổ game FCO bằng `PrintWindow` và hiển thị lên dialog
  - Người dùng **click vào ảnh chụp** để chọn tọa độ nút (không cần click vào game thật)
  - Tự động lấy màu pixel tại vị trí được chọn
  - Hiển thị preview tọa độ + màu sắc trước khi xác nhận
- **Ưu điểm**: Không cần overlay toàn màn hình, không ảnh hưởng đến game đang chạy

#### [NEW] MainFrame.java
- Giao diện chính với Dark Theme (FlatLaf):
  - **Panel Dashboard:**
    - Trạng thái bot (IDLE / RUNNING / PAUSED) với indicator LED màu
    - Trạng thái cửa sổ game (Tìm thấy / Không tìm thấy) với tên cửa sổ
    - Nút **▶ Bắt đầu** / **⏸ Tạm dừng** / **⏹ Dừng**
    - Đồng hồ đếm thời gian chạy
    - Bộ đếm số trận đã xử lý
  - **Panel Targets (Bảng tọa độ):**
    - JTable hiển thị danh sách các `ClickTarget` (Tên, X, Y, Màu, Delay, Bật/Tắt)
    - Nút **➕ Thêm** (mở CoordinatePickerDialog) / **✏️ Sửa** / **🗑️ Xóa**
  - **Panel Settings (Cài đặt):**
    - Tên cửa sổ game (có thể chỉnh sửa)
    - Bật/tắt kiểm tra màu sắc pixel
    - Thời gian chờ giữa các vòng lặp
    - Độ ngẫu nhiên hóa thời gian (jitter)
    - Giới hạn số trận
  - **Panel Log:**
    - JTextArea hiển thị log realtime (timestamp + hành động)
    - Nút xóa log
  - **Thanh trạng thái:**
    - Phím tắt: F9 = Start/Pause, F10 = Stop

#### [NEW] App.java
- Điểm khởi chạy ứng dụng:
  - Thiết lập FlatLaf Dark Theme
  - Khởi tạo ConfigManager, AutoBotService, GlobalHotkeyService
  - Khởi chạy MainFrame

---

## Cấu trúc thư mục dự án

```
fco-auto-glxh/
├── pom.xml
└── src/main/java/com/fco/autoglxh/
    ├── App.java                          # Entry point
    ├── config/
    │   └── ConfigManager.java            # Đọc/ghi config.json
    ├── model/
    │   ├── AppConfig.java                # Model cấu hình
    │   └── ClickTarget.java              # Model điểm click
    ├── native_api/
    │   ├── Win32Api.java                 # JNA wrapper Windows API
    │   └── WindowCapture.java            # Chụp ảnh cửa sổ game
    ├── service/
    │   ├── AutoBotService.java           # Engine chính
    │   └── GlobalHotkeyService.java      # Phím tắt F9/F10
    └── ui/
        ├── CoordinatePickerDialog.java   # Lấy tọa độ từ ảnh chụp game
        └── MainFrame.java               # Giao diện chính
```

---

## Verification Plan

### Build Verification
```bash
cd fco-auto-glxh
mvn clean package
```

### Manual Verification
1. Chạy `java -jar target/fco-auto-glxh-1.0.0-shaded.jar`
2. Kiểm tra giao diện Dark Theme hiển thị đúng
3. Kiểm tra tìm cửa sổ game FCO (nếu game đang mở)
4. Kiểm tra CoordinatePickerDialog chụp ảnh cửa sổ game
5. Kiểm tra PostMessage gửi click đến cửa sổ game
6. Kiểm tra phím tắt F9/F10 hoạt động toàn hệ thống
7. Kiểm tra lưu/đọc config.json
