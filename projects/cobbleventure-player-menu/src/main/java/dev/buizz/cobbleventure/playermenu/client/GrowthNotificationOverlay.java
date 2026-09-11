package dev.buizz.cobbleventure.playermenu.client;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.buizz.cobbleventure.playermenu.CobbleventurePlayerMenu;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** Persistent, compact growth reminder plus the pending actions exposed by the E menu. */
@EventBusSubscriber(modid = CobbleventurePlayerMenu.MOD_ID, value = Dist.CLIENT)
public final class GrowthNotificationOverlay {
    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(
        CobbleventurePlayerMenu.MOD_ID, "growth_notification"
    );
    private static final long FADE_IN_NANOS = 250_000_000L;
    private static final int MAX_VISIBLE_POKEMON = 2;
    private static final GrowthNotificationState STATE = new GrowthNotificationState();
    private static final Set<UUID> HUD_NOTICES = new LinkedHashSet<>();
    private static long noticeStartedAt;

    private GrowthNotificationOverlay() {}

    public static void experienceGained(
        UUID pokemonId, int movesLearned, int evolutionsUnlocked
    ) {
        if (movesLearned > 0) STATE.addMoves(pokemonId, movesLearned);
        if (evolutionsUnlocked > 0) STATE.addEvolutions(pokemonId, evolutionsUnlocked);
        if (movesLearned > 0 || evolutionsUnlocked > 0) showHud(pokemonId);
    }

    public static void movesGained(UUID pokemonId, int count) {
        if (count <= 0) return;
        STATE.addMoves(pokemonId, count);
        showHud(pokemonId);
    }

    public static void evolutionsGained(UUID pokemonId, int count) {
        if (count <= 0) return;
        STATE.addEvolutions(pokemonId, count);
        showHud(pokemonId);
    }

    static GrowthNotificationState.PendingGrowth pending(UUID pokemonId) {
        return STATE.get(pokemonId);
    }

    static void reconcileParty(List<Pokemon> party) {
        Set<UUID> ids = new HashSet<>();
        for (Pokemon pokemon : party) {
            ids.add(pokemon.getUuid());
            if (STATE.get(pokemon.getUuid()).evolutions() > 0
                && pokemon.getEvolutionProxy().client().isEmpty()) {
                STATE.resolveEvolutions(pokemon.getUuid());
            }
        }
        STATE.retainPokemon(ids);
    }

    static void acknowledgeMoves(UUID pokemonId) {
        STATE.acknowledgeMoves(pokemonId);
    }

    static void dismissHud() {
        HUD_NOTICES.clear();
        noticeStartedAt = 0L;
    }

