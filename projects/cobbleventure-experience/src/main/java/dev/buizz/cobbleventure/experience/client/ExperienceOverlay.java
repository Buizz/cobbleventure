package dev.buizz.cobbleventure.experience.client;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.gui.battle.BattleOverlay;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;
import dev.buizz.cobbleventure.experience.CobbleventureExperience;
import dev.buizz.cobbleventure.experience.ExperienceMath;
import dev.buizz.cobbleventure.experience.ExperienceNetwork.ExperiencePayload;
import dev.buizz.cobbleventure.playermenu.client.MenuTheme;
import dev.buizz.cobbleventure.playermenu.client.ThemedOverlayPanel;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Persistent experience strips attached to Cobblemon's actual battle tiles. */
@EventBusSubscriber(modid = CobbleventureExperience.MOD_ID, value = Dist.CLIENT)
public final class ExperienceOverlay {
    private static final long ANIMATION_NANOS = 1_000_000_000L;
    // Experience domain color sampled from Cobblemon's party status bar.
    private static final int XP_FILL = 0xFF33A6D6;
    private static final Map<UUID, Notice> NOTICES = new HashMap<>();
    private static final Map<UUID, Integer> DISPLAY_LEVELS = new HashMap<>();
    private static final Map<UUID, Long> LEVEL_UP_FLASHES = new HashMap<>();
    private static final long FLASH_NANOS = 650_000_000L;
    private static final ResourceLocation FRAME = ResourceLocation.fromNamespaceAndPath(
        "cobblemon", "textures/gui/battle/battle_info_base.png");
    private static final ResourceLocation COMPACT_FRAME = ResourceLocation.fromNamespaceAndPath(
        "cobblemon", "textures/gui/battle/battle_info_base_condensed.png");
    private static MenuTheme theme;

    private ExperienceOverlay() {}
    public static void initialize() {}

    public static void show(ExperiencePayload payload) {
        long now = System.nanoTime();
        NOTICES.entrySet().removeIf(entry -> now - entry.getValue().startedAt() >= ANIMATION_NANOS);
        Notice previous = NOTICES.get(payload.pokemonId());
        int from = previous == null ? payload.previousExperience() : previous.experienceAt(now);
        NOTICES.put(payload.pokemonId(), new Notice(from, payload.currentExperience(), now));
        DISPLAY_LEVELS.putIfAbsent(payload.pokemonId(), payload.previousLevel());
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        NOTICES.clear();
        DISPLAY_LEVELS.clear();
        LEVEL_UP_FLASHES.clear();
        theme = null;
    }

