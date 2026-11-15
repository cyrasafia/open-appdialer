package com.cyrasafia.openappdialer.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * spec 7.1 匹配引擎表驱动单测（真实拼音源 = vendored TinyPinyin + 多音字表）。
 * v3.4 起层级：L1=首字母、L2=全拼、L3=混合、L4=子串。
 */
class T9MatcherTest {

    private val pinyin = DefaultPinyinSource()

    private fun idx(label: String) = LabelIndexer.index(label, pinyin)

    // ---- 微信 weixin = 934946 / wx = 99 ----

    @Test
    fun `weixin initials L1 exact`() {
        val m = T9Matcher.match("99", idx("微信"))!!
        assertEquals(MatchResult.L1, m.level)
        assertEquals(true, m.exact)
    }

    @Test
    fun `weixin full L2 exact`() {
        val m = T9Matcher.match("934946", idx("微信"))!!
        assertEquals(MatchResult.L2, m.level)
        assertEquals(true, m.exact)
    }

    @Test
    fun `weixin full prefix L2`() {
        val m = T9Matcher.match("934", idx("微信"))!!
        assertEquals(MatchResult.L2, m.level)
        assertEquals(false, m.exact)
    }

    // ---- T9启动器：全拼 t9qidongqi = 8974366474；首字母 t9qdq = 89737；混合 qdongq = 736647 / qidq = 7437 ----

    @Test
    fun `t9launcher initials L1 exact`() {
        val m = T9Matcher.match("89737", idx("T9启动器"))!!
        assertEquals(MatchResult.L1, m.level) // t9(串) + qdq（音节首字母）= 首字母串全文
        assertEquals(true, m.exact)
    }

    @Test
    fun `t9launcher full L2 exact`() {
        val m = T9Matcher.match("8974366474", idx("T9启动器"))!!
        assertEquals(MatchResult.L2, m.level)
        assertEquals(true, m.exact)
    }

    @Test
    fun `t9launcher mixed qdongq L3`() {
        val m = T9Matcher.match("736647", idx("T9启动器"))!!
        assertEquals(MatchResult.L3, m.level)
    }

    @Test
    fun `t9launcher mixed qidq L3`() {
        val m = T9Matcher.match("7437", idx("T9启动器"))!!
        assertEquals(MatchResult.L3, m.level)
    }

    // ---- 1DM+：1→键2、+→键9；序列 2369（折叠）/ 236（删除分隔符）。
    // 纯字母数字名 full==initials，按新规则归入更优的 L1 ----

    @Test
    fun `1dm L1 exact`() {
        val m = T9Matcher.match("2369", idx("1DM+"))!!
        assertEquals(MatchResult.L1, m.level)
        assertEquals(true, m.exact)
    }

    @Test
    fun `1dm dropped-separator variant prefix L1`() {
        val m = T9Matcher.match("236", idx("1DM+"))!!
        assertEquals(MatchResult.L1, m.level)
    }

    @Test
    fun `1dm substring L4`() {
        val m = T9Matcher.match("369", idx("1DM+"))!!
        assertEquals(MatchResult.L4, m.level)
        assertEquals(1, m.position)
    }

    // ---- 12306：序列 22396（纯数字名 full==initials → L1） ----

    @Test
    fun `12306 L1 exact`() {
        val m = T9Matcher.match("22396", idx("12306"))!!
        assertEquals(MatchResult.L1, m.level)
        assertEquals(true, m.exact)
    }

    @Test
    fun `12306 skip leading 1 substring L4`() {
        val m = T9Matcher.match("2396", idx("12306"))!!
        assertEquals(MatchResult.L4, m.level)
        assertEquals(1, m.position)
    }

    // ---- Chrome：子串 rom = 766 ----

    @Test
    fun `chrome substring L4`() {
        val m = T9Matcher.match("766", idx("Chrome"))!!
        assertEquals(MatchResult.L4, m.level)
        assertEquals(2, m.position)
    }

    // ---- 重命名工具：多音字 重 zhong/chong 两种读音均可命中 ----

    @Test
    fun `polyphone zhong reading L2`() {
        val m = T9Matcher.match("9466464", idx("重命名工具"))!!
        assertEquals(MatchResult.L2, m.level)
    }

    @Test
    fun `polyphone chong reading L2`() {
        val m = T9Matcher.match("2466464", idx("重命名工具"))!!
        assertEquals(MatchResult.L2, m.level)
    }

    // ---- Gmail = 46245（纯字母名 full==initials → L1） ----

    @Test
    fun `gmail L1`() {
        val m = T9Matcher.match("46245", idx("Gmail"))!!
        assertEquals(MatchResult.L1, m.level)
    }

    // ---- 9 键同时命中 W/X/Y/Z、含 0、含特殊字符的应用（spec 7.1） ----

    @Test
    fun `key9 hits wxyz zero and special chars`() {
        assertNotNull(T9Matcher.match("9", idx("微信"))) // w → 9
        assertNotNull(T9Matcher.match("9", idx("12306"))) // 0 → 9
        assertNotNull(T9Matcher.match("9", idx("1DM+"))) // + → 9
    }

    // ---- v3.4 排序语义：首字母前缀(L1) 优先于 全拼前缀(L2) ----

    @Test
    fun `initials prefix outranks full pinyin prefix`() {
        // 微信 initials "99" 为 L1；某应用全拼恰以 99 开头（如 word=9673? 构造：label "Zxx"?）
        // 用真实 fixture：query "93" 命中 微信 全拼前缀(L2)；命中 微信 initials 前缀(L1)
        val m = T9Matcher.match("9", idx("微信"))!!
        assertEquals(MatchResult.L1, m.level) // 9 是 initials "99" 的前缀 → L1 优先于全拼前缀
    }

    // ---- 边界 ----

    @Test
    fun `no match returns null`() {
        assertNull(T9Matcher.match("5555", idx("微信"))) // 934946 不含 5555，也无 j/k/l 音
    }

    @Test
    fun `invalid query chars return null`() {
        assertNull(T9Matcher.match("23a", idx("微信")))
        assertNull(T9Matcher.match("1", idx("微信"))) // '1' 不是合法查询键（无键产生它）
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty query rejected`() {
        T9Matcher.match("", idx("微信"))
    }
}
