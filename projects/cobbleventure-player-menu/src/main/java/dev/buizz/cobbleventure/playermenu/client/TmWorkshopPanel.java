package dev.buizz.cobbleventure.playermenu.client;

import com.cobblemon.mod.common.client.CobblemonClient;
import dev.buizz.cobbleventure.playermenu.BagTechnicalMachines;
import dev.buizz.cobbleventure.playermenu.MachineBagNetwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Searchable TM list, details, party compatibility, costs and craft controls. */
final class TmWorkshopPanel {
    private static final int ROW_HEIGHT = 20;
    private final Minecraft minecraft;
    private final Font font;
    private final MenuTheme theme;
    private final UUID token;
    private List<MachineBagNetwork.TmEntry> entries;
    private List<MachineBagNetwork.TmEntry> filtered = List.of();
    private ResourceLocation selectedId;
    private EditBox search;
    private ThemedButton minus;
    private ThemedButton plus;
    private ThemedButton craft;
    private ThemedButton typeButton;
    private ThemedButton unlockButton;
    private int quantity = 1;
    private String typeFilter = "";
    private boolean unlockedOnly = true;
    private boolean processing;
    private int scroll;
    private int x;
    private int y;
    private int width;
    private int height;
    private int listWidth;
    private Component status = Component.empty();
    private int statusColor;

    TmWorkshopPanel(
        Minecraft minecraft, Font font, MenuTheme theme, UUID token,
        List<MachineBagNetwork.TmEntry> entries
    ) {
        this.minecraft = minecraft;
        this.font = font;
        this.theme = theme;
        this.token = token;
        this.entries = List.copyOf(entries);
        this.statusColor = theme.mutedTextColor;
    }

    List<AbstractWidget> initialize(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.listWidth = Math.max(112, width * 42 / 100);
        search = new EditBox(
            font, x + 10, y + 31, listWidth - 20, 18,
            Component.translatable("screen.cobbleventure_player_menu.tm_workshop.search")
        );
        search.setHint(Component.translatable("screen.cobbleventure_player_menu.tm_workshop.search"));
        search.setBordered(false);
        search.setMaxLength(48);
        search.setTextColor(theme.textColor);
        search.setTextColorUneditable(theme.mutedTextColor);
        search.setResponder(ignored -> refresh(true));

        int filterWidth = Math.max(34, (listWidth - 70) / 2);
        typeButton = new ThemedButton(
            theme, Component.translatable("screen.cobbleventure_player_menu.tm_workshop.type_all"),
            x + listWidth - filterWidth * 2 - 8, y + 8, filterWidth, 18,
            MenuTheme.ButtonVariant.GHOST, this::cycleType
        );
        unlockButton = new ThemedButton(
            theme, Component.translatable("screen.cobbleventure_player_menu.tm_workshop.show_unlocked"),
            x + listWidth - filterWidth - 6, y + 8, filterWidth, 18,
            MenuTheme.ButtonVariant.GHOST, this::toggleUnlocked, () -> unlockedOnly
        );

        int actionY = y + height - 29;
        int detailX = x + listWidth + 8;
        int detailWidth = width - listWidth - 16;
        minus = new ThemedButton(theme, Component.literal("−"),
            detailX, actionY, 22, 20, MenuTheme.ButtonVariant.GHOST, () -> changeQuantity(-1));
        plus = new ThemedButton(theme, Component.literal("+"),
            detailX + 25, actionY, 22, 20, MenuTheme.ButtonVariant.GHOST, () -> changeQuantity(1));
        craft = new ThemedButton(
            theme, Component.translatable("screen.cobbleventure_player_menu.tm_workshop.craft"),
            detailX + 51, actionY, Math.max(54, detailWidth - 51), 20,
            MenuTheme.ButtonVariant.PRIMARY, this::craft
        );
        refresh(true);
        return List.of(search, typeButton, unlockButton, minus, plus, craft);
    }

