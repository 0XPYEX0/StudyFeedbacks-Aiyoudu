package me.xpyex.software.feedback.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import me.xpyex.software.feedback.packet.both.StudentInfo;
import me.xpyex.software.feedback.tasks.StudentReader;

/**
 * 学生选择对话框抽象基类
 * 提供通用的学生选择功能，子类可通过钩子方法扩展额外列和自定义行为
 */
public abstract class BaseStudentSelectionDialog extends JDialog {
    protected final List<JCheckBox> studentCheckBoxes = new ArrayList<>();
    protected final Map<JCheckBox, Integer> checkBoxToStudentId = new HashMap<>();
    protected final Map<JCheckBox, JLabel> checkBoxToInfoLabel = new HashMap<>(); // 用于高亮
    protected JTextField searchField; // 搜索框
    
    /**
     * 构造函数
     *
     * @param parent 父窗口
     * @param title  对话框标题
     */
    protected BaseStudentSelectionDialog(JFrame parent, String title) {
        super(parent, "选择学生 - " + title, true);
        setSize(450, 550);
        setLocationRelativeTo(parent);
    }

    /**
     * 初始化UI（模板方法）
     */
    protected void initBaseUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // 顶部面板：提示 + 搜索框
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        
        JLabel hintLabel = new JLabel(getHintText());
        hintLabel.setFont(new Font("微软雅黑", Font.BOLD, 13));
        topPanel.add(hintLabel, BorderLayout.WEST);
        
        // 搜索框
        searchField = new JTextField(15);
        searchField.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        searchField.setToolTipText("输入学生姓名进行搜索");
        topPanel.add(searchField, BorderLayout.EAST);
        
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // 中间复选框列表（带滚动）
        JPanel studentPanel = new JPanel(new GridBagLayout());
        JScrollPane scrollPane = new JScrollPane(studentPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        
        // 允许子类自定义滚动速度
        customizeScrollPane(scrollPane);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(3, 5, 3, 5);

        studentCheckBoxes.clear();
        Map<Integer, StudentInfo> allStudents = StudentReader.getAllStudents();

        int row = 0;
        for (Map.Entry<Integer, StudentInfo> entry : allStudents.entrySet()) {
            StudentInfo student = entry.getValue();
            int studentId = student.getStudentId();

            // 第0列：勾选框
            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.weightx = 0;
            gbc.fill = GridBagConstraints.NONE;
            JCheckBox checkBox = new JCheckBox();
            checkBox.setSelected(isDefaultSelected());
            studentCheckBoxes.add(checkBox);
            checkBoxToStudentId.put(checkBox, studentId);
            studentPanel.add(checkBox, gbc);

            // 第1列：学生信息（使用普通JLabel，后续通过文本高亮）
            gbc.gridx = 1;
            gbc.weightx = 1.0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            String studentInfo = formatStudentInfo(student);
            JLabel infoLabel = createHighlightableLabel(studentInfo);
            infoLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
            studentPanel.add(infoLabel, gbc);
            
            // 保存引用以便高亮
            checkBoxToInfoLabel.put(checkBox, infoLabel);
            
            // 存储原始文本用于高亮
            infoLabel.putClientProperty("originalText", studentInfo);
            infoLabel.putClientProperty("studentName", student.getRealName());

            // 钩子方法：添加额外列（如输入框、日期选择等）
            addExtraColumns(gbc, row, student, studentPanel);

            row++;
        }

        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // 底部按钮面板
        JPanel buttonPanel = createButtonPanel();
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);
        
