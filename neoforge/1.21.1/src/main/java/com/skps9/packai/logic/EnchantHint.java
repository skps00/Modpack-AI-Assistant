package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic vanilla [ENCHANT_TABLE] hint (v2, 2026-09-05): given an item id and its
 * tool-action lines, classify the item kind and return a compact per-language list of
 * applicable VANILLA enchantments (name, max level, one-line effect). Mod-only
 * enchantments are NOT listed — JEI book recipes still cover those per book.
 * Pure data — byte-identical across loader trees.
 */
public final class EnchantHint {

    private EnchantHint() {
    }

    private record Entry(String name, int maxLevel, String en, String zhCn, String zhTw) {
    }

    /** Sword-like: sharpness etc. */
    private static final List<Entry> SWORD = List.of(
            new Entry("锋利 Sharpness", 5, "increases melee damage", "提高近战伤害", "提高近戰傷害"),
            new Entry("亡灵杀手 Smite", 5, "extra damage vs undead", "对亡灵生物加伤", "對不死生物加傷"),
            new Entry("节肢杀手 Bane of Arthropods", 5, "extra damage vs spiders/bees etc.", "对节肢动物加伤", "對節肢動物加傷"),
            new Entry("击退 Knockback", 2, "knocks enemies back", "击退敌人", "擊退敵人"),
            new Entry("火焰附加 Fire Aspect", 2, "sets targets on fire", "点燃目标", "點燃目標"),
            new Entry("抢夺 Looting", 3, "more drops from mobs", "提高生物掉落", "提高生物掉落"),
            new Entry("横扫之刃 Sweeping Edge", 3, "more damage to sweep targets", "提高横扫伤害", "提高橫掃傷害"),
            new Entry("耐久 Unbreaking", 3, "slows durability loss", "降低耐久消耗", "降低耐久消耗"),
            new Entry("经验修补 Mending", 1, "repairs with XP orbs", "用经验修补耐久", "用經驗修補耐久"),
            new Entry("消失诅咒 Curse of Vanishing", 1, "item vanishes on death", "死亡时物品消失", "死亡時物品消失"));

    private static final List<Entry> AXE = List.of(
            new Entry("锋利 Sharpness", 5, "increases melee damage", "提高近战伤害", "提高近戰傷害"),
            new Entry("亡灵杀手 Smite", 5, "extra damage vs undead", "对亡灵生物加伤", "對不死生物加傷"),
            new Entry("节肢杀手 Bane of Arthropods", 5, "extra damage vs spiders/bees etc.", "对节肢动物加伤", "對節肢動物加傷"),
            new Entry("效率 Efficiency", 5, "faster block breaking", "提高挖掘速度", "提高挖掘速度"),
            new Entry("精准采集 Silk Touch", 1, "blocks drop themselves (exclusive with Fortune)", "方块掉落本体（与时运互斥）", "方塊掉落本體（與幸運互斥）"),
            new Entry("时运 Fortune", 3, "more block drops (exclusive with Silk Touch)", "提高方块掉落（与精准采集互斥）", "提高方塊掉落（與絲綢之觸互斥）"),
            new Entry("耐久 Unbreaking", 3, "slows durability loss", "降低耐久消耗", "降低耐久消耗"),
            new Entry("经验修补 Mending", 1, "repairs with XP orbs", "用经验修补耐久", "用經驗修補耐久"),
            new Entry("消失诅咒 Curse of Vanishing", 1, "item vanishes on death", "死亡时物品消失", "死亡時物品消失"));

    private static final List<Entry> TOOL = List.of(
            new Entry("效率 Efficiency", 5, "faster block breaking", "提高挖掘速度", "提高挖掘速度"),
            new Entry("精准采集 Silk Touch", 1, "blocks drop themselves (exclusive with Fortune)", "方块掉落本体（与时运互斥）", "方塊掉落本體（與幸運互斥）"),
            new Entry("时运 Fortune", 3, "more block drops (exclusive with Silk Touch)", "提高方块掉落（与精准采集互斥）", "提高方塊掉落（與絲綢之觸互斥）"),
            new Entry("耐久 Unbreaking", 3, "slows durability loss", "降低耐久消耗", "降低耐久消耗"),
            new Entry("经验修补 Mending", 1, "repairs with XP orbs", "用经验修补耐久", "用經驗修補耐久"),
            new Entry("消失诅咒 Curse of Vanishing", 1, "item vanishes on death", "死亡时物品消失", "死亡時物品消失"));

