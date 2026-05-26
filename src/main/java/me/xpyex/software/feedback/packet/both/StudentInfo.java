package me.xpyex.software.feedback.packet.both;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import me.xpyex.software.feedback.packet.in.DataInfo;

@Data
@Accessors(chain = true)
@NoArgsConstructor(staticName = "of")
public class StudentInfo {
    private int studentId;  //学生在系统里的ID，用于访问API
    private String realName;  //学生的真实姓名
    private String group;  //学生所在时间段[分组]
    private String gradeValue;  //学生所在年级
    private String phone;  //学生手机号
    private DataInfo dataInfo = null;  //词汇量、阅读力
    private int cardType; // 课程卡类型，1是体验，2是包月起步，3是按日结算
    private int expireDay;  // 剩余天数
}