        // 添加搜索监听器
        setupSearchListener();
    }

    /**
     * 创建可高亮的标签
     */
    private JLabel createHighlightableLabel(String text) {
        return new JLabel(text);
    }

    /**
     * 设置搜索监听器
     */
    private void setupSearchListener() {
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                highlightStudents();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                highlightStudents();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                highlightStudents();
            }
        });
    }

    /**
     * 高亮匹配的学生
     */
    private void highlightStudents() {
        String searchText = searchField.getText().trim().toLowerCase();
        
        for (Map.Entry<JCheckBox, JLabel> entry : checkBoxToInfoLabel.entrySet()) {
            JLabel label = entry.getValue();
            String originalText = (String) label.getClientProperty("originalText");
            String studentName = (String) label.getClientProperty("studentName");
            
            if (searchText.isEmpty()) {
                // 无搜索文本，恢复默认样式
                label.setForeground(Color.BLACK);
                label.setFont(new Font("微软雅黑", Font.PLAIN, 12));
            } else if (studentName != null && studentName.toLowerCase().contains(searchText)) {
                // 匹配，高亮显示
                label.setForeground(new Color(0, 100, 0)); // 深绿色
                label.setFont(new Font("微软雅黑", Font.BOLD, 12));
            } else {
                // 不匹配，淡化显示
                label.setForeground(Color.GRAY);
                label.setFont(new Font("微软雅黑", Font.PLAIN, 12));
            }
        }
    }

    /**
     * 获取提示信息文本（子类可重写）
     */
    protected String getHintText() {
        return "请选择要处理的学生：";
    }

    /**
     * 格式化学生信息显示（子类可重写）
     */
    protected String formatStudentInfo(StudentInfo student) {
        return String.format("%-10s [ID:%d] - %s",
            student.getRealName(),
            student.getStudentId(),
            student.getGroup());
    }

    /**
     * 是否默认全选（子类可重写）
     */
    protected boolean isDefaultSelected() {
        return true;
    }

    /**
     * 自定义滚动面板（子类可重写）
     */
    protected void customizeScrollPane(JScrollPane scrollPane) {
        // 默认实现：无特殊设置
    }

    /**
     * 添加额外列的钩子方法（子类可重写）
     *
     * @param gbc         布局约束
     * @param row         当前行号
     * @param student     学生信息
     * @param studentPanel 学生面板
     */
    protected void addExtraColumns(GridBagConstraints gbc, int row, 
                                   StudentInfo student, JPanel studentPanel) {
        // 默认空实现，子类可根据需要添加额外列
    }

    /**
     * 创建按钮面板（子类可重写以添加额外按钮）
     */
    protected JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));

        JButton btnSelectAll = new JButton("全选");
        JButton btnDeselectAll = new JButton("取消全选");
        JButton btnOK = new JButton("确定");
        JButton btnCancel = new JButton("取消");

        // 全选按钮
        btnSelectAll.addActionListener(ev -> {
            for (JCheckBox cb : studentCheckBoxes) {
                cb.setSelected(true);
            }
        });

        // 取消全选按钮
        btnDeselectAll.addActionListener(ev -> {
            for (JCheckBox cb : studentCheckBoxes) {
                cb.setSelected(false);
            }
        });

        // 确定按钮
        btnOK.addActionListener(ev -> {
            List<StudentInfo> selectedStudents = getSelectedStudents();

            if (selectedStudents.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                    "请至少选择一个学生！",
                    "提示",
                    JOptionPane.WARNING_MESSAGE);
                return;
            }

            // 调用子类的确认处理方法
            onOK(selectedStudents);
        });

        // 取消按钮
        btnCancel.addActionListener(ev -> dispose());

        buttonPanel.add(btnSelectAll);
        buttonPanel.add(btnDeselectAll);
        buttonPanel.add(btnOK);
        buttonPanel.add(btnCancel);

        return buttonPanel;
    }

    /**
     * 确定按钮点击处理（子类可重写）
     *
     * @param selectedStudents 选中的学生列表
     */
    protected void onOK(List<StudentInfo> selectedStudents) {
        dispose();
    }

    /**
     * 获取选中的学生列表
     */
    protected List<StudentInfo> getSelectedStudents() {
        return studentCheckBoxes.stream()
                   .filter(JCheckBox::isSelected)
                   .map(cb -> {
                       Integer studentId = checkBoxToStudentId.get(cb);
                       if (studentId != null) {
                           return StudentReader.getStudentById(studentId);
                       }
                       return null;
                   })
                   .filter(Objects::nonNull)
                   .toList();
    }
}
