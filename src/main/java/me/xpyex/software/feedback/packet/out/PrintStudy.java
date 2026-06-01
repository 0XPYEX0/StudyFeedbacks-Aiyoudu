package me.xpyex.software.feedback.packet.out;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import me.xpyex.software.feedback.packet.both.AYDPacket;
import me.xpyex.software.feedback.util.AiyouduUtil;

@Data
@Accessors(chain = true)
@NoArgsConstructor(staticName = "of")
// 打印学案
public class PrintStudy extends AYDPacket {
    public static final String url = AiyouduUtil.apiUrl + "student/study/printSaveStudy";
    private int printNum = 1;  //打印份数
    private int articleNum = 2;  //单份有几篇
    private int printType = 1;  //待探究，先固定
    private int studentId;
}
