package me.xpyex.software.feedback.ui;

import java.util.List;
import java.util.function.Consumer;
import javax.swing.JFrame;
import me.xpyex.software.feedback.packet.both.StudentInfo;

/**
 * 通用学生选择对话框 - 用于选择要处理的学生列表（不包含额外配置项）
 * 适用于只需要选择学生的场景，如AI点评等
 */
public class SimpleStudentSelectionDialog extends BaseStudentSelectionDialog {
    private Consumer<List<StudentInfo>> callback;

    private SimpleStudentSelectionDialog(JFrame parent, String title, Consumer<List<StudentInfo>> callback) {
        super(parent, title);
        this.callback = callback;
        initBaseUI();
    }

    @Override
    protected void onOK(List<StudentInfo> selectedStudents) {
        dispose();
        if (callback != null) {
            callback.accept(selectedStudents);
        }
    }

    /**
     * 显示学生选择对话框
     *
     * @param parent   父窗口
     * @param title    对话框标题
     * @param callback 回调函数，接收选中的学生列表
     */
    public static void showDialog(JFrame parent, String title, Consumer<List<StudentInfo>> callback) {
        SimpleStudentSelectionDialog dialog = new SimpleStudentSelectionDialog(parent, title, callback);
        dialog.setVisible(true);
    }
}
