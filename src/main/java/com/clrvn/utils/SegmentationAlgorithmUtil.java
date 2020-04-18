package com.clrvn.utils;

import org.apdplat.word.WordSegmenter;
import org.apdplat.word.segmentation.SegmentationAlgorithm;
import org.apdplat.word.segmentation.Word;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Clrvn
 * @description 分割算法工具类
 * @className SegmentationAlgorithmUtil
 */
public class SegmentationAlgorithmUtil {

    /**
     * 分割方法
     */
    private static List<String> seg(String text, SegmentationAlgorithm segmentationAlgorithm) {
        List<String> stringList = new ArrayList<>();
        for (Word word : WordSegmenter.segWithStopWords(text, segmentationAlgorithm)) {
            stringList.add(word.getText());
        }
        return stringList;
    }

    /**
     * 把文本以word分词算法进行分割
     */
    public static List<String> segmentationAlgorithm(String text) {
        List<String> stringList = new ArrayList<>();
       /* //这里用到的是所有的11种算法加在一起
        for (SegmentationAlgorithm segmentationAlgorithm : SegmentationAlgorithm.values()) {
            stringList.addAll(seg(text, segmentationAlgorithm));
        }*/
        //如果需要指定一种分词方法
         stringList = seg(text, SegmentationAlgorithm.MinimumMatching );
        return stringList;
    }
}
