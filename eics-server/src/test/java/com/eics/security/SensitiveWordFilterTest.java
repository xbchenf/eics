package com.eics.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SensitiveWordFilter 单元测试 — DFA 匹配 + 脱敏
 */
class SensitiveWordFilterTest {

    private SensitiveWordFilter filter;

    @BeforeEach
    void setUp() {
        filter = new SensitiveWordFilter();
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "customWords", "法轮功,赌博");
        filter.init();
    }

    @Test
    void testContainsSensitiveWord() {
        assertTrue(filter.contains("你是个傻逼"));
        assertTrue(filter.contains("what the fuck"));
        assertFalse(filter.contains("你好，请问怎么办理"));
    }

    @Test
    void testFindAll() {
        List<String> words = filter.findAll("傻逼和蠢货都是不好的词");
        assertEquals(2, words.size());
        assertTrue(words.contains("傻逼"));
        assertTrue(words.contains("蠢货"));
    }

    @Test
    void testReplace() {
        String result = filter.replace("你真是个傻逼");
        assertEquals("你真是个**", result);
    }

    @Test
    void testReplaceEnglish() {
        String result = filter.replace("what the fuck is this shit");
        assertEquals("what the **** is this ****", result);
    }

    @Test
    void testReplaceMultipleOccurrences() {
        String result = filter.replace("傻逼说另一人也是傻逼");
        assertEquals("**说另一人也是**", result);
    }

    @Test
    void testDisabled() {
        ReflectionTestUtils.setField(filter, "enabled", false);
        assertFalse(filter.contains("傻逼"));
        assertEquals("hello world", filter.replace("hello world"));
    }

    @Test
    void testNullAndEmpty() {
        assertFalse(filter.contains(null));
        assertFalse(filter.contains(""));
        assertNull(filter.replace(null));
    }

    @Test
    void testMaskPhone() {
        assertEquals("134****1234", SensitiveWordFilter.maskPhone("13412341234"));
        assertEquals("139****5678", SensitiveWordFilter.maskPhone("13912345678"));
        assertEquals("no phone here", SensitiveWordFilter.maskPhone("no phone here"));
    }

    @Test
    void testMaskIdCard() {
        assertEquals("110101****1234", SensitiveWordFilter.maskIdCard("110101199001011234"));
    }

    @Test
    void testMaskEmail() {
        assertEquals("a**@example.com", SensitiveWordFilter.maskEmail("abc@example.com"));
    }

    @Test
    void testSanitize() {
        String result = filter.sanitize("你好傻逼，手机13800138000");
        assertTrue(result.contains("**"));
        assertTrue(result.contains("138****8000"));
    }
}
