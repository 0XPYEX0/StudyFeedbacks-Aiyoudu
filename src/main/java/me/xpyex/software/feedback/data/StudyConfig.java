package me.xpyex.software.feedback.data;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 单个学生的学案设置（对应 config/study/{真实姓名}_{studentId}.json）
 * <p>
 * typeCountMap 的 key 使用题型的展示名（{@code StudyContentsUtil.StudyType.getName()}，
 * 如 "精准阅读"），value 为该题型本次要打印的篇数（非负整数）。
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor(staticName = "of")
public class StudyConfig {
    private int studentId;
    /** 冗余保存真实姓名，便于生成可读的文件名与界面识别 */
    private String realName;
    /** 各题型 -> 打印篇数（仅保存 > 0 的有效配置） */
    private Map<String, Integer> typeCountMap = new LinkedHashMap<>();
    /** 打印完成后是否回收（退掉）刚续费的月卡；false 表示给学生保留该月卡（真正续费一月） */
    private boolean refundMonth = true;

    /** 各题型篇数总和（用于判断该配置是否需要处理） */
    public int totalCount() {
        if (typeCountMap == null) return 0;
        return typeCountMap.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** 是否无需打印（无配置或所有题型篇数都 <= 0） */
    public boolean isEmpty() {
        return totalCount() <= 0;
    }
}
