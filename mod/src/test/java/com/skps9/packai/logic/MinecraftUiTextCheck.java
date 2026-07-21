package com.skps9.packai.logic;

/** Self-check: strip emoji + markdown for Minecraft UI. */
public final class MinecraftUiTextCheck {
    private MinecraftUiTextCheck() {}

    public static void main(String[] args) {
        String raw = "## 標題\n### 1🧪 測試\n**粗體** 與 `code` → 完成";
        String out = Plainify.forMinecraftUi(raw);
        assert !out.contains("#") : out;
        assert !out.contains("**") : out;
        assert !out.contains("`") : out;
        assert !out.contains("🧪") : out;
        assert out.contains("粗體") && out.contains("code") && out.contains("->") : out;
        System.out.println("MinecraftUiTextCheck OK: " + out.replace("\n", " | "));
    }
}
