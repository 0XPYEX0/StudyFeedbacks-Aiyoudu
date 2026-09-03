package me.xpyex.software.feedback.packet.util;

import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.xpyex.software.feedback.packet.both.StudentInfo;
import me.xpyex.software.feedback.packet.in.AYDResponse;
import me.xpyex.software.feedback.packet.in.StudyContentInfo;
import me.xpyex.software.feedback.util.AiyouduUtil;
import me.xpyex.software.feedback.util.GsonUtil;

public class StudyContentsUtil {
    // https://group.aiyoudu.cn/api2/student/study/applySupervisionPage?articleId=&studyType=1&current=1&size=12&readType=1&studentId=251838
    // articleId为文章ID，搜索时需要提供
    // studyType为完成情况，按照FinishedType枚举的ID操作
    // readType是文章类型，按照StudyType枚举的ID操作
    public static final String PRINT_URL =
        AiyouduUtil.apiUrl + "student/study/applySupervisionPage?" + String.join("&",
            "article={$article}",
            "studyType={$finishedType}",
            "readType={$studyType}",
            "current=1&size={$amount}",
            "studentId={$id}"
        );

    public static String getPrintUrl(long studentId, FinishedType finishedType, StudyType studyType, int amount, Integer articleId) {
        return PRINT_URL.replace("{$article}", articleId == null ? "" : articleId + "")
                   .replace("{$finishedType}", finishedType.getId() + "")
                   .replace("{$studyType}", studyType.getId() + "")
                   .replace("{$amount}", amount + "")
                   .replace("{$id}", studentId + "");
    }

    public static String getPrintUrl(StudentInfo studentInfo, FinishedType finishedType, StudyType studyType, int amount, Integer articleId) {
        return getPrintUrl(studentInfo.getStudentId(), finishedType, studyType, amount, articleId);
    }

    /**
     * 获取学生在单题型的平均难度
     *
     * @param studentId     学生的ID
     * @param finishedType  待反馈、平板已读、纸面已读
     * @param studyType     题型
     * @param averageAmount 向API查询的近期数量，可以通过反馈的数量来填写，例如单周完成8篇，就拉取近8篇处理
     * @return 学生在该题型的近期平均难度，取整。若为-1则失败
     */
    public static int getAverageDifficulty(long studentId, FinishedType finishedType, StudyType studyType, int averageAmount) {
        String url = getPrintUrl(studentId, finishedType, studyType, averageAmount, null);
        AYDResponse response = AYDResponse.of(AiyouduUtil.getUrlWithToken(url));
        if (response.isSuccess()) {
            try {
                return getStudyContents(response).stream()
                           .map(StudyContentInfo::getDifficulty)
                           .collect(Collectors.averagingInt(Integer::intValue))
                           .intValue();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return -1;
    }

    public static List<StudyContentInfo> getStudyContents(AYDResponse response) {
        return response.getDataAsJsonObject().getAsJsonArray("records")
                   .asList().stream()
                   .map(e -> GsonUtil.parseObj(GsonUtil.toJsonStr(e.getAsJsonObject(), false), StudyContentInfo.class))
                   .toList();
    }

    @AllArgsConstructor
    public enum FinishedType {
        NOT_FINISHED(1),  // 待反馈
        FINISHED_ONLINE(3),  // 平板已读
        FINISHED_PAPER(2);  // 纸面已读

        @Getter
        private final int id;
    }

    @AllArgsConstructor
    public enum StudyType {
        NORMAL_READ(1),  // 精准读阅读
        CHOOSE_FIVE_FROM_SEVEN(4),  // 七选五
        COMPLETION(5);  // 完形填空
        //有其它的，我们暂时不需要处理，人工操作就行

        @Getter
        private final int id;

        public static StudyType getStudyTypeByName(String name) {
            if (name.contains("精准")) {
                return NORMAL_READ;
            } else if (name.contains("七选五")) {
                return CHOOSE_FIVE_FROM_SEVEN;
            } else if (name.contains("完形")) {
                return COMPLETION;
            }
            return null;
        }
    }
}