    void update(List<MachineBagNetwork.TmEntry> entries, boolean success, String resultKey) {
        this.entries = List.copyOf(entries);
        processing = false;
        status = resultKey == null || resultKey.isBlank() ? Component.empty() : Component.translatable(resultKey);
        statusColor = success ? theme.success : theme.danger;
        refresh(false);
    }

    void tick() {
        updateButtons();
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY) {
        ThemedOverlayPanel.draw(graphics, theme, x, y, width, height);
        theme.drawText(graphics, font,
            Component.translatable("screen.cobbleventure_player_menu.tm_workshop.moves"),
            x + 12, y + 12, MenuTheme.TextRole.HEADING);
        drawInput(graphics, x + 8, y + 29, listWidth - 16, 22);
        renderList(graphics, mouseX, mouseY);
        renderDetails(graphics);
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        int visible = visibleRows();
        for (int row = 0; row < visible; row++) {
            int index = scroll + row;
            if (index >= filtered.size()) break;
            MachineBagNetwork.TmEntry entry = filtered.get(index);
            int rowY = listY() + row * ROW_HEIGHT;
            boolean selected = entry.id().equals(selectedId);
            boolean hovered = mouseX >= x + 8 && mouseX < x + listWidth - 8
                && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 1;
            int color = selected ? theme.selectedBackground : hovered ? theme.hoverBackground : theme.cardBackground;
            if (selected || hovered) ThemedOverlayPanel.fillRoundedRect(
                graphics, x + 8, rowY, x + listWidth - 8, rowY + ROW_HEIGHT - 1,
                Math.min(theme.rowRadius, 6), color);
            graphics.renderItem(entry.stack(), x + 10, rowY + 1);
            Component label = entry.unlocked() ? entry.name() : Component.literal("🔒 ").append(entry.name());
            theme.drawText(graphics, font,
                Component.literal(font.plainSubstrByWidth(label.getString(), listWidth - 43)),
                x + 29, rowY + 5, MenuTheme.TextRole.LABEL,
                entry.unlocked() ? theme.textColor : theme.mutedTextColor);
        }
        theme.drawText(graphics, font,
            Component.translatable("screen.cobbleventure_player_menu.tm_workshop.move_count", filtered.size()),
            x + 10, y + height - 14, MenuTheme.TextRole.CAPTION, theme.mutedTextColor);
    }

    private void renderDetails(GuiGraphics graphics) {
        MachineBagNetwork.TmEntry entry = selected();
        int detailX = x + listWidth + 10;
        int detailWidth = width - listWidth - 20;
        if (entry == null) {
            theme.drawWrappedText(graphics, font,
                Component.translatable("screen.cobbleventure_player_menu.tm_workshop.select"),
                detailX, y + 16, detailWidth, MenuTheme.TextRole.BODY, theme.mutedTextColor, 3);
            return;
        }
        theme.drawText(graphics, font, entry.name(), detailX, y + 12, MenuTheme.TextRole.HEADING);
        theme.drawText(graphics, font,
            Component.translatable(entry.unlocked()
                ? "screen.cobbleventure_player_menu.tm_workshop.unlocked"
                : "screen.cobbleventure_player_menu.tm_workshop.locked"),
            detailX, y + 28, MenuTheme.TextRole.CAPTION,
            entry.unlocked() ? theme.success : theme.warning);
        theme.drawText(graphics, font,
            Component.translatable("screen.cobbleventure_player_menu.tm_workshop.stats",
                entry.type(), entry.category(), number(entry.power()), number(entry.accuracy()), entry.pp()),
            detailX, y + 42, MenuTheme.TextRole.CAPTION, theme.secondaryTextColor);
        if (height >= 220) theme.drawWrappedText(graphics, font, entry.description(),
            detailX, y + 58, detailWidth, MenuTheme.TextRole.CAPTION, theme.mutedTextColor, 3);

        int costsY = y + (height >= 260 ? 96 : 68);
        theme.drawText(graphics, font,
            Component.translatable("screen.cobbleventure_player_menu.tm_workshop.costs", quantity),
            detailX, costsY, MenuTheme.TextRole.LABEL);
        int shownCosts = Math.min(entry.costs().size(), height >= 300 ? 4 : 2);
        for (int index = 0; index < shownCosts; index++) {
            MachineBagNetwork.CostEntry cost = entry.costs().get(index);
            int rowY = costsY + 14 + index * 18;
            graphics.renderItem(cost.stack(), detailX, rowY);
            long required = (long)cost.required() * quantity;
            boolean enough = cost.owned() >= required;
            theme.drawText(graphics, font,
                Component.literal(font.plainSubstrByWidth(cost.stack().getHoverName().getString(), detailWidth - 78)),
                detailX + 18, rowY + 4, MenuTheme.TextRole.CAPTION,
                enough ? theme.textColor : theme.danger);
            String amount = required + "/" + cost.owned();
            theme.drawText(graphics, font, Component.literal(amount),
                detailX + detailWidth - theme.textWidth(font, Component.literal(amount), MenuTheme.TextRole.CAPTION),
                rowY + 4, MenuTheme.TextRole.CAPTION, enough ? theme.success : theme.danger);
        }

        if (height >= 280) renderParty(graphics, entry, detailX, detailWidth, costsY + 90);
        theme.drawText(graphics, font,
            Component.translatable("screen.cobbleventure_player_menu.tm_workshop.quantity", quantity),
            detailX + 3, y + height - 43, MenuTheme.TextRole.CAPTION, theme.secondaryTextColor);
        if (!status.getString().isBlank()) theme.drawText(graphics, font,
            Component.literal(font.plainSubstrByWidth(status.getString(), detailWidth)),
            detailX, y + height - 56, MenuTheme.TextRole.CAPTION, statusColor);
    }