    public static void renderTile(
        GuiGraphics graphics, ActiveClientBattlePokemon active,
        float tileX, float tileY, boolean compact, float opacity
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui
            || !active.getActor().getUuid().equals(minecraft.player.getUUID())
            || active.getBattlePokemon() == null) return;
        UUID uuid = active.getBattlePokemon().getUuid();
        for (var pokemon : CobblemonClient.INSTANCE.getStorage().getParty()) {
            if (pokemon == null || !pokemon.getUuid().equals(uuid)) continue;
            if (theme == null) theme = MenuTheme.load(minecraft);
            Notice notice = NOTICES.get(uuid);
            long now = System.nanoTime();
            int experience = notice == null ? pokemon.getExperience() : notice.experienceAt(now);
            if (notice != null && now - notice.startedAt() >= ANIMATION_NANOS) NOTICES.remove(uuid);
            var group = pokemon.getExperienceGroup();
            int level = group.getLevel(experience);
            Integer previousLevel = DISPLAY_LEVELS.put(uuid, level);
            if (previousLevel != null && level > previousLevel) LEVEL_UP_FLASHES.put(uuid, now);
            int start = group.getExperience(level);
            int next = level >= Cobblemon.INSTANCE.getConfig().getMaxPokemonLevel()
                ? start : group.getExperience(level + 1);
            float progress = ExperienceMath.progress(experience, start, next);

            // Follow the real tile position, including slide animations and multi-battle layout.
            int portrait = compact ? BattleOverlay.COMPACT_PORTRAIT_DIAMETER : BattleOverlay.PORTRAIT_DIAMETER;
            int portraitOffset = compact ? BattleOverlay.COMPACT_PORTRAIT_OFFSET_X : BattleOverlay.PORTRAIT_OFFSET_X;
            int tileWidth = compact ? BattleOverlay.COMPACT_TILE_WIDTH : BattleOverlay.TILE_WIDTH;
            int tileHeight = compact ? BattleOverlay.COMPACT_TILE_HEIGHT : BattleOverlay.TILE_HEIGHT;
            // Reserve the lower-left strip for up to two type icons.
            int inset = portrait + portraitOffset + 24;
            int x = Math.round(tileX) + inset;
            int height = 2;
            int y = Math.round(tileY) + tileHeight - height - 2;
            int width = tileWidth - inset - 7;
            graphics.fill(x, y, x + width, y + height,
                ThemedOverlayPanel.withOpacity(theme.scrim, opacity));
            int filled = Math.round(width * progress);
            graphics.fill(x, y, x + filled, y + height,
                ThemedOverlayPanel.withOpacity(XP_FILL, opacity));
            return;
        }
    }

    /** Called around the original tile draw so portrait, labels, HP and XP expand together. */
    public static float beginTile(
        GuiGraphics graphics, ActiveClientBattlePokemon active, float x, float y, boolean compact
    ) {
        float pulse = 0;
        var minecraft = Minecraft.getInstance();
        if (minecraft.player != null && active.getBattlePokemon() != null
            && active.getActor().getUuid().equals(minecraft.player.getUUID())) {
            UUID uuid = active.getBattlePokemon().getUuid();
            Long started = LEVEL_UP_FLASHES.get(uuid);
            if (started != null) {
                double elapsed = (double)(System.nanoTime() - started) / FLASH_NANOS;
                if (elapsed >= 1) LEVEL_UP_FLASHES.remove(uuid);
                else pulse = (float)Math.sin(Math.PI * Math.max(0, elapsed));
            }
        }
        graphics.pose().pushPose();
        float centerX = x + (compact ? BattleOverlay.COMPACT_TILE_WIDTH : BattleOverlay.TILE_WIDTH) / 2.0F;
        float centerY = y + (compact ? BattleOverlay.COMPACT_TILE_HEIGHT : BattleOverlay.TILE_HEIGHT) / 2.0F;
        float scale = 1.0F + 0.08F * pulse;
        graphics.pose().translate(centerX, centerY, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.pose().translate(-centerX, -centerY, 0);
        return pulse;
    }

    public static void endTile(
        GuiGraphics graphics, float x, float y, boolean compact, float opacity, float pulse
    ) {
        try {
            if (pulse <= 0) return;
            int width = compact ? BattleOverlay.COMPACT_TILE_WIDTH : BattleOverlay.TILE_WIDTH;
            int height = compact ? BattleOverlay.COMPACT_TILE_HEIGHT : BattleOverlay.TILE_HEIGHT;
            int textureHeight = compact ? BattleOverlay.COMPACT_TILE_TEXTURE_HEIGHT : height;
            // Additive white light through the native frame alpha preserves its pixel silhouette.
            graphics.flush();
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            RenderSystem.setShaderColor(1, 1, 1, pulse * opacity * 0.85F);
            graphics.blit(compact ? COMPACT_FRAME : FRAME, Math.round(x), Math.round(y),
                0, 0, width, height, width, textureHeight);
            graphics.flush();
        } finally {
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            graphics.pose().popPose();
        }
    }

    private record Notice(int from, int to, long startedAt) {
        int experienceAt(long now) {
            double fraction = Math.clamp((double)(now - startedAt) / ANIMATION_NANOS, 0.0D, 1.0D);
            double eased = 1.0D - (1.0D - fraction) * (1.0D - fraction);
            return (int)Math.round(from + (to - from) * eased);
        }
    }
}
