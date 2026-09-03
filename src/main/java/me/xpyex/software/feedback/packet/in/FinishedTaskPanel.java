package me.xpyex.software.feedback.packet.in;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor(staticName = "of")
public class FinishedTaskPanel {
    private int studyWord;  //学习词汇
    private int checkWord;  //测试词汇
    private int increaseWord;  //增长词汇
    @SerializedName("revieWord") //原JSON拼错了
    private int reviewWord;  //复习词汇
    private List<SinglePanel> wordAndReadList;
    private List<SinglePanel> listeningAndList;
    private int studyPhrase;  // 学习短语
    private int checkPhrase;  // 测试短语


    public int getReviewWord() {
        return reviewWord;
    }
}