    private void renderParty(
        GuiGraphics graphics, MachineBagNetwork.TmEntry entry, int detailX, int detailWidth, int partyY
    ) {
        theme.drawText(graphics, font,
            Component.translatable("screen.cobbleventure_player_menu.tm_workshop.party"),
            detailX, partyY, MenuTheme.TextRole.LABEL);
        var party = CobblemonClient.INSTANCE.getStorage().getParty();
        for (int slot = 0; slot < 6; slot++) {
            var pokemon = party.get(slot);
            if (pokemon == null) continue;
            BagTechnicalMachines.Availability availability = BagTechnicalMachines.availability(
                entry.stack(), pokemon, CobblemonClient.INSTANCE.getBattle() != null
            );
            int column = slot % 2;
            int row = slot / 2;
            int cellWidth = detailWidth / 2;
            String label = pokemon.getDisplayName(false).getString() + " · " + availability.label().getString();
            theme.drawText(graphics, font,
                Component.literal(font.plainSubstrByWidth(label, cellWidth - 4)),
                detailX + column * cellWidth, partyY + 14 + row * 13,
                MenuTheme.TextRole.CAPTION,
                availability == BagTechnicalMachines.Availability.LEARNABLE ? theme.success : theme.mutedTextColor);
        }
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || mouseX < x + 8 || mouseX >= x + listWidth - 8 || mouseY < listY()) return false;
        int row = (int)(mouseY - listY()) / ROW_HEIGHT;
        if (row < 0 || row >= visibleRows()) return false;
        int index = scroll + row;
        if (index >= filtered.size()) return false;
        selectedId = filtered.get(index).id();
        quantity = 1;
        status = Component.empty();
        updateButtons();
        return true;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (mouseX < x || mouseX >= x + listWidth || mouseY < y || mouseY >= y + height) return false;
        scroll = Math.clamp(scroll - (int)Math.signum(scrollY), 0,
            Math.max(0, filtered.size() - visibleRows()));
        return true;
    }

    void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (mouseY < listY() || mouseX < x + 8 || mouseX >= x + listWidth - 8) return;
        int row = (mouseY - listY()) / ROW_HEIGHT;
        int index = scroll + row;
        if (row >= 0 && row < visibleRows() && index < filtered.size()) {
            graphics.renderTooltip(font, filtered.get(index).stack(), mouseX, mouseY);
        }
    }

    private void refresh(boolean resetScroll) {
        String query = search == null ? "" : search.getValue().strip().toLowerCase(Locale.ROOT);
        List<MachineBagNetwork.TmEntry> next = new ArrayList<>();
        for (MachineBagNetwork.TmEntry entry : entries) {
            String searchable = (entry.name().getString() + " " + entry.description().getString()
                + " " + entry.id() + " " + entry.type()).toLowerCase(Locale.ROOT);
            if ((!unlockedOnly || entry.unlocked())
                && (typeFilter.isEmpty() || entry.type().equals(typeFilter))
                && (query.isEmpty() || searchable.contains(query))) next.add(entry);
        }
        next.sort(java.util.Comparator.comparing(entry -> entry.name().getString().toLowerCase(Locale.ROOT)));
        filtered = List.copyOf(next);
        if (selectedId == null || entries.stream().noneMatch(entry -> entry.id().equals(selectedId))) {
            selectedId = filtered.isEmpty() ? null : filtered.get(0).id();
        }
        if (resetScroll) scroll = 0;
        scroll = Math.clamp(scroll, 0, Math.max(0, filtered.size() - visibleRows()));
        updateButtons();
    }

    private void changeQuantity(int delta) {
        quantity = Math.clamp(quantity + delta, 1, 64);
        status = Component.empty();
        updateButtons();
    }

    private void craft() {
        MachineBagNetwork.TmEntry entry = selected();
        if (entry == null || processing || !canCraft(entry)) return;
        processing = true;
        status = Component.translatable("screen.cobbleventure_player_menu.tm_workshop.processing");
        statusColor = theme.accent;
        updateButtons();
        MachineBagNetwork.craft(token, entry.id(), quantity);
    }

    private void cycleType() {
        List<String> types = entries.stream().map(MachineBagNetwork.TmEntry::type).distinct().sorted().toList();
        if (types.isEmpty()) return;
        int current = types.indexOf(typeFilter);
        typeFilter = current < 0 ? types.get(0)
            : current + 1 < types.size() ? types.get(current + 1) : "";
        typeButton.setMessage(typeFilter.isEmpty()
            ? Component.translatable("screen.cobbleventure_player_menu.tm_workshop.type_all")
            : Component.literal(typeFilter));
        refresh(true);
    }

    private void toggleUnlocked() {
        unlockedOnly = !unlockedOnly;
        unlockButton.setMessage(Component.translatable(unlockedOnly
            ? "screen.cobbleventure_player_menu.tm_workshop.show_unlocked"
            : "screen.cobbleventure_player_menu.tm_workshop.show_all"));
        refresh(true);
    }

    private void updateButtons() {
        if (minus == null) return;
        minus.active = quantity > 1;
        plus.active = quantity < 64;
        MachineBagNetwork.TmEntry entry = selected();
        craft.active = !processing && entry != null && canCraft(entry);
    }

    private boolean canCraft(MachineBagNetwork.TmEntry entry) {
        if (!entry.unlocked()) return false;
        for (MachineBagNetwork.CostEntry cost : entry.costs()) {
            if ((long)cost.required() * quantity > cost.owned()) return false;
        }
        return true;
    }

    private MachineBagNetwork.TmEntry selected() {
        if (selectedId == null) return null;
        for (MachineBagNetwork.TmEntry entry : entries) if (entry.id().equals(selectedId)) return entry;
        return null;
    }

    private void drawInput(GuiGraphics graphics, int x, int y, int width, int height) {
        int radius = Math.min(theme.rowRadius, height / 2);
        ThemedOverlayPanel.fillRoundedRect(graphics, x, y, x + width, y + height, radius, theme.border);
        ThemedOverlayPanel.fillRoundedRect(graphics, x + 1, y + 1, x + width - 1, y + height - 1,
            Math.max(0, radius - 1), theme.inputBackground);
    }

    private int listY() { return y + 56; }
    private int visibleRows() { return Math.max(1, (height - 76) / ROW_HEIGHT); }
    private static String number(double value) {
        return value < 0 ? "—" : Integer.toString((int)Math.round(value));
    }
}
