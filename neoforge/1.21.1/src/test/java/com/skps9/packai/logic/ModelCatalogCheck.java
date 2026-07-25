package com.skps9.packai.logic;

/** Self-check for model list JSON parsers. */
public final class ModelCatalogCheck {
    private ModelCatalogCheck() {}

    public static void main(String[] args) {
        var cloud = ModelCatalog.parseOpenAiModels("""
                {"data":[
                  {"id":"deepseek-chat"},
                  {"id":"text-embedding-3-small"},
                  {"id":"gpt-4o-mini"}
                ]}
                """);
        assert cloud.contains("deepseek-chat") && cloud.contains("gpt-4o-mini");
        assert !cloud.contains("text-embedding-3-small");

        var ollama = ModelCatalog.parseOllamaTags("""
                {"models":[
                  {"name":"llama3.2:latest"},
                  {"name":"qwen2.5:14b"}
                ]}
                """);
        assert ollama.contains("llama3.2") && ollama.contains("qwen2.5:14b");
        System.out.println("ModelCatalogCheck OK");
    }
}
