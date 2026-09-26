package com.fco.autoglxh.ui;

import com.fco.autoglxh.model.ClickTarget;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;

/**
 * TargetEditDialog - Dialog modal thêm/sửa một {@link ClickTarget}.
 *
 * 5b: nhập tọa độ X/Y bằng tay + chọn màu bằng {@link JColorChooser}.
 * 5c sẽ bổ sung CoordinatePickerDialog để lấy tọa độ + màu trực tiếp từ ảnh chụp game.
 *
 * Cách dùng:
 * <pre>
 *   TargetEditDialog dlg = new TargetEditDialog(owner, "Thêm mục tiêu", existingOrNull);
 *   dlg.setVisible(true);           // block cho tới khi đóng
 *   if (dlg.isConfirmed()) { ClickTarget t = dlg.getResult(); ... }
 * </pre>
 */
public class TargetEditDialog extends JDialog {

    private final JTextField nameField = new JTextField(16);
    private final JSpinner xSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 100_000, 1));
    private final JSpinner ySpinner = new JSpinner(new SpinnerNumberModel(0, 0, 100_000, 1));
    private final JSpinner toleranceSpinner = new JSpinner(new SpinnerNumberModel(10, 0, 255, 1));
    private final JSpinner delaySpinner = new JSpinner(new SpinnerNumberModel(500, 0, 600_000, 50));
    private final JCheckBox enabledBox = new JCheckBox("Bật target này", true);
    private final JPanel colorSwatch = new JPanel();

    private Color chosenColor = Color.BLACK;
    private boolean confirmed = false;

    public TargetEditDialog(Window owner, String title, ClickTarget source) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        buildUi();
        if (source != null) {
            prefill(source);
        }
        updateSwatch();
        pack();
        setLocationRelativeTo(owner);
    }

    private void buildUi() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 6, 12));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 6, 6, 6);
        gc.anchor = GridBagConstraints.WEST;

        int row = 0;
        addRow(form, gc, row++, "Tên:", nameField);
        addRow(form, gc, row++, "X:", xSpinner);
        addRow(form, gc, row++, "Y:", ySpinner);

        colorSwatch.setPreferredSize(new Dimension(44, 22));
        colorSwatch.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        JButton pickColor = new JButton("Chọn màu...");
        pickColor.addActionListener(e -> {
            Color c = JColorChooser.showDialog(this, "Chọn màu mục tiêu", chosenColor);
            if (c != null) {
                chosenColor = c;
                updateSwatch();
            }
        });
        JPanel colorRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        colorRow.add(colorSwatch);
        colorRow.add(pickColor);
        addRow(form, gc, row++, "Màu:", colorRow);

        addRow(form, gc, row++, "Tolerance (0–255):", toleranceSpinner);
        addRow(form, gc, row++, "Delay (ms):", delaySpinner);

        gc.gridx = 1;
        gc.gridy = row;
        gc.gridwidth = 1;
        gc.fill = GridBagConstraints.NONE;
        form.add(enabledBox, gc);

        JButton ok = new JButton("Lưu");
        ok.addActionListener(e -> onOk());
        JButton cancel = new JButton("Hủy");
        cancel.addActionListener(e -> {
            confirmed = false;
            dispose();
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(ok);
        buttons.add(cancel);

        setLayout(new BorderLayout());
        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(ok);
    }

    private void addRow(JPanel form, GridBagConstraints gc, int row, String label, JComponent field) {
        gc.gridx = 0;
        gc.gridy = row;
        gc.gridwidth = 1;
        gc.weightx = 0;
        gc.fill = GridBagConstraints.NONE;
        form.add(new JLabel(label), gc);
        gc.gridx = 1;
        gc.weightx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        form.add(field, gc);
    }

    private void updateSwatch() {
        colorSwatch.setBackground(chosenColor);
    }

    private void prefill(ClickTarget t) {
        nameField.setText(t.getName());
        xSpinner.setValue(t.getX());
        ySpinner.setValue(t.getY());
        toleranceSpinner.setValue(t.getColorTolerance());
        delaySpinner.setValue((int) Math.min(Integer.MAX_VALUE, t.getDelayMs()));
        enabledBox.setSelected(t.isEnabled());
        chosenColor = t.getTargetColor();
    }

    private void onOk() {
        if (nameField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Tên không được để trống.",
                    "Thiếu thông tin", JOptionPane.WARNING_MESSAGE);
            return;
        }
        confirmed = true;
        dispose();
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    /** @return một {@link ClickTarget} mới dựng từ giá trị các field (chỉ gọi khi confirmed). */
    public ClickTarget getResult() {
        ClickTarget t = new ClickTarget();
        t.setName(nameField.getText().trim());
        t.setX(((Number) xSpinner.getValue()).intValue());
        t.setY(((Number) ySpinner.getValue()).intValue());
        t.setColorTolerance(((Number) toleranceSpinner.getValue()).intValue());
        t.setDelayMs(((Number) delaySpinner.getValue()).longValue());
        t.setEnabled(enabledBox.isSelected());
        t.setTargetColor(chosenColor);
        return t;
    }
}
