package com.example.chinesecompactkeyboard

import com.example.chinesecompactkeyboard.engine.AssetDictionary
import com.example.chinesecompactkeyboard.engine.PinyinEngine
import com.example.chinesecompactkeyboard.engine.TraditionalConverter
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * Latency guard rails. The keyboard must stay responsive on low-end hardware,
 * so a candidate lookup has to stay in the low-millisecond range.
 */
class EnginePerformanceTest {

    companion object {
        private lateinit var engine: PinyinEngine
        private lateinit var dictionary: AssetDictionary
        private lateinit var converter: TraditionalConverter

        @BeforeClass
        @JvmStatic
        fun setUp() {
            val assets = File("src/main/assets")
            val start = System.nanoTime()
            dictionary = AssetDictionary.loadFromBytes(TestAssets.dictionaryBytes())
            converter = TraditionalConverter.loadFromBytes(TestAssets.tradBytes())
            engine = PinyinEngine.create(dictionary, converter)
            val ms = (System.nanoTime() - start) / 1_000_000.0
            println("dictionary load: %.1f ms, %d entries, %.1f MB".format(ms, dictionary.count, TestAssets.dictionarySize() / 1e6))
        }
    }

    @Test
    fun dictionaryLoadsQuickly() {
        val start = System.nanoTime()
        AssetDictionary.loadFromBytes(TestAssets.dictionaryBytes())
        val ms = (System.nanoTime() - start) / 1_000_000.0
        println("cold load: %.1f ms".format(ms))
        assertTrue("dictionary load took ${ms}ms", ms < 400.0)
    }

    private val inputs = listOf(
        "n", "ni", "nih", "niha", "nihao", "zhongguo", "woaini", "xiexie",
        "jintian", "jintiantianqihenhao", "pengyou", "shijie", "women", "shenme",
        "weishenme", "zenmeban", "duibuqi", "meiguanxi", "qingwen", "shijian",
        "gongzuo", "shouji", "diannao", "xihuan", "kafei", "chifan", "shuijiao",
        "zaoshang", "wanshang", "beijing", "shanghai", "chongqing", "putonghua",
        "zhongwen", "danshi", "yinwei", "suoyi", "keneng", "xianzai", "yihou",
        "nihaoshijie", "woxihuannizhongguo", "mingtianjian", "xiexieni", "zaijianle",
        "wanshanghao", "jintianhao", "shenmeren", "zenmeyang", "haode"
    )

    /** Uncached latency: a fresh engine per pass so the result cache is empty. */
    @Test
    fun candidateLookupIsFast() {
        var total = 0.0
        var count = 0
        repeat(3) {
            val fresh = PinyinEngine.create(dictionary, converter)
            for (input in inputs) {
                val start = System.nanoTime()
                fresh.candidates(input, false, null, null, true, 60)
                total += (System.nanoTime() - start) / 1_000_000.0
                count++
            }
        }
        val perQueryMs = total / count
        println("uncached candidate lookup: %.2f ms".format(perQueryMs))
        assertTrue("lookup averaged ${perQueryMs}ms", perQueryMs < 25.0)
    }

    @Test
    fun cachedLookupIsVeryFast() {
        repeat(2) { inputs.forEach { engine.candidates(it, false, null, null, true, 60) } }
        val start = System.nanoTime()
        repeat(20) { inputs.forEach { engine.candidates(it, false, null, null, true, 60) } }
        val perQueryMs = (System.nanoTime() - start) / 1_000_000.0 / (20 * inputs.size)
        println("cached candidate lookup: %.3f ms".format(perQueryMs))
        assertTrue("lookup averaged ${perQueryMs}ms", perQueryMs < 5.0)
    }

    @Test
    fun traditionalConversionIsFast() {
        var total = 0.0
        var count = 0
        repeat(3) {
            val fresh = PinyinEngine.create(dictionary, converter)
            for (input in inputs) {
                val start = System.nanoTime()
                fresh.candidates(input, true, null, null, true, 60)
                total += (System.nanoTime() - start) / 1_000_000.0
                count++
            }
        }
        val perQueryMs = total / count
        println("uncached traditional lookup: %.2f ms".format(perQueryMs))
        assertTrue("lookup averaged ${perQueryMs}ms", perQueryMs < 25.0)
    }
}
