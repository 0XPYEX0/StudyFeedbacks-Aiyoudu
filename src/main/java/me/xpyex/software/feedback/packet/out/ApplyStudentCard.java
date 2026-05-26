package me.xpyex.software.feedback.packet.out;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import me.xpyex.software.feedback.util.AiyouduUtil;

@Data
@Accessors(chain = true)
@NoArgsConstructor(staticName = "of")
// 续费
public class ApplyStudentCard {
    //  https://group.aiyoudu.cn/api2/organiztion/student/studentApply
    public static final String url = AiyouduUtil.apiUrl + "organiztion/student/studentApply";
    private int studentId;
    private int applyType;  //目前已知，1是月，4是天
    private int curriculumCardId;  //目前已知，14代表天，23代表月
    private int day;  // 续费天数
    private Object remark = null;  //不知道是啥，反正发出去的JSON都是null
    private int wisdomCurrency;  // 消耗多少钱

    public static ApplyStudentCard day(int amount) {
        return of().setDay(amount)
                   .setApplyType(4)
                   .setCurriculumCardId(14)
                   .setWisdomCurrency(amount * 10);
    }

    public static ApplyStudentCard month(int amount) {
        return of().setDay(amount * 30)
                   .setApplyType(Type.MONTH.getApplyType())
                   .setCurriculumCardId(Type.MONTH.getCurriculumCardId())
                   .setWisdomCurrency(amount * 100);
    }

    @Getter
    @AllArgsConstructor
    public enum Type {
        DAY(4, 14),
        MONTH(1, 23);

        private final int applyType;
        private final int curriculumCardId;
    }
}
