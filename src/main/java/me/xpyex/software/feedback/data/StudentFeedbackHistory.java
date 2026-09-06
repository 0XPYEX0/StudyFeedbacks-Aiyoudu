package me.xpyex.software.feedback.data;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 单个学生的历史反馈记录（对应 config/feedback/{真实姓名}_{studentId}.json）。
 * <p>
 * 用户在「填入历史反馈」中粘贴的是曾经真正交给学生的反馈文本（从系统外或人工整理而来），
 * 程序只负责保存。生成 AI 反馈时会取最近若干条一并合并给 AI 参考，保持风格连续、避免重复。
 * <p>
 * records 内的 date 使用 ISO yyyy-MM-dd，便于按时间排序取"最近几条"。
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor(staticName = "of")
public class StudentFeedbackHistory {
    private int studentId;
    /** 冗余保存真实姓名，用于生成可读的文件名 */
    private String realName;
    private List<Record> records = new ArrayList<>();

    @Data
    @Accessors(chain = true)
    @NoArgsConstructor(staticName = "of")
    public static class Record {
        /** 该次反馈对应的日期（ISO yyyy-MM-dd） */
        private String date;
        /** 反馈文本 */
        private String text;
    }
}
