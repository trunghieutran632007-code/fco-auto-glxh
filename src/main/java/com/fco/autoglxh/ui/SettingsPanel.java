package com.fco.autoglxh.ui;

import com.fco.autoglxh.model.AppConfig;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.function.Consumer;

/**
 * SettingsPanel - Cấu hình chung của bot (bind với {@link AppConfig}).
 *
 * Các field không tự ghi vào config ngay; chúng được đẩy vào config qua
 * {@link #commitToConfig()} — được MainFrame gọi trong {@code saveAll()} trước khi
 * ghi ra config.json. Nhờ đó mọi lần lưu (kể cả khi thêm/sửa target) luôn dùng
 * giá trị settings mới nhất, tránh ghi đè bằng dữ liệu cũ.
 */
public class SettingsPanel extends JPanel {

    private final AppConfig config;

    private final JTextField titleField = new JTextField(18);
    private final JCheckBox colorCheckBox = new JCheckBox("Kiểm tra màu trước khi click");
    private final JSpinner loopDelaySpinner = new JSpinner(new SpinnerNumberModel(3000, 0, 3_600_000, 100));
    private final JSpinner jitterSpinner = new JSpinner(new SpinnerNumberModel(500, 0, 600_000, 50));
    private final JSpinner maxMatchesSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 1_000_000, 1));

    public SettingsPanel(AppConfig config, Runnable onSave, Consumer<String> logger) {
        this.config = config;
        buildUi(onSave, logger);
        loadFromConfig();
    }

    private void buildUi(Runnable onSave, Consumer<String> logger) {
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 6, 6, 6);
        gc.anchor = GridBagConstraints.WEST;

        int row = 0;
        addRow(gc, row++, "Tên cửa sổ game:", titleField);

        gc.gridx = 0;
        gc.gridy = row++;
        gc.gridwidth = 2;
        gc.fill = GridBagConstraints.NONE;
        gc.weightx = 0;
        add(colorCheckBox, gc);
        gc.gridwidth = 1;

        addRow(gc, row++, "Loop delay (ms):", loopDelaySpinner);
        addRow(gc, row++, "Jitter (±ms):", jitterSpinner);
        addRow(gc, row++, "Giới hạn số trận (0 = vô hạn):", maxMatchesSpinner);

        JButton saveBtn = new JButton("💾 Lưu cấu hình");
        saveBtn.addActionListener(e -> {
            onSave.run();
            logger.accept("Đã lưu cấu hình vào config.json.");
        });
        gc.gridx = 0;
        gc.gridy = row++;
        gc.gridwidth = 2;
        gc.insets = new Insets(14, 6, 6, 6);
        add(saveBtn, gc);

        // filler đẩy các field lên trên
        gc.gridx = 0;
        gc.gridy = row;
        gc.weighty = 1;
        gc.fill = GridBagConstraints.BOTH;
        add(Box.createGlue(), gc);
    }

    private void addRow(GridBagConstraints gc, int row, String label, JComponent field) {
        gc.gridx = 0;
        gc.gridy = row;
        gc.gridwidth = 1;
        gc.weightx = 0;
        gc.fill = GridBagConstraints.NONE;
        add(new JLabel(label), gc);
        gc.gridx = 1;
        gc.weightx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        add(field, gc);
    }

    private void loadFromConfig() {
        titleField.setText(config.getGameWindowTitle());
        colorCheckBox.setSelected(config.isUseColorCheck());
        loopDelaySpinner.setValue((int) Math.min(Integer.MAX_VALUE, config.getLoopDelayMs()));
        jitterSpinner.setValue((int) Math.min(Integer.MAX_VALUE, config.getJitterMs()));
        maxMatchesSpinner.setValue(config.getMaxMatches());
    }

    /** Ghi giá trị các field vào {@link AppConfig} (gọi trước khi persist ra disk). */
    public void commitToConfig() {
        config.setGameWindowTitle(titleField.getText().trim());
        config.setUseColorCheck(colorCheckBox.isSelected());
        config.setLoopDelayMs(((Number) loopDelaySpinner.getValue()).longValue());
        config.setJitterMs(((Number) jitterSpinner.getValue()).longValue());
        config.setMaxMatches(((Number) maxMatchesSpinner.getValue()).intValue());
    }
}
