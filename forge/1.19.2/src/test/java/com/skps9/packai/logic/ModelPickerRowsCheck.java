package com.skps9.packai.logic;

import java.util.List;

import com.skps9.packai.client.gui.ModelPickerScreen;
import com.skps9.packai.client.gui.ModelPickerScreen.Row;
import com.skps9.packai.client.gui.ModelPickerScreen.Target;

/**
 * Headless: unified picker row build / filter / target mapping. Run with -ea.
 */
public final class ModelPickerRowsCheck {
    private ModelPickerRowsCheck() {}

    public static void main(String[] args) {
        bothSections();
        emptyCloudShowsStatus();
        emptyLocalOmittedWhenNoCurrent();
        filterKeepsHeader();
        filterDropsEmptySection();
        targetMapping();
        System.out.println("ModelPickerRowsCheck OK");
    }

    static void bothSections() {
        List<Row> rows = ModelPickerScreen.buildRows(
                List.of("gpt-4o-mini", "deepseek-chat"),
                List.of("llama3.2"),
                "gpt-4o-mini",
                "llama3.2",
                true,
                true);
        assert hasHeader(rows, "packai.model_picker.section.cloud");
        assert hasHeader(rows, "packai.model_picker.section.local");
        assert hasModel(rows, Target.CLOUD, "gpt-4o-mini");
        assert hasModel(rows, Target.LOCAL, "llama3.2");
        System.out.println("bothSections OK");
    }

    static void emptyCloudShowsStatus() {
        List<Row> rows = ModelPickerScreen.buildRows(
                List.of(), List.of("llama3.2"), "deepseek-flash", "llama3.2", false, true);
        assert hasHeader(rows, "packai.model_picker.section.cloud");
        assert hasStatus(rows, "packai.model_picker.status.cloud_empty");
        assert hasModel(rows, Target.CLOUD, "deepseek-flash") : "configured cloud always listed";
        System.out.println("emptyCloudShowsStatus OK");
    }

    static void emptyLocalOmittedWhenNoCurrent() {
        List<Row> rows = ModelPickerScreen.buildRows(
                List.of("gpt-4o"), List.of(), "gpt-4o", "", true, false);
        assert hasHeader(rows, "packai.model_picker.section.cloud");
        assert hasHeader(rows, "packai.model_picker.section.local");
        assert hasStatus(rows, "packai.model_picker.status.ollama_empty");
        System.out.println("emptyLocalOmittedWhenNoCurrent OK");
    }

    static void filterKeepsHeader() {
        List<Row> all = ModelPickerScreen.buildRows(
                List.of("gpt-4o-mini", "deepseek-chat"),
                List.of("llama3.2", "mistral"),
                "gpt-4o-mini",
                "llama3.2",
                true,
                true);
        List<Row> filtered = ModelPickerScreen.filterRows(all, "llama");
        assert hasHeader(filtered, "packai.model_picker.section.local");
        assert !hasHeader(filtered, "packai.model_picker.section.cloud")
                : "cloud section must drop when no match";
        assert hasModel(filtered, Target.LOCAL, "llama3.2");
        for (Row r : filtered) {
            if (r.kind == Row.Kind.HEADER) {
                assert !r.selectable();
            }
        }
        System.out.println("filterKeepsHeader OK");
    }

    static void filterDropsEmptySection() {
        List<Row> all = ModelPickerScreen.buildRows(
                List.of("a"), List.of("b"), "a", "b", true, true);
        List<Row> filtered = ModelPickerScreen.filterRows(all, "zzz");
        assert filtered.isEmpty() : filtered;
        System.out.println("filterDropsEmptySection OK");
    }

    static void targetMapping() {
        List<Row> rows = ModelPickerScreen.buildRows(
                List.of("cloud-m"), List.of("local-m"), "cloud-m", "local-m", true, true);
        Row cloud = null;
        Row local = null;
        for (Row r : rows) {
            if (r.kind == Row.Kind.MODEL && "cloud-m".equals(r.text)) {
                cloud = r;
            }
            if (r.kind == Row.Kind.MODEL && "local-m".equals(r.text)) {
                local = r;
            }
        }
        assert cloud != null && cloud.target == Target.CLOUD;
        assert local != null && local.target == Target.LOCAL;
        System.out.println("targetMapping OK");
    }

    static boolean hasHeader(List<Row> rows, String key) {
        for (Row r : rows) {
            if (r.kind == Row.Kind.HEADER && key.equals(r.text)) {
                return true;
            }
        }
        return false;
    }

    static boolean hasStatus(List<Row> rows, String key) {
        for (Row r : rows) {
            if (r.kind == Row.Kind.STATUS && key.equals(r.text)) {
                return true;
            }
        }
        return false;
    }

    static boolean hasModel(List<Row> rows, Target t, String id) {
        for (Row r : rows) {
            if (r.kind == Row.Kind.MODEL && r.target == t && id.equals(r.text)) {
                return true;
            }
        }
        return false;
    }
}
