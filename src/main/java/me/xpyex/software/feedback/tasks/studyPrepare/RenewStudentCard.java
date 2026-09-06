package me.xpyex.software.feedback.tasks.studyPrepare;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.ExtensionMethod;
import me.xpyex.software.feedback.packet.both.StudentInfo;
import me.xpyex.software.feedback.packet.in.AYDResponse;
import me.xpyex.software.feedback.packet.out.ApplyStudentCard;
import me.xpyex.software.feedback.packet.out.RecoverMonth;
import me.xpyex.software.feedback.tasks.basis.StudentReader;
import me.xpyex.software.feedback.util.AiyouduUtil;
import me.xpyex.software.feedback.util.ConfigManager;
import me.xpyex.software.feedback.util.LogUtil;
import org.slf4j.Logger;

@ExtensionMethod(AiyouduUtil.class)
public class RenewStudentCard {
    public static Logger log = LogUtil.getLogger();

    /**
     * 开始续费（处理所有学生）
     */
    public static void start() {
        startWithStudents(null);
    }

    /**
     * 开始续费（处理指定学生列表）
     *
     * @param students 要续费的学生列表，null表示处理所有学生
     */
    public static void startWithStudents(List<StudentInfo> students) {
        if (AiyouduUtil.token == null) {
            LogUtil.warn(" × 请先登录");
            return;
        }
        if (!StudentReader.hasStudents()) {
            LogUtil.warn(" × 请先导入学生信息");
            return;
        }
        JsonObject config = ConfigManager.loadConfig("renew");

        // 确定要处理的学生列表
        var studentMap = (students != null)
                             ? students.stream().collect(Collectors.toMap(StudentInfo::getStudentId, s -> s))
                             : StudentReader.copyStudents();

        studentMap.values().forEach(student -> {
            int day = 1;
            if (config.has(student.getRealName())) day = config.get(student.getRealName()).getAsInt();
            if (day > 0) {
                if (student.getExpireDay() != 0) {  //天数非0的情况下
                    if (student.getCardType() == StudentInfo.CardType.IN_DAYS.getCardType()) {
                        if ((day - student.getExpireDay()) <= 0) {
                            LogUtil.logNecessary("学生 " + student.getRealName() + " 的学生卡天数充足，跳过续费");
                            return;
                        }
                    } else if (student.getCardType() == StudentInfo.CardType.IN_MONTHS.getCardType()) {
                        if (student.getExpireDay() >= 30) {
                            int month = student.getExpireDay() / 30;
                            if (student.getExpireDay() - month * 30 == 0) {
                                RecoverMonth.url.postUrlWithToken(RecoverMonth.of().setStudentId(student.getStudentId()).setMonth(month));
                                LogUtil.logNecessary("学生 " + student.getRealName() + " 的学生卡是月度卡，已退费");
                            } else {
                                LogUtil.logNecessary("学生 " + student.getRealName() + " 的学生卡是月度卡，且无法退费，已跳过此学生");
                                return;
                            }
                        }
                    }
                }
                AYDResponse response = ApplyStudentCard.day(day).setStudentId(student.getStudentId()).sendToUrl();
                if (response.isSuccess()) {
                    log.info("  √ {} 的学生卡续费成功: {} 天", student.getRealName(), day);
                } else {
                    LogUtil.logNecessary("  ✗ " + student.getRealName() + " 的学生卡续费失败：" + response.getMessage());
                }
            }
        });
        LogUtil.logNecessary("已按照配置文件续费所有学生");
    }
}
