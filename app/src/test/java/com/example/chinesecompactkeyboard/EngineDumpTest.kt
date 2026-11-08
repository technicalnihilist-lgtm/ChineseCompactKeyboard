package com.example.chinesecompactkeyboard

import com.example.chinesecompactkeyboard.engine.AssetDictionary
import com.example.chinesecompactkeyboard.engine.PinyinEngine
import com.example.chinesecompactkeyboard.engine.TraditionalConverter
import org.junit.Test
import java.io.File

/**
 * Not an assertion test: dumps the top candidates for a set of common inputs so
 * the ranking can be eyeballed after any dictionary or scoring change.
 */
class EngineDumpTest {

    @Test
    fun dumpCommonInputs() {
        val assets = File("src/main/assets")
        val dictionary = AssetDictionary.loadFromBytes(TestAssets.dictionaryBytes())
        val converter = TraditionalConverter.loadFromBytes(TestAssets.tradBytes())
        val engine = PinyinEngine.create(dictionary, converter)

        val inputs = listOf(
            "ni", "nihao", "wo", "women", "woaini", "zhongguo", "xiexie", "zaijian",
            "xuexi", "pengyou", "jintian", "mingtian", "tianqi", "shenme", "weishenme",
            "zenme", "duibuqi", "meiguanxi", "qingwen", "shijian", "gongzuo", "shouji",
            "diannao", "xihuan", "kafei", "chifan", "shuijiao", "zaoshang", "wanshang",
            "beijing", "shanghai", "chongqing", "putonghua", "zhongwen", "danshi",
            "yinwei", "suoyi", "keneng", "xianzai", "yihou", "zui", "hen", "ma",
            "jintiantianqihenhao", "woxihuannizhongguo", "nihaoshijie"
        )

        val sb = StringBuilder()
        for (input in inputs) {
            val simplified = engine.candidates(input, false, null, null, true, 10)
            val traditional = engine.candidates(input, true, null, null, true, 10)
            sb.append(input).append("\n")
            sb.append("  S: ").append(simplified.joinToString("  ")).append("\n")
            sb.append("  T: ").append(traditional.joinToString("  ")).append("\n")
        }
        File("/scratch/work/engine_dump.txt").writeText(sb.toString())
        println(sb.toString())
    }
}
