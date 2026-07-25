package com.skps9.packai.logic;

/**
 * Non-item / non-fluid recipe slot (Mekanism gas, slurry, chemical, etc.).
 * {@link #softId()} keys a client JEI render cache when non-blank.
 */
public record RecipeExtra(String label, long amount, int tint, String softId) {
    public RecipeExtra {
        label = label == null ? "" : label;
        softId = softId == null ? "" : softId;
        if (tint == 0) {
            tint = 0xFF6EC6FF;
        }
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
