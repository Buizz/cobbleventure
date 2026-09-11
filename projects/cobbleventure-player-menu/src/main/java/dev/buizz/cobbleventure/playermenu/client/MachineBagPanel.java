package dev.buizz.cobbleventure.playermenu.client;

import dev.buizz.cobbleventure.playermenu.BagNetwork;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Read-only shared bag panel used beside registered machine functions. */
final class MachineBagPanel {
    private static final int ROW_HEIGHT = 20;
    private final Minecraft minecraft;
    private final Font font;
    private final MenuTheme theme;
    private List<BagItemCatalog.Entry> entries = List.of();
    private EditBox searchBox;
    private long snapshotRevision = Long.MIN_VALUE;
    private int x;
    private int y;
    private int width;
    private int height;
    private int scroll;

    MachineBagPanel(Minecraft minecraft, Font font, MenuTheme theme) {
        this.minecraft = minecraft;
        this.font = font;
        this.theme = theme;
    }

    EditBox initialize(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        searchBox = new EditBox(
            font, x + 10, y + 31, width - 20, 18,
            Component.translatable("screen.cobbleventure_player_menu.bag.search")
        );
        searchBox.setHint(Component.translatable("screen.cobbleventure_player_menu.bag.search"));
        searchBox.setMaxLength(48);
        searchBox.setBordered(false);
        searchBox.setTextColor(theme.textColor);
        searchBox.setTextColorUneditable(theme.mutedTextColor);
        searchBox.setResponder(ignored -> refresh(true));
        refresh(true);
        return searchBox;
    }

    void tick() {
        long revision = BagNetwork.clientSnapshot().revision();
        if (revision != snapshotRevision) refresh(false);
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY) {
        ThemedOverlayPanel.draw(graphics, theme, x, y, width, height);
        theme.drawText(
            graphics, font,
            Component.translatable("screen.cobbleventure_player_menu.tm_workshop.bag"),
            x + 12, y + 12, MenuTheme.TextRole.HEADING
        );
        ThemedOverlayPanel.fillRoundedRect(
            graphics, x + 8, y + 29, x + width - 8, y + 51,
            Math.min(theme.rowRadius, 8), theme.border
        );
        ThemedOverlayPanel.fillRoundedRect(
            graphics, x + 9, y + 30, x + width - 9, y + 50,
            Math.max(0, Math.min(theme.rowRadius, 8) - 1), theme.inputBackground
        );

        int visibleRows = visibleRows();
        for (int row = 0; row < visibleRows; row++) {
            int index = scroll + row;
            if (index >= entries.size()) break;
            BagItemCatalog.Entry entry = entries.get(index);
            int rowY = listY() + row * ROW_HEIGHT;
            boolean hovered = mouseX >= x + 8 && mouseX < x + width - 8
                && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 1;
            if (hovered) ThemedOverlayPanel.fillRoundedRect(
                graphics, x + 8, rowY, x + width - 8, rowY + ROW_HEIGHT - 1,
                Math.min(theme.rowRadius, 6), theme.hoverBackground
            );
            graphics.renderItem(entry.stack(), x + 10, rowY + 1);
            int countWidth = font.width("×" + entry.count());
            int nameWidth = Math.max(12, width - 44 - countWidth);
            theme.drawText(
                graphics, font,
                Component.literal(font.plainSubstrByWidth(entry.stack().getHoverName().getString(), nameWidth)),
                x + 29, rowY + 5, MenuTheme.TextRole.LABEL
            );
            theme.drawText(
                graphics, font, Component.literal("×" + entry.count()),
                x + width - 11 - countWidth, rowY + 5, MenuTheme.TextRole.CAPTION,
                theme.secondaryTextColor
            );
        }

        Component footer = entries.isEmpty()
            ? Component.translatable("screen.cobbleventure_player_menu.tm_workshop.bag_empty")
            : Component.translatable("screen.cobbleventure_player_menu.tm_workshop.bag_count", entries.size());
        theme.drawText(
            graphics, font, footer, x + 10, y + height - 15,
            MenuTheme.TextRole.CAPTION, theme.mutedTextColor
        );
    }

    void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (mouseY < listY()) return;
        int row = (mouseY - listY()) / ROW_HEIGHT;
        int index = scroll + row;
        if (mouseX < x + 8 || mouseX >= x + width - 8 || row < 0 || row >= visibleRows()
            || index < 0 || index >= entries.size()) return;
        graphics.renderTooltip(font, entries.get(index).stack(), mouseX, mouseY);
    }

    boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) return false;
        int maximum = Math.max(0, entries.size() - visibleRows());
        scroll = Math.clamp(scroll - (int)Math.signum(scrollY), 0, maximum);
        return true;
    }

    private void refresh(boolean resetScroll) {
        snapshotRevision = BagNetwork.clientSnapshot().revision();
        if (minecraft.player == null) {
            entries = List.of();
        } else {
            entries = BagItemCatalog.aggregate(
                minecraft.player,
                BagNetwork.clientSnapshot().slots(),
                BagItemCatalog.Category.ALL,
                searchBox == null ? "" : searchBox.getValue()
            );
        }
        if (resetScroll) scroll = 0;
        scroll = Math.clamp(scroll, 0, Math.max(0, entries.size() - visibleRows()));
    }

    private int listY() {
        return y + 56;
    }

    private int visibleRows() {
        return Math.max(1, (height - 75) / ROW_HEIGHT);
    }
}
