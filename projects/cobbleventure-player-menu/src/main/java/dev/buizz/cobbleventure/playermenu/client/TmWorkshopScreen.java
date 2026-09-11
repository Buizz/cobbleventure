package dev.buizz.cobbleventure.playermenu.client;

import dev.buizz.cobbleventure.playermenu.MachineBagNetwork;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Theme-backed shell for the bag-integrated TM workshop. */
public final class TmWorkshopScreen extends Screen {
    private final UUID token;
    private final ResourceLocation machineId;
    private final BlockPos machinePos;
    private final MenuTheme theme;
    private final List<MachineBagNetwork.TmEntry> initialEntries;
    private MachineBagPanel bagPanel;
    private TmWorkshopPanel workshopPanel;
    private boolean closeSent;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int contentY;
    private int leftWidth;
    private int contentHeight;

    public TmWorkshopScreen(
        UUID token, ResourceLocation machineId, BlockPos machinePos,
        List<MachineBagNetwork.TmEntry> entries
    ) {
        super(Component.translatable("screen.cobbleventure_player_menu.tm_workshop.title"));
        this.token = token;
        this.machineId = machineId;
        this.machinePos = machinePos;
        this.initialEntries = List.copyOf(entries);
        this.theme = MenuTheme.load(Minecraft.getInstance());
    }

    @Override
    protected void init() {
        panelWidth = Math.min(780, width - 24);
        panelHeight = Math.min(400, height - 24);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        contentY = panelY + 42;
        int gap = 8;
        leftWidth = Math.max(160, (panelWidth - 40) * 2 / 3);
        int rightWidth = panelWidth - 32 - gap - leftWidth;
        contentHeight = panelHeight - 86;
        bagPanel = new MachineBagPanel(minecraft, font, theme);
        addRenderableWidget(bagPanel.initialize(
            panelX + 12 + leftWidth + gap, contentY, rightWidth, contentHeight
        ));
        workshopPanel = new TmWorkshopPanel(minecraft, font, theme, token, initialEntries);
        for (var widget : workshopPanel.initialize(
            panelX + 12, contentY, leftWidth, contentHeight
        )) addRenderableWidget(widget);
        addRenderableWidget(new MenuBackButton(
            theme, panelX + 12, panelY + panelHeight - 32, 92, 20, this::onClose
        ));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, theme.scrim);
        ThemedOverlayPanel.draw(graphics, theme, panelX, panelY, panelWidth, panelHeight);
        theme.drawText(
            graphics, font, title, panelX + 16, panelY + 14, MenuTheme.TextRole.TITLE
        );

        if (workshopPanel != null) workshopPanel.render(graphics, mouseX, mouseY);
        if (bagPanel != null) bagPanel.render(graphics, mouseX, mouseY);
        theme.drawText(
            graphics, font,
            Component.literal(machineId + " · " + machinePos.toShortString()),
            panelX + 116, panelY + panelHeight - 26, MenuTheme.TextRole.CAPTION, theme.mutedTextColor
        );
        super.render(graphics, mouseX, mouseY, partialTick);
        if (workshopPanel != null) workshopPanel.renderTooltip(graphics, mouseX, mouseY);
        if (bagPanel != null) bagPanel.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public void tick() {
        super.tick();
        if (bagPanel != null) bagPanel.tick();
        if (workshopPanel != null) workshopPanel.tick();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (bagPanel != null && bagPanel.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        if (workshopPanel != null && workshopPanel.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (workshopPanel != null && workshopPanel.mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    void update(MachineBagNetwork.UpdatePayload payload) {
        if (workshopPanel != null) workshopPanel.update(payload.entries(), payload.success(), payload.resultKey());
    }

    boolean matches(UUID candidate) {
        return token.equals(candidate);
    }

    void expire() {
        closeSent = true;
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void onClose() {
        closeSession();
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void removed() {
        closeSession();
    }

    private void closeSession() {
        if (closeSent) return;
        closeSent = true;
        MachineBagNetwork.close(token);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
