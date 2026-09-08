package dev.buizz.cobbleventure.experience.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.buizz.cobbleventure.experience.CobbleventureExperience;
import dev.buizz.cobbleventure.experience.ExperienceMath;
import dev.buizz.cobbleventure.experience.ExperienceNetwork.ExperiencePayload;
import dev.buizz.cobbleventure.playermenu.client.MenuTheme;
import dev.buizz.cobbleventure.playermenu.client.ThemedOverlayPanel;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Resolution-relative, multi-battle-safe experience presentation. */
@EventBusSubscriber(modid = CobbleventureExperience.MOD_ID, value = Dist.CLIENT)
public final class ExperienceOverlay {
    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(
        CobbleventureExperience.MOD_ID, "experience"
    );
    private static final int MAX_QUEUE = 12;
    private static final double DURATION_TICKS = 58.0D;
    private static final double ANIMATION_TICKS = 20.0D;
    private static final Deque<ExperiencePayload> QUEUE = new ArrayDeque<>();
    private static Notice notice;

    private ExperienceOverlay() {}

    public static void initialize() {
        // Forces safe client-only class loading from the mod entry point.
    }

    public static void show(ExperiencePayload payload) {
        if (notice == null) {
            notice = new Notice(payload, MenuTheme.load(Minecraft.getInstance()), System.nanoTime());
        } else {
            if (QUEUE.size() >= MAX_QUEUE) QUEUE.removeFirst();
            QUEUE.addLast(payload);
        }
    }

    @SubscribeEvent
    public static void registerLayer(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER, ExperienceOverlay::renderHud);
    }

    @SubscribeEvent
    public static void renderScreen(ScreenEvent.Render.Post event) {
        render(event.getGuiGraphics());
    }

    private static void renderHud(GuiGraphics graphics, DeltaTracker ignored) {
        if (Minecraft.getInstance().screen == null) render(graphics);
    }

    private static void render(GuiGraphics graphics) {
        Notice current = notice;
        if (current == null) return;
        double elapsed = (System.nanoTime() - current.startedAt()) / 50_000_000.0D;
        if (elapsed >= DURATION_TICKS) {
            ExperiencePayload next = QUEUE.pollFirst();
            notice = next == null ? null
                : new Notice(next, MenuTheme.load(Minecraft.getInstance()), System.nanoTime());
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        MenuTheme theme = current.theme();
        ExperiencePayload payload = current.payload();
        int width = Math.min(250, Math.max(190, graphics.guiWidth() - 24));
        int height = 43;
        int x = (graphics.guiWidth() - width) / 2;
        int y = 12;
        float opacity = (float)Math.min(1.0D, Math.min(elapsed / 4.0D, (DURATION_TICKS - elapsed) / 8.0D));

        float from = payload.previousLevel() == payload.currentLevel()
            ? ExperienceMath.progress(
                payload.previousExperience(), payload.levelStart(), payload.nextLevel()
            ) : 0.0F;
        float target = ExperienceMath.progress(
            payload.currentExperience(), payload.levelStart(), payload.nextLevel()
        );
        float animation = (float)Math.clamp(elapsed / ANIMATION_TICKS, 0.0D, 1.0D);
        float eased = 1.0F - (1.0F - animation) * (1.0F - animation);
        float progress = from + (target - from) * eased;

        RenderSystem.enableBlend();
        ThemedOverlayPanel.draw(graphics, theme, x, y, width, height, opacity, theme.accent);
        int text = ThemedOverlayPanel.withOpacity(theme.textColor, opacity);
        int secondary = ThemedOverlayPanel.withOpacity(theme.secondaryTextColor, opacity);
        theme.drawText(
            graphics, minecraft.font, payload.pokemonName(), x + 10, y + 8,
            MenuTheme.TextRole.BODY, text
        );
        Component level = Component.translatable(
            "hud.cobbleventure_experience.level", payload.currentLevel()
        );
        int levelWidth = theme.textWidth(minecraft.font, level, MenuTheme.TextRole.CAPTION);
        theme.drawText(
            graphics, minecraft.font, level, x + width - levelWidth - 10, y + 9,
            MenuTheme.TextRole.CAPTION, secondary
        );

        int barX = x + 10;
        int barY = y + 26;
        int barWidth = width - 20;
        int barHeight = 7;
        ThemedOverlayPanel.fillRoundedRect(
            graphics, barX, barY, barX + barWidth, barY + barHeight, 3,
            ThemedOverlayPanel.withOpacity(theme.disabledBackground, opacity)
        );
        int filled = Math.round(barWidth * progress);
        if (filled > 0) {
            ThemedOverlayPanel.fillRoundedRect(
                graphics, barX, barY, barX + filled, barY + barHeight, 3,
                ThemedOverlayPanel.withOpacity(theme.accent, opacity)
            );
        }
        Component gain = Component.translatable(
            "hud.cobbleventure_experience.gained", payload.gained()
        );
        int gainWidth = theme.textWidth(minecraft.font, gain, MenuTheme.TextRole.CAPTION);
        theme.drawText(
            graphics, minecraft.font, gain, x + width - gainWidth - 10, y + 35,
            MenuTheme.TextRole.CAPTION,
            ThemedOverlayPanel.withOpacity(theme.success, opacity)
        );
        RenderSystem.disableBlend();
    }

    private record Notice(ExperiencePayload payload, MenuTheme theme, long startedAt) {}
}
