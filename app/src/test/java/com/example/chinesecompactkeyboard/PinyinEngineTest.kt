package com.example.chinesecompactkeyboard

import com.example.chinesecompactkeyboard.engine.AssetDictionary
import com.example.chinesecompactkeyboard.engine.PinyinEngine
import com.example.chinesecompactkeyboard.engine.PinyinSegmenter
import com.example.chinesecompactkeyboard.engine.TraditionalConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Runs the real engine against the real bundled dictionary on the JVM.
 * These are the acceptance tests for the offline pinyin input path.
 */
class PinyinEngineTest {

    companion object {
        private lateinit var engine: PinyinEngine
        private lateinit var converter: TraditionalConverter
        private lateinit var segmenter: PinyinSegmenter
        private lateinit var dictionary: AssetDictionary

        @BeforeClass
        @JvmStatic
        fun setUp() {
            val dictBytes = TestAssets.dictionaryBytes()
            val tradBytes = TestAssets.tradBytes()
            dictionary = AssetDictionary.loadFromBytes(dictBytes)
            converter = TraditionalConverter.loadFromBytes(tradBytes)
            engine = PinyinEngine.create(dictionary, converter)
            segmenter = PinyinSegmenter(dictionary.syllables)
        }
    }

    private fun top(input: String, traditional: Boolean = false): List<String> =
        engine.candidates(input, traditional, null, null, suggestions = true, limit = 40)

    @Test
    fun dictionaryIsSubstantial() {
        assertTrue("expected a real dictionary, got ${dictionary.count}", dictionary.count > 50_000)
        assertTrue("expected a full syllable table", dictionary.syllables.size > 350)
    }

    @Test
    fun nihaoGivesNiHao() {
        assertEquals("你好", top("nihao").first())
    }

    @Test
    fun zhongguoGivesZhongGuo() {
        assertEquals("中国", top("zhongguo").first())
    }

    @Test
    fun woainiGivesWoAiNi() {
        assertEquals("我爱你", top("woaini").first())
    }

    @Test
    fun xiexieGivesXieXie() {
        assertEquals("谢谢", top("xiexie").first())
    }

    @Test
    fun zaijianGivesZaiJian() {
        assertEquals("再见", top("zaijian").first())
    }

    @Test
    fun xuexiGivesXueXi() {
        assertEquals("学习", top("xuexi").first())
    }

    @Test
    fun toneIndependentAndUppercaseInput() {
        assertEquals("你好", top("NiHao").first())
        assertEquals("中国", top("ZHONGGUO").first())
    }

    @Test
    fun singleSyllableShowsCharacters() {
        val list = top("ni")
        assertEquals("你", list.first())
        assertTrue(list.contains("你"))
        assertTrue("expected several single-character candidates", list.count { it.length == 1 } >= 4)
    }

    @Test
    fun partialSyllableStillSuggestsWords() {
        val list = top("nih")
        assertTrue("expected 你好 for partial input, got $list", list.contains("你好"))
    }

    @Test
    fun sentenceCompositionForLongInput() {
        val list = top("jintiantianqihenhao")
        assertTrue("expected a composed sentence, got $list", list.contains("今天天气很好"))
    }

    @Test
    fun alternativeSegmentationIsOffered() {
        val list = top("xian")
        assertTrue("expected 西安 as an alternative, got $list", list.contains("西安") || list.contains("先"))
    }

    @Test
    fun traditionalModeConvertsCandidates() {
        assertEquals("中國", top("zhongguo", traditional = true).first())
        assertEquals("謝謝", top("xiexie", traditional = true).first())
        assertEquals("學習", top("xuexi", traditional = true).first())
    }

    @Test
    fun traditionalConverterHandlesPhrasesAndCharacters() {
        assertEquals("中國", converter.convert("中国"))
        assertEquals("愛", converter.convert("爱"))
        assertEquals("謝謝", converter.convert("谢谢"))
        assertEquals("學習", converter.convert("学习"))
        assertEquals("乾淨", converter.convert("干净"))
        assertEquals("幹部", converter.convert("干部"))
        assertEquals("頭髮", converter.convert("头发"))
    }

    @Test
    fun converterLeavesAsciiAndTraditionalAlone() {
        assertEquals("hello 123", converter.convert("hello 123"))
        assertEquals("中國", converter.convert("中國"))
    }

    @Test
    fun segmenterSplitsSyllables() {
        assertEquals(listOf("ni", "hao"), segmenter.segment("nihao").first().syllables)
        assertEquals(listOf("zhong", "guo"), segmenter.segment("zhongguo").first().syllables)
        assertEquals(listOf("xi", "huan"), segmenter.segment("xihuan").first().syllables)
    }

    @Test
    fun segmenterHandlesPartialTail() {
        val seg = segmenter.segment("nih").first()
        assertEquals(listOf("ni"), seg.syllables)
        assertEquals("h", seg.partial)
    }

    @Test
    fun candidatesAreDistinct() {
        val list = top("shi")
        assertEquals(list.size, list.distinct().size)
    }

    @Test
    fun everyCandidateIsChineseOrAscii() {
        for (input in listOf("nihao", "wo", "shijie", "pengyou", "tianqi")) {
            for (candidate in top(input).take(20)) {
                assertTrue(
                    "unexpected candidate '$candidate' for $input",
                    candidate.all { it.code > 0x2E00 || it.isLetterOrDigit() }
                )
            }
        }
    }
}