    @SubscribeEvent
    public static void registerLayer(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER, GrowthNotificationOverlay::renderHud);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        STATE.clear();
        dismissHud();
    }

    private static void renderHud(GuiGraphics graphics, DeltaTracker ignored) {
        if (Minecraft.getInstance().screen == null) render(graphics);
    }

    private static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        List<PokemonNotice> notices = visibleNotices();
        if (notices.isEmpty()) return;

        int visibleCount = Math.min(MAX_VISIBLE_POKEMON, notices.size());
        List<Component> rows = new ArrayList<>(visibleCount);
        for (int index = 0; index < visibleCount; index++) {
            rows.add(noticeLine(notices.get(index)));
        }
        int hiddenCount = notices.size() - visibleCount;
        Component title = Component.translatable("toast.cobbleventure_player_menu.growth.title");
        Component footer = hiddenCount > 0
            ? Component.translatable(
                "toast.cobbleventure_player_menu.growth.more", hiddenCount,
                minecraft.options.keyInventory.getTranslatedKeyMessage()
            )
            : Component.translatable(
                "toast.cobbleventure_player_menu.growth.hint",
                minecraft.options.keyInventory.getTranslatedKeyMessage()
            );
        MenuTheme theme = MenuTheme.load(minecraft);
        int contentWidth = Math.max(
            theme.textWidth(minecraft.font, title, MenuTheme.TextRole.LABEL),
            theme.textWidth(minecraft.font, footer, MenuTheme.TextRole.CAPTION)
        );
        for (Component row : rows) {
            contentWidth = Math.max(
                contentWidth, theme.textWidth(minecraft.font, row, MenuTheme.TextRole.LABEL)
            );
        }
        int width = Math.clamp(
            contentWidth + 24, 190, Math.max(190, Math.min(264, graphics.guiWidth() - 24))
        );
        int height = 38 + visibleCount * 13;
        long elapsed = Math.max(0L, System.nanoTime() - noticeStartedAt);
        float progress = Math.min(1.0F, elapsed / (float)FADE_IN_NANOS);
        float alpha = progress;
        float slide = 1.0F - easeOutCubic(progress);
        int x = graphics.guiWidth() - width - 12 + Math.round(slide * 18);
        int y = 12;
        boolean hasMoves = notices.stream().anyMatch(notice -> notice.pending().moves() > 0);
        int accent = hasMoves ? theme.warning : theme.success;

        RenderSystem.enableBlend();
        ThemedOverlayPanel.draw(graphics, theme, x, y, width, height, alpha, accent);
        graphics.enableScissor(x + 8, y + 4, x + width - 8, y + height - 4);
        try {
            theme.drawText(
                graphics, minecraft.font, title, x + 12, y + 8,
                MenuTheme.TextRole.LABEL, ThemedOverlayPanel.withOpacity(accent, alpha)
            );
            int rowY = y + 22;
            for (Component row : rows) {
                theme.drawText(
                    graphics, minecraft.font, row, x + 12, rowY,
                    MenuTheme.TextRole.LABEL,
                    ThemedOverlayPanel.withOpacity(theme.textColor, alpha)
                );
                rowY += 13;
            }
            theme.drawText(
                graphics, minecraft.font, footer, x + 12, rowY + 1,
                MenuTheme.TextRole.CAPTION,
                ThemedOverlayPanel.withOpacity(theme.secondaryTextColor, alpha)
            );
        } finally {
            graphics.disableScissor();
        }
        RenderSystem.disableBlend();
    }

    private static void showHud(UUID pokemonId) {
        if (HUD_NOTICES.isEmpty()) noticeStartedAt = System.nanoTime();
        HUD_NOTICES.add(pokemonId);
    }

    private static float easeOutCubic(float value) {
        float inverse = 1.0F - value;
        return 1.0F - inverse * inverse * inverse;
    }

    private static List<PokemonNotice> visibleNotices() {
        List<PokemonNotice> result = new ArrayList<>();
        Set<UUID> partyIds = new HashSet<>();
        for (Pokemon pokemon : CobblemonClient.INSTANCE.getStorage().getParty()) {
            if (pokemon == null) continue;
            UUID pokemonId = pokemon.getUuid();
            partyIds.add(pokemonId);
            GrowthNotificationState.PendingGrowth pending = STATE.get(pokemonId);
            if (HUD_NOTICES.contains(pokemonId) && pending.total() > 0) {
                result.add(new PokemonNotice(pokemon, pending));
            }
        }
        HUD_NOTICES.retainAll(partyIds);
        return result;
    }

    private static Component noticeLine(PokemonNotice notice) {
        Component name = notice.pokemon().getDisplayName(false);
        GrowthNotificationState.PendingGrowth pending = notice.pending();
        if (pending.moves() > 0 && pending.evolutions() > 0) {
            return Component.translatable(
                "toast.cobbleventure_player_menu.growth.combined", name, pending.moves()
            );
        }
        if (pending.moves() > 0) {
            return Component.translatable(
                "toast.cobbleventure_player_menu.growth.moves", name, pending.moves()
            );
        }
        return Component.translatable(
            "toast.cobbleventure_player_menu.growth.evolution", name
        );
    }

    enum Kind { MOVES, EVOLUTION }
    private record PokemonNotice(
        Pokemon pokemon, GrowthNotificationState.PendingGrowth pending
    ) {}
}
