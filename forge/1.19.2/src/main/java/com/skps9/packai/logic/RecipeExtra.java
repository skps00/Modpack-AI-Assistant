package com.skps9.packai.logic;

/**
 * Non-item / non-fluid recipe slot (Mekanism gas, slurry, chemical, entity, etc.).
 * {@link #softId()} keys a client JEI render cache when non-blank.
 * {@link #uniqueId()} is a helper resource id when JEI provides one — never invented.
 */
public record RecipeExtra(String label, long amount, int tint, String softId, String uniqueId) {
    public RecipeExtra {
        label = label == null ? "" : label;
        softId = softId == null ? "" : softId;
        uniqueId = uniqueId == null ? "" : uniqueId;
        if (tint == 0) {
            tint = 0xFF6EC6FF;
        }
    }

    public RecipeExtra(String label, long amount, int tint, String softId) {
        this(label, amount, tint, softId, "");
    }

    public boolean isEmpty() {
        return label.isBlank() && softId.isBlank();
    }

    public String tooltipLine() {
        if (amount > 0) {
            return label + " (" + amount + " mB)";
        }
        return label;
    }
}
