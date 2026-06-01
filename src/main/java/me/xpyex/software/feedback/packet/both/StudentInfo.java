package me.xpyex.software.feedback.packet.both;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import me.xpyex.software.feedback.packet.in.DataInfo;
import me.xpyex.software.feedback.util.AiyouduUtil;

@Data
@Accessors(chain = true)
@NoArgsConstructor(staticName = "of")
public class StudentInfo extends AYDPacket {
    public static final String updateUrl = AiyouduUtil.apiUrl + "organiztion/student/updateStudentInfo";

    private int studentId;  //学生在系统里的ID，用于访问API
    private String realName;  //学生的真实姓名
    private String nickName;  //学生昵称
    private String group;  //学生所在时间段[分组]
    private String gradeValue;  //学生所在年级
    private int grade;  //年级数字，10表示九年级(？但是系统里真这样)
    private String phone;  //学生手机号
    private String userName;  //用户名(通常是手机号)
    private String pwd;  //密码
    private String className;  //班级名称
    private String schoolName;  //学校名称
    private int groupId;  //分组ID
    private String parent;  //家长信息
    private String parentPhone;  //家长电话
    private String studyNo;  //学号
    private Integer sex;  //性别
    private String province;  //省份
    private String city;  //城市
    private DataInfo dataInfo = null;  //词汇量、阅读力
    private int cardType; // 课程卡类型，1是体验，2是包月起步，3是按日结算
    private int expireDay;  // 剩余天数
    private int dayValid;  //有效天数
    private int curriculumStatus = 2;  //课程卡???
    private int stage;  //阶段
    private int isTraining;  //是否培训中
    private int billingType;  //计费类型
    private Double fractionEn = null;  //英语分数
    private Double fractionTargetEn = null;  //英语目标分数
    private Double fractionCn = null;  //语文分数
    private Double maths = null;  //数学分数
    private String remark;  //备注
    private List<Integer> teacherIds;  //教师ID列表

    @Getter
    @AllArgsConstructor
    public enum CardType {
        IN_TRIAL(1),
        IN_DAYS(3),
        IN_MONTHS(2);

        private final int cardType;
    }
}
