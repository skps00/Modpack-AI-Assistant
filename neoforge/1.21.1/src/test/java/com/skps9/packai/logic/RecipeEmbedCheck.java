package com.skps9.packai.logic;

import java.util.List;

/** Purpose-first: recipe cards must not sandwich 怎么用. Run with -ea. */
public final class RecipeEmbedCheck {
    private RecipeEmbedCheck() {}

    public static void main(String[] args) {
        String sandwich = ""
                + "[[recipe_card:0]]\n"
                + "怎么用\n"
                + "1. Place the brazier.\n"
                + "2. Add source.\n"
                + "[[recipe_card:1]]\n"
                + "【来源】JEI";
        List<RecipeEmbed.Part> parts = RecipeEmbed.parts(sandwich, 2);
        int firstCard = -1;
        int useAt = -1;
        int cards = 0;
        for (int i = 0; i < parts.size(); i++) {
            RecipeEmbed.Part p = parts.get(i);
            if (p.isCard()) {
                if (firstCard < 0) {
                    firstCard = i;
                }
                cards++;
            } else if (p.kind() == RecipeEmbed.Kind.TEXT
                    && p.text() != null
                    && p.text().contains("怎么用")
                    && useAt < 0) {
                useAt = i;
            }
        }
        assert useAt >= 0 : parts;
        assert firstCard > useAt : "card before 怎么用: " + describe(parts);
        assert cards == 2 : describe(parts);

        String alreadyAfter = ""
                + "怎么用\n"
                + "1. Throw the gem.\n"
                + "[[recipe_card:0]]\n"
                + "[[recipe_card:1]]";
        List<RecipeEmbed.Part> after = RecipeEmbed.parts(alreadyAfter, 2);
        assert after.get(0).kind() == RecipeEmbed.Kind.TEXT : describe(after);
        assert after.get(0).text().contains("怎么用") : describe(after);

        String craftFirst = "[[recipe_card:0]]\n怎么来\n1. Craft it.";
        List<RecipeEmbed.Part> craft = RecipeEmbed.parts(craftFirst, 1);
        assert craft.get(0).isCard() : describe(craft);

        List<RecipeEmbed.Part> blob = new java.util.ArrayList<>(RecipeEmbed.parts(
                "怎么用\nplace it.\n怎么来\ncraft at workbench.\n【来源】JEI、PURPOSE", 0));
        RecipeEmbed.splitTrailingSources(blob);
        blob.add(RecipeEmbed.insertObtainClusterAt(blob), RecipeEmbed.Part.card(0));
        int src = -1;
        int card = -1;
        int get = -1;
        for (int i = 0; i < blob.size(); i++) {
            RecipeEmbed.Part p = blob.get(i);
            if (p.isCard()) {
                card = i;
            } else if (p.text() != null && p.text().contains("【来源】")) {
                src = i;
            } else if (p.text() != null && p.text().contains("怎么来") && get < 0) {
                get = i;
            }
        }
        assert get >= 0 && card > get && src > card : describe(blob);

        List<RecipeEmbed.Part> noGet = new java.util.ArrayList<>(RecipeEmbed.parts(
                "怎么用\nfoo\n[[recipe_card:0]]\n【来源】JEI", 1));
        RecipeEmbed.splitTrailingSources(noGet);
        noGet.add(RecipeEmbed.insertObtainClusterAt(noGet), RecipeEmbed.Part.card(99));
        int src2 = -1;
        int lastCard = -1;
        int card0 = -1;
        for (int i = 0; i < noGet.size(); i++) {
            RecipeEmbed.Part p = noGet.get(i);
            if (p.isCard() && p.cardIndex() == 0) {
                card0 = i;
            }
            if (p.isCard()) {
                lastCard = i;
            } else if (p.text() != null && p.text().contains("【来源】")) {
                src2 = i;
            }
        }
        assert card0 >= 0 && lastCard == card0 + 1 && src2 > lastCard : describe(noGet);

        String uOnly = "0 | role=input | Mechanical Crafter | x → y\n";
        assert !AskEngine.hasObtainRecipes(true, uOnly) : uOnly;
        assert AskEngine.hasObtainRecipes(true, "0 | role=output | Crafting | a → b\n");
        assert AskEngine.hasObtainRecipes(true, "JEI dump without catalog");
        assert !AskEngine.hasObtainRecipes(false, uOnly);

        System.out.println("RecipeEmbedCheck OK");
    }

    private static String describe(List<RecipeEmbed.Part> parts) {
        StringBuilder sb = new StringBuilder();
        for (RecipeEmbed.Part p : parts) {
            sb.append(p.kind()).append(':').append(p.isCard() ? p.cardIndex() : p.text()).append('|');
        }
        return sb.toString();
    }
}
