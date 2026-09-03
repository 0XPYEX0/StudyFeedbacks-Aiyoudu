package me.xpyex.software.feedback.packet.in;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor(staticName = "of")
public class DataInfo {
    private int vocabulary;  //当前词汇量
    private int vocabularyStart;  //摸底词汇量
    private int readingAbility;  //阅读力
    private int phraseNum;  // 短语量
    private int phraseStart;  // 摸底短语量
}
