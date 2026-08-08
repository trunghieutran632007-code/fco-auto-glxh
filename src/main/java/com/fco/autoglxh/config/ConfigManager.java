package com.fco.autoglxh.config;

import com.fco.autoglxh.model.AppConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * ConfigManager - Đọc/ghi cấu hình ứng dụng ra file {@code config.json} bằng Gson.
 *
 * Trách nhiệm:
 * - Xác định đường dẫn file config (cùng thư mục với file JAR khi chạy production,
 *   hoặc thư mục code source khi chạy từ IDE).
 * - {@link #load()}: đọc config.json → trả về {@link AppConfig}. Nếu file chưa tồn tại
 *   thì tự tạo cấu hình mặc định và ghi ra disk rồi trả về.
 * - {@link #save(AppConfig)}: ghi AppConfig ra config.json (pretty-print để dễ sửa tay).
 *
 * Xử lý lỗi mềm: nếu file tồn tại nhưng bị hỏng (parse lỗi), {@code load()} trả về
 * cấu hình mặc định thay vì ném exception, giúp app vẫn khởi chạy được.
 */
public class ConfigManager {

    /** Tên file cấu hình. */
    public static final String CONFIG_FILE_NAME = "config.json";

    /** Gson instance dùng chung, có pretty-printing. */
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private final Path configPath;

    // ==================== Constructors ====================

    /**
     * Constructor mặc định - tự xác định đường dẫn config.json
     * cùng thư mục với file JAR (hoặc code source khi chạy từ IDE).
     */
    public ConfigManager() {
        this(resolveDefaultConfigPath());
    }

    /**
     * Constructor cho test / chỉ định đường dẫn file config cụ thể.
     *
     * @param configPath Đường dẫn tuyệt đối đến config.json
     */
    public ConfigManager(Path configPath) {
        this.configPath = configPath;
    }

    // ==================== Load / Save ====================

    /**
     * Đọc cấu hình từ config.json.
     * Nếu file chưa tồn tại → tạo cấu hình mặc định, ghi ra disk rồi trả về.
     * Nếu file hỏng (parse lỗi) → trả về cấu hình mặc định (không ghi đè file cũ).
     *
     * @return AppConfig đọc được (hoặc mặc định)
     */
    public AppConfig load() {
        if (!Files.exists(configPath)) {
            System.out.println("[Config] Chưa có config.json → tạo mặc định tại: " + configPath);
            AppConfig def = createDefaultConfig();
            save(def);
            return def;
        }

        try {
            String json = Files.readString(configPath, StandardCharsets.UTF_8);
            AppConfig config = GSON.fromJson(json, AppConfig.class);
            if (config == null) {
                System.err.println("[Config] config.json rỗng/không hợp lệ → dùng mặc định.");
                return createDefaultConfig();
            }
            System.out.println("[Config] Đã đọc config.json: " + config);
            return config;
        } catch (IOException e) {
            System.err.println("[Config] Lỗi đọc config.json: " + e.getMessage() + " → dùng mặc định.");
            return createDefaultConfig();
        } catch (Exception e) {
            System.err.println("[Config] config.json hỏng (parse lỗi): " + e.getMessage() + " → dùng mặc định.");
            return createDefaultConfig();
        }
    }

    /**
     * Ghi cấu hình ra config.json (pretty-print, UTF-8).
     *
     * @param config Cấu hình cần ghi
     */
    public void save(AppConfig config) {
        try {
            // Tạo thư mục cha nếu chưa có
            Path parent = configPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            String json = GSON.toJson(config);
            Files.writeString(configPath, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[Config] Lỗi ghi config.json: " + e.getMessage());
        }
    }

    /**
     * Tạo cấu hình mặc định (giá trị hợp lý, danh sách targets rỗng).
     *
     * @return AppConfig mặc định
     */
    public AppConfig createDefaultConfig() {
        return new AppConfig();
    }

    /**
     * @return Đường dẫn tuyệt đối đến file config.json
     */
    public Path getConfigPath() {
        return configPath;
    }

    // ==================== Path Resolution ====================

    /**
     * Xác định đường dẫn mặc định cho config.json.
     *
     * Chiến lược:
     * - Lấy vị trí code source (JAR hoặc thư mục classes).
     * - Nếu là file JAR → dùng thư mục chứa JAR đó.
     * - Nếu là thư mục (chạy từ IDE) → dùng chính thư mục đó.
     * - Nếu không xác định được → fallback về thư mục làm việc hiện tại.
     *
     * @return Đường dẫn đến config.json
     */
    private static Path resolveDefaultConfigPath() {
        Path baseDir;
        try {
            URL url = ConfigManager.class.getProtectionDomain().getCodeSource().getLocation();
            if (url != null) {
                Path codeSource = Paths.get(url.toURI());
                // codeSource là file .jar → lấy thư mục cha; là thư mục → giữ nguyên
                baseDir = Files.isDirectory(codeSource) ? codeSource : codeSource.getParent();
            } else {
                baseDir = Paths.get(".").toAbsolutePath();
            }
        } catch (Exception e) {
            System.err.println("[Config] Không xác định được thư mục JAR, dùng working dir. Lý do: " + e.getMessage());
            baseDir = Paths.get(".").toAbsolutePath();
        }
        return baseDir.resolve(CONFIG_FILE_NAME);
    }
}
