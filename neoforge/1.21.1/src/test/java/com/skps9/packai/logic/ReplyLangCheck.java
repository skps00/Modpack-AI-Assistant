package com.skps9.packai.logic;

/** Runnable check: Minecraft lang codes map to reply-language names. */
public final class ReplyLangCheck {
    private ReplyLangCheck() {}

    public static void main(String[] args) {
        assert "繁體中文".equals(LlmClient.replyLanguageName("zh_tw"));
        assert "繁體中文".equals(LlmClient.replyLanguageName("zh_hk"));
        assert "简体中文".equals(LlmClient.replyLanguageName("zh_cn"));
        assert "English".equals(LlmClient.replyLanguageName("en_us"));
        assert "English".equals(LlmClient.replyLanguageName("en_gb"));
        assert "日本語".equals(LlmClient.replyLanguageName("ja_jp"));
        assert LlmClient.replyLanguageName("sv_se").contains("sv_se");
        System.out.println("ReplyLangCheck OK");
    }
}