    private static final List<Entry> HOE = List.of(
            new Entry("效率 Efficiency", 5, "faster tilling/block breaking", "提高耕地/挖掘速度", "提高耕地/挖掘速度"),
            new Entry("耐久 Unbreaking", 3, "slows durability loss", "降低耐久消耗", "降低耐久消耗"),
            new Entry("经验修补 Mending", 1, "repairs with XP orbs", "用经验修补耐久", "用經驗修補耐久"),
            new Entry("消失诅咒 Curse of Vanishing", 1, "item vanishes on death", "死亡时物品消失", "死亡時物品消失"));

    private static final List<Entry> BOW = List.of(
            new Entry("力量 Power", 5, "more arrow damage", "提高箭矢伤害", "提高箭矢傷害"),
            new Entry("冲击 Punch", 2, "knocks targets back", "击退目标", "擊退目標"),
            new Entry("火矢 Flame", 1, "arrows set targets on fire", "箭矢点燃目标", "箭矢點燃目標"),
            new Entry("无限 Infinity", 1, "normal arrows never consumed (exclusive with Mending)", "普通箭矢不消耗（与经验修补互斥）", "普通箭矢不消耗（與經驗修補互斥）"),
            new Entry("耐久 Unbreaking", 3, "slows durability loss", "降低耐久消耗", "降低耐久消耗"),
            new Entry("经验修补 Mending", 1, "repairs with XP orbs", "用经验修补耐久", "用經驗修補耐久"),
            new Entry("消失诅咒 Curse of Vanishing", 1, "item vanishes on death", "死亡时物品消失", "死亡時物品消失"));

    private static final List<Entry> CROSSBOW = List.of(
            new Entry("快速装填 Quick Charge", 3, "faster reloading", "加快装填", "加快裝填"),
            new Entry("多重射击 Multishot", 1, "fires 3 arrows (exclusive with Piercing)", "一次射3箭（与穿透互斥）", "一次射3箭（與貫穿互斥）"),
            new Entry("穿透 Piercing", 4, "arrows pierce entities (exclusive with Multishot)", "箭矢穿透实体（与多重射击互斥）", "箭矢貫穿實體（與多重射擊互斥）"),
            new Entry("耐久 Unbreaking", 3, "slows durability loss", "降低耐久消耗", "降低耐久消耗"),
            new Entry("经验修补 Mending", 1, "repairs with XP orbs", "用经验修补耐久", "用經驗修補耐久"),
            new Entry("消失诅咒 Curse of Vanishing", 1, "item vanishes on death", "死亡时物品消失", "死亡時物品消失"));

    private static final List<Entry> TRIDENT = List.of(
            new Entry("忠诚 Loyalty", 3, "trident returns after throw", "掷出后返回", "擲出後返回"),
            new Entry("引雷 Channeling", 1, "strikes lightning on hit (thunder only)", "雷雨天命中引雷", "雷雨天命中引雷"),
            new Entry("激流 Riptide", 3, "launches you with the trident in water/rain", "水中/雨天掷出可冲刺（与忠诚/引雷互斥）", "水中/雨天擲出可衝刺（與忠誠/引雷互斥）"),
            new Entry("穿刺 Impaling", 5, "extra damage vs aquatic mobs", "对水生生物加伤", "對水生生物加傷"),
            new Entry("耐久 Unbreaking", 3, "slows durability loss", "降低耐久消耗", "降低耐久消耗"),
            new Entry("经验修补 Mending", 1, "repairs with XP orbs", "用经验修补耐久", "用經驗修補耐久"),
            new Entry("消失诅咒 Curse of Vanishing", 1, "item vanishes on death", "死亡时物品消失", "死亡時物品消失"));

    private static final List<Entry> ARMOR_COMMON = List.of(
            new Entry("保护 Protection", 4, "reduces most damage (exclusive with fire/blast/projectile)", "减免大部分伤害（与火焰/爆炸/弹射物保护互斥）", "減免大部分傷害（與火焰/爆炸/投射物保護互斥）"),
            new Entry("火焰保护 Fire Protection", 4, "reduces fire damage", "减免火焰伤害", "減免火焰傷害"),
            new Entry("爆炸保护 Blast Protection", 4, "reduces explosion damage", "减免爆炸伤害", "減免爆炸傷害"),
            new Entry("弹射物保护 Projectile Protection", 4, "reduces projectile damage", "减免弹射物伤害", "減免投射物傷害"),
            new Entry("荆棘 Thorns", 3, "damages attackers", "反伤攻击者", "反傷攻擊者"),
            new Entry("耐久 Unbreaking", 3, "slows durability loss", "降低耐久消耗", "降低耐久消耗"),
            new Entry("经验修补 Mending", 1, "repairs with XP orbs", "用经验修补耐久", "用經驗修補耐久"),
            new Entry("绑定诅咒 Curse of Binding", 1, "armor cannot be removed", "装备后无法脱下", "裝備後無法脫下"),
            new Entry("消失诅咒 Curse of Vanishing", 1, "item vanishes on death", "死亡时物品消失", "死亡時物品消失"));

