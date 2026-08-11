package com.skps9.packai.client.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skps9.packai.client.chat.ChatSession;
import com.skps9.packai.client.service.AskService;
import com.skps9.packai.compat.CuriosBridge;
import com.skps9.packai.logic.ItemRef;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Pick which inventory stacks to send with the next Pack AI ask (multi-select).
 */
public class InvPickScreen extends Screen {
    private static final int SLOT = 18;
    private static final int PAD = 2;

    private final Screen parent;
    private final List<String> slotOrder = new ArrayList<>();
    private final Set<String> selected = new LinkedHashSet<>();
    private String status = "";

    public InvPickScreen(Screen parent) {
        super(Component.translatable("packai.invpick.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rebuildSlotKeys();
        seedSelectionFromPending();
        int left = (this.width - 200) / 2;
        int bottom = this.height - 28;
        this.addRenderableWidget(WidgetCompat.button(left, bottom, 96, 20,
                Component.translatable("packai.invpick.clear"), b -> {
                    this.selected.clear();
                    this.status = "";
                }, Component.translatable("packai.invpick.tooltip.clear")));
        this.addRenderableWidget(WidgetCompat.button(left + 104, bottom, 96, 20,
                Component.translatable("gui.done"), b -> finish(),
                Component.translatable("packai.invpick.tooltip.done")));
    }

    private void rebuildSlotKeys() {
        this.slotOrder.clear();
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        for (int i = 9; i < 36; i++) {
            this.slotOrder.add("inv:" + i);
        }
        for (int i = 0; i < 9; i++) {
            this.slotOrder.add("hotbar:" + i);
        }
        for (int i = 0; i < 4; i++) {
            this.slotOrder.add("armor:" + i);
        }
        this.slotOrder.add("offhand");
        if (CuriosBridge.isLoaded()) {
            CuriosBridge.appendSlotKeys(this.minecraft.player, this.slotOrder);
        }
    }

    private void seedSelectionFromPending() {
        this.selected.clear();
        List<ItemRef> pending = ChatSession.pendingItems();
        if (pending.isEmpty() || this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        Set<String> want = new LinkedHashSet<>();
        for (ItemRef ref : pending) {
            if (ref.isPresent()) {
                want.add(ref.id().toLowerCase(Locale.ROOT));
            }
        }
        for (String key : this.slotOrder) {
            ItemRef ref = AskService.fromStack(stackAt(key));
            if (ref.isPresent() && want.contains(ref.id().toLowerCase(Locale.ROOT))) {
                this.selected.add(key);
            }
        }
    }

    private ItemStack stackAt(String key) {
        LocalPlayer player = this.minecraft == null ? null : this.minecraft.player;
        if (player == null || key == null) {
            return ItemStack.EMPTY;
        }
        Inventory inv = player.getInventory();
        if (key.startsWith("inv:")) {
            return inv.getItem(Integer.parseInt(key.substring(4)));
        }
        if (key.startsWith("hotbar:")) {
            return inv.getItem(Integer.parseInt(key.substring(7)));
        }
        if (key.startsWith("armor:")) {
            return inv.armor.get(Integer.parseInt(key.substring(6)));
        }
        if ("offhand".equals(key)) {
            return inv.offhand.get(0);
        }
        if (key.startsWith("curios:")) {
            return CuriosBridge.stackAt(player, key);
        }
        return ItemStack.EMPTY;
    }

    private void finish() {
        List<ItemRef> picked = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String key : this.slotOrder) {
            if (!this.selected.contains(key)) {
                continue;
            }
            ItemRef ref = AskService.fromStack(stackAt(key));
            if (!ref.isPresent() || !seen.add(AskService.selectionKey(ref))) {
                continue;
            }
            picked.add(ref);
            if (picked.size() >= ChatSession.MAX_PENDING_ITEMS) {
                break;
            }
        }
        ChatSession.setPendingItems(picked);
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void onClose() {
        finish();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            String hit = hitSlot((int) mouseX, (int) mouseY);
            if (hit != null) {
                ItemStack stack = stackAt(hit);
                if (stack.isEmpty()) {
                    return true;
                }
                if (this.selected.contains(hit)) {
                    this.selected.remove(hit);
                    this.status = "";
                } else if (distinctSelectedCount() >= ChatSession.MAX_PENDING_ITEMS
                        && !selectedContainsKey(AskService.selectionKey(AskService.fromStack(stack)))) {
                    this.status = Component.translatable(
                            "packai.invpick.cap", ChatSession.MAX_PENDING_ITEMS).getString();
                } else {
                    this.selected.add(hit);
                    this.status = "";
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean selectedContainsKey(String selKey) {
        if (selKey == null || selKey.isBlank()) {
            return false;
        }
        for (String key : this.selected) {
            ItemRef ref = AskService.fromStack(stackAt(key));
            if (ref.isPresent() && AskService.selectionKey(ref).equals(selKey)) {
                return true;
            }
        }
        return false;
    }

    private int distinctSelectedCount() {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (String key : this.selected) {
            ItemRef ref = AskService.fromStack(stackAt(key));
            if (ref.isPresent()) {
                keys.add(AskService.selectionKey(ref));
            }
        }
        return keys.size();
    }

    private String hitSlot(int mx, int my) {
        for (Map.Entry<String, int[]> e : layoutSlots().entrySet()) {
            int x = e.getValue()[0];
            int y = e.getValue()[1];
            if (mx >= x && mx < x + SLOT && my >= y && my < y + SLOT) {
                return e.getKey();
            }
        }
        return null;
    }

    private Map<String, int[]> layoutSlots() {
        Map<String, int[]> out = new LinkedHashMap<>();
        int gridW = 9 * (SLOT + PAD) - PAD;
        int left = (this.width - gridW) / 2;
        int top = 40;
        int armorX = left - (SLOT + PAD) - 8;
        for (int i = 3; i >= 0; i--) {
            out.put("armor:" + i, new int[]{armorX, top + (3 - i) * (SLOT + PAD)});
        }
        out.put("offhand", new int[]{armorX, top + 4 * (SLOT + PAD) + 4});
        int idx = 0;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                out.put("inv:" + (9 + idx), new int[]{
                        left + col * (SLOT + PAD),
                        top + row * (SLOT + PAD)
                });
                idx++;
            }
        }
        int hotTop = top + 3 * (SLOT + PAD) + 8;
        for (int col = 0; col < 9; col++) {
            out.put("hotbar:" + col, new int[]{left + col * (SLOT + PAD), hotTop});
        }
        if (CuriosBridge.isLoaded()) {
            int curiosTop = hotTop + SLOT + PAD + 10;
            int col = 0;
            int row = 0;
            for (String key : this.slotOrder) {
                if (!key.startsWith("curios:")) {
                    continue;
                }
                out.put(key, new int[]{
                        left + col * (SLOT + PAD),
                        curiosTop + row * (SLOT + PAD)
                });
                col++;
                if (col >= 9) {
                    col = 0;
                    row++;
                }
            }
        }
        return out;
    }

    private void renderScreen(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics.pose());
        super.render(graphics.pose(), mouseX, mouseY, partialTick);
        WidgetCompat.renderHoveredTips(this, graphics.pose(), mouseX, mouseY);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.translatable(
                        "packai.invpick.count", distinctSelectedCount(), ChatSession.MAX_PENDING_ITEMS),
                this.width / 2, 28, 0xAAAAAA);
        if (CuriosBridge.isLoaded()) {
            int gridW = 9 * (SLOT + PAD) - PAD;
            int left = (this.width - gridW) / 2;
            int hotTop = 40 + 3 * (SLOT + PAD) + 8;
            int labelY = hotTop + SLOT + PAD + 1;
            graphics.drawString(this.font,
                    Component.translatable("packai.invpick.curios"), left, labelY, 0xAAAAAA, false);
        }
        if (!this.status.isEmpty()) {
            graphics.drawCenteredString(this.font, this.status, this.width / 2, this.height - 48, 0xFFAAAA);
        }
        String hoverKey = null;
        for (Map.Entry<String, int[]> e : layoutSlots().entrySet()) {
            String key = e.getKey();
            int x = e.getValue()[0];
            int y = e.getValue()[1];
            ItemStack stack = stackAt(key);
            boolean on = this.selected.contains(key);
            graphics.fill(x - 1, y - 1, x + SLOT + 1, y + SLOT + 1, on ? 0x8866AAFF : 0x66000000);
            graphics.fill(x, y, x + SLOT, y + SLOT, 0xFF373737);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, x + 1, y + 1);
                graphics.renderItemDecorations(this.font, stack, x + 1, y + 1);
            }
            if (mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + SLOT) {
                hoverKey = key;
            }
        }
        if (hoverKey != null) {
            ItemStack stack = stackAt(hoverKey);
            if (!stack.isEmpty()) {
                graphics.renderTooltip(this.font, stack, mouseX, mouseY);
            }
        }
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        renderScreen(new GuiGraphics(this.minecraft, this, pose), mouseX, mouseY, partialTick);
    }
}
