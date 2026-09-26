package com.fco.autoglxh.ui;

import com.fco.autoglxh.model.AppConfig;
import com.fco.autoglxh.model.ClickTarget;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * TargetsPanel - Bảng danh sách {@link ClickTarget} + thao tác Thêm/Sửa/Xóa.
 *
 * Cột: Bật (checkbox, sửa trực tiếp) | Tên | X | Y | Màu (ô swatch + hex) | Delay (ms).
 * Bảng chia sẻ trực tiếp {@code config.getTargets()} (cùng list mà bot đọc mỗi vòng).
 *
 * Persist: sau mỗi thay đổi gọi {@code onSave} (do MainFrame cung cấp = commit settings + ghi config.json)
 * và {@code logger} để ghi nhật ký.
 */
public class TargetsPanel extends JPanel {

    private final AppConfig config;
    private final Runnable onSave;
    private final Consumer<String> logger;

    private final TargetTableModel model;
    private final JTable table;
    private final JButton editBtn = new JButton("✏️ Sửa");
    private final JButton deleteBtn = new JButton("🗑️ Xóa");

    public TargetsPanel(AppConfig config, Runnable onSave, Consumer<String> logger) {
        super(new BorderLayout(0, 6));
        this.config = config;
        this.onSave = onSave;
        this.logger = logger;

        model = new TargetTableModel(config.getTargets(), this::onInlineToggle);
        table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        table.getSelectionModel().addListSelectionListener(e -> updateButtons());
        configureColumns();

        add(buildToolbar(), BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        updateButtons();
    }

    private JPanel buildToolbar() {
        JButton addBtn = new JButton("➕ Thêm");
        addBtn.addActionListener(e -> onAdd());
        editBtn.addActionListener(e -> onEdit());
        deleteBtn.addActionListener(e -> onDelete());

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        bar.add(addBtn);
        bar.add(editBtn);
        bar.add(deleteBtn);
        return bar;
    }

    private void configureColumns() {
        int[] widths = {48, 170, 60, 60, 120, 90};
        for (int i = 0; i < widths.length && i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.getColumnModel().getColumn(4).setCellRenderer(new ColorCellRenderer());
    }

    private void updateButtons() {
        boolean selected = table.getSelectedRow() >= 0;
        editBtn.setEnabled(selected);
        deleteBtn.setEnabled(selected);
    }

    // ==================== Actions ====================

    private void onInlineToggle(int row) {
        ClickTarget t = config.getTargets().get(row);
        onSave.run();
        logger.accept((t.isEnabled() ? "Bật" : "Tắt") + " target \"" + t.getName() + "\".");
    }

    private void onAdd() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        TargetEditDialog dlg = new TargetEditDialog(owner, "Thêm mục tiêu", null);
        dlg.setVisible(true);
        if (dlg.isConfirmed()) {
            ClickTarget t = dlg.getResult();
            List<ClickTarget> targets = config.getTargets();
            targets.add(t);
            int idx = targets.size() - 1;
            model.fireTableRowsInserted(idx, idx);
            onSave.run();
            logger.accept("Đã thêm target \"" + t.getName() + "\" @ (" + t.getX() + "," + t.getY() + ").");
        }
    }

    private void onEdit() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }
        Window owner = SwingUtilities.getWindowAncestor(this);
        TargetEditDialog dlg = new TargetEditDialog(owner, "Sửa mục tiêu", config.getTargets().get(row));
        dlg.setVisible(true);
        if (dlg.isConfirmed()) {
            ClickTarget updated = dlg.getResult();
            config.getTargets().set(row, updated);
            model.fireTableRowsUpdated(row, row);
            onSave.run();
            logger.accept("Đã sửa target \"" + updated.getName() + "\".");
        }
    }

    private void onDelete() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }
        ClickTarget t = config.getTargets().get(row);
        int ans = JOptionPane.showConfirmDialog(this,
                "Xóa target \"" + t.getName() + "\"?", "Xác nhận xóa",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ans == JOptionPane.YES_OPTION) {
            config.getTargets().remove(row);
            model.fireTableRowsDeleted(row, row);
            onSave.run();
            logger.accept("Đã xóa target \"" + t.getName() + "\".");
        }
    }

    // ==================== Table model ====================

    /** Model chia sẻ trực tiếp list targets của config. Chỉ cột "Bật" sửa được inline. */
    private static class TargetTableModel extends AbstractTableModel {
        private final String[] cols = {"Bật", "Tên", "X", "Y", "Màu", "Delay (ms)"};
        private final List<ClickTarget> targets;
        private final IntConsumer onToggle;

        TargetTableModel(List<ClickTarget> targets, IntConsumer onToggle) {
            this.targets = targets;
            this.onToggle = onToggle;
        }

        @Override
        public int getRowCount() {
            return targets.size();
        }

        @Override
        public int getColumnCount() {
            return cols.length;
        }

        @Override
        public String getColumnName(int c) {
            return cols[c];
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return switch (c) {
                case 0 -> Boolean.class;
                case 2, 3 -> Integer.class;
                case 4 -> Color.class;
                case 5 -> Long.class;
                default -> String.class;
            };
        }

        @Override
        public boolean isCellEditable(int r, int c) {
            return c == 0;
        }

        @Override
        public Object getValueAt(int r, int c) {
            ClickTarget t = targets.get(r);
            return switch (c) {
                case 0 -> t.isEnabled();
                case 1 -> t.getName();
                case 2 -> t.getX();
                case 3 -> t.getY();
                case 4 -> t.getTargetColor();
                case 5 -> t.getDelayMs();
                default -> "";
            };
        }

        @Override
        public void setValueAt(Object val, int r, int c) {
            if (c == 0 && val instanceof Boolean b) {
                targets.get(r).setEnabled(b);
                fireTableCellUpdated(r, c);
                onToggle.accept(r);
            }
        }
    }

    // ==================== Color cell renderer ====================

    /** Vẽ ô màu (background = màu target) + hiển thị mã hex, foreground tương phản. */
    private static class ColorCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (value instanceof Color color) {
                setBackground(color);
                setText(String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue()));
                double lum = 0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue();
                setForeground(lum > 140 ? Color.BLACK : Color.WHITE);
                setHorizontalAlignment(CENTER);
                setOpaque(true);
            }
            return this;
        }
    }
}
