package dev.buizz.cobbleventure.adventure.fossil.client;

import dev.buizz.cobbleventure.adventure.fossil.FossilLaboratoryService;
import dev.buizz.cobbleventure.adventure.fossil.FossilNetwork;
import dev.buizz.cobbleventure.playermenu.client.MenuBackButton;
import dev.buizz.cobbleventure.playermenu.client.MenuTheme;
import dev.buizz.cobbleventure.playermenu.client.ThemedButton;
import dev.buizz.cobbleventure.playermenu.client.ThemedOverlayPanel;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FossilScreen extends Screen {
    private FossilNetwork.View view;
    private MenuTheme theme;
    private int page, selected = -1, x, y, panelWidth, panelHeight, rows;
    private boolean pending;
    public FossilScreen(FossilNetwork.View view) { super(text("title")); this.view = view; }
    private static Component text(String key, Object... args) { return FossilLaboratoryService.text(key, args); }
    public UUID npc() { return view.npc(); }
    public void apply(FossilNetwork.View view) { this.view = view; pending = false; selected = -1; rebuildWidgets(); }
    @Override protected void init() {
        theme = MenuTheme.load(minecraft);
        panelWidth = Math.min(420, width - 16); panelHeight = Math.min(330, height - 16);
        x = (width - panelWidth) / 2; y = (height - panelHeight) / 2;
        rows = Math.max(1, (panelHeight - 150) / 30);
        page = Math.min(page, Math.max(0, (view.entries().size() - 1) / rows));
        for (int row = 0; row < rows; row++) {
            int index = page * rows + row;
            if (index >= view.entries().size()) break;
            var entry = view.entries().get(index);
            Component label = Component.empty();
            for (int i = 0; i < entry.items().size(); i++) {
                if (i > 0) label = label.copy().append(" + ");
                label = label.copy().append(entry.items().get(i).getHoverName());
            }
            var button = addRenderableWidget(new ThemedButton(theme, label,
                x + 36, y + 57 + row * 30, panelWidth - 50, 26, MenuTheme.ButtonVariant.SECONDARY,
                () -> { selected = index; rebuildWidgets(); }, () -> selected == index));
            button.active = view.selectable() && !pending;
        }
        addButton(text("previous"), x + 14, y + panelHeight - 86, 65,
            () -> { page--; rebuildWidgets(); }, page > 0);
        addButton(text("next"), x + 84, y + panelHeight - 86, 65,
            () -> { page++; rebuildWidgets(); }, (page + 1) * rows < view.entries().size());
        addButton(text("refresh"), x + panelWidth - 100, y + panelHeight - 86, 86,
            () -> send(FossilNetwork.Kind.REFRESH, ""), true);
        addButton(text(view.ready() ? "collect" : "restore"), x + panelWidth - 164, y + panelHeight - 37, 150,
            () -> send(view.ready() ? FossilNetwork.Kind.COLLECT : FossilNetwork.Kind.START,
                selected >= 0 ? view.entries().get(selected).id() : ""),
            view.ready() || (view.selectable() && selected >= 0));
        addRenderableWidget(new MenuBackButton(theme, x + 14, y + panelHeight - 37, 84, 24, this::onClose));
    }
    private void addButton(Component label, int bx, int by, int bw, Runnable action, boolean active) {
        var button = addRenderableWidget(new ThemedButton(theme, label, bx, by, bw, 24,
            MenuTheme.ButtonVariant.SECONDARY, action));
        button.active = active && !pending;
    }
    private void send(FossilNetwork.Kind kind, String recipe) {
        pending = true; rebuildWidgets();
        PacketDistributor.sendToServer(new FossilNetwork.Action(view.npc(), kind, recipe));
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float tick) {
        // Blur the world before drawing the panel; Screen.render calls our
        // renderBackground override again while rendering the widgets.
        super.renderBackground(graphics, mouseX, mouseY, tick);
        ThemedOverlayPanel.draw(graphics, theme, x, y, panelWidth, panelHeight);
        theme.drawText(graphics, font, title, x + 14, y + 12, MenuTheme.TextRole.TITLE);
        theme.drawWrappedText(graphics, font, view.status(), x + 14, y + 34, panelWidth - 28,
            MenuTheme.TextRole.CAPTION, theme.secondaryTextColor, 2);
        if (view.entries().isEmpty() && view.selectable())
            theme.drawWrappedText(graphics, font, text("empty"), x + 14, y + 60, panelWidth - 28,
                MenuTheme.TextRole.BODY, theme.textColor, 3);
        super.render(graphics, mouseX, mouseY, tick);
        for (int row = 0; row < rows; row++) {
            int index = page * rows + row;
            if (index >= view.entries().size()) break;
            var item = view.entries().get(index).items().getFirst();
            graphics.renderItem(item, x + 15, y + 62 + row * 30);
            if (mouseX >= x + 14 && mouseX < x + panelWidth - 14
                && mouseY >= y + 57 + row * 30 && mouseY < y + 83 + row * 30)
                graphics.renderTooltip(font, view.entries().get(index).name(), mouseX, mouseY);
        }
        Component detail = !view.feedback().getString().isBlank() ? view.feedback()
            : selected >= 0 ? text("result", view.entries().get(selected).name()) : text("included");
        theme.drawWrappedText(graphics, font, detail, x + 14, y + panelHeight - 60, panelWidth - 28,
            MenuTheme.TextRole.CAPTION, theme.secondaryTextColor, 2);
    }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float tick) {
        // Already rendered before the panel, so never blur the UI itself.
    }
    @Override public boolean isPauseScreen() { return false; }
}