    private static final List<Entry> HELMET = List.of(
            new Entry("水下呼吸 Respiration", 3, "extends underwater air", "延长水下氧气", "延長水下氧氣"),
            new Entry("水下速掘 Aqua Affinity", 1, "faster underwater mining", "加快水下挖掘", "加快水下挖掘"));

    private static final List<Entry> LEGS = List.of(
            new Entry("迅捷潜行 Swift Sneak", 3, "move faster while sneaking", "潜行时加速移动", "潛行時加速移動"));

    private static final List<Entry> BOOTS = List.of(
            new Entry("摔落保护 Feather Falling", 4, "reduces fall damage", "减免摔落伤害", "減免摔落傷害"),
            new Entry("深海探索者 Depth Strider", 3, "faster in water", "提高水中移动", "提高水中移動"),
            new Entry("冰霜行者 Frost Walker", 2, "freezes water underfoot", "行走冻结水面", "行走凍結水面"),
            new Entry("灵魂疾行 Soul Speed", 3, "faster on soul sand/soil", "灵魂沙上加速", "靈魂砂上加速"));

    private static final List<Entry> HELMET_FULL = concat(ARMOR_COMMON, HELMET);
    private static final List<Entry> CHEST_FULL = concat(ARMOR_COMMON, List.of());
    private static final List<Entry> LEGS_FULL = concat(ARMOR_COMMON, LEGS);
    private static final List<Entry> BOOTS_FULL = concat(ARMOR_COMMON, BOOTS);

    /** Classify an item by registry path + known tool-action keywords. Empty = unknown. */
    public static String classify(String itemId, String toolActions) {
        if (itemId == null || itemId.isBlank()) {
            return "";
        }
        String id = itemId.toLowerCase(Locale.ROOT);
        String acts = toolActions == null ? "" : toolActions.toLowerCase(Locale.ROOT);
        if (id.contains("sword") || acts.contains("sword_dig") || acts.contains("sword_sweep")
                || acts.contains("sword")) {
            return "sword";
        }
        if (id.contains("axe")) {
            return "axe";
        }
        if (id.contains("pickaxe") || id.contains("_pick") || id.contains("_shovel")
                || id.contains("_shov") || id.contains("shovel")) {
            return "tool";
        }
        if (id.contains("_hoe") || id.contains("hoe")) {
            return "hoe";
        }
        if (id.contains("bow") && !id.contains("crossbow")) {
            return "bow";
        }
        if (id.contains("crossbow")) {
            return "crossbow";
        }
        if (id.contains("trident")) {
            return "trident";
        }
        if (id.contains("helmet") || id.contains("_head")) {
            return "helmet";
        }
        if (id.contains("chestplate") || id.contains("chest")) {
            return "chest";
        }
        if (id.contains("leggings") || id.contains("legs")) {
            return "legs";
        }
        if (id.contains("boots") || id.contains("_feet")) {
            return "boots";
        }
        return "";
    }

    /** Table lines for the reply language, or empty list when kind unknown. */
    public static List<String> tableFor(String kind, String replyLang) {
        List<Entry> list;
        switch (kind == null ? "" : kind) {
            case "sword":
                list = SWORD;
                break;
            case "axe":
                list = AXE;
                break;
            case "tool":
                list = TOOL;
                break;
            case "hoe":
                list = HOE;
                break;
            case "bow":
                list = BOW;
                break;
            case "crossbow":
                list = CROSSBOW;
                break;
            case "trident":
                list = TRIDENT;
                break;
            case "helmet":
                list = HELMET_FULL;
                break;
            case "chest":
                list = CHEST_FULL;
                break;
            case "legs":
                list = LEGS_FULL;
                break;
            case "boots":
                list = BOOTS_FULL;
                break;
            default:
                return List.of();
        }
        String lang = replyLang == null ? "" : replyLang.trim();
        boolean tw = lang.contains("zh_tw");
        boolean cn = lang.contains("zh_cn") || tw;
        List<String> out = new ArrayList<>(list.size());
        for (Entry e : list) {
            String effect = cn ? (tw ? e.zhTw : e.zhCn) : e.en;
            out.add("- " + e.name() + " Lv" + e.maxLevel() + "：" + effect);
        }
        return out;
    }

    private static List<Entry> concat(List<Entry> a, List<Entry> b) {
        List<Entry> out = new ArrayList<>(a.size() + b.size());
        out.addAll(a);
        out.addAll(b);
        return out;
    }
}
