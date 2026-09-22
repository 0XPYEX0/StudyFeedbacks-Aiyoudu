package me.xpyex.software.feedback.packet.out;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
    private int printNum = 1;  //打印份数，就是生成新学案的旁边那个
    private int articleNum = 2;  //单份有几篇
    private int printType = 1;  //打印的类型，根据StudyType来
    private int isAnalysis = 0;  // 是否包含文章解析
    private int urlType = 1;  // URL的类型. 此处体现为生成学案时候是否生成答案，默认为不生成(1)，若需生成则设为0
    private int studentId;

    public PrintStudy setGenerateAnalysis(boolean analysis) {
        this.isAnalysis = analysis ? 1 : 0;
        return this;
    }

    public PrintStudy setGenerateAnswer(boolean answer) {
        this.urlType = answer ? 0 : 1;  // 生成是0，不生成是1
        return this;
    }

    @Data
    @Accessors(chain = true)
    @NoArgsConstructor(staticName = "of")
    public static class PrintMerge extends AYDPacket {
        public static final String url = AiyouduUtil.apiUrl + "student/study/printMerge";
        private final List<Integer> printIds = new ArrayList<>();

        public PrintMerge add(int id) {
            printIds.add(id);
            return this;
        }

        public PrintMerge addAll(Collection<Integer> collection) {
            printIds.addAll(collection);
            return this;
        }
    }
}
