package dev.buizz.cobbleventure.playermenu.client;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.gui.summary.widgets.ModelWidget;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Shared party picker. Callers provide eligibility and actions; the server must revalidate mutations. */
public class PokemonSelectScreen extends Screen {
    private static final int HP_GREEN = 0xFF64D66D;
    private static final int HP_YELLOW = 0xFFE6C84F;
    private static final int HP_RED = 0xFFE86666;
    private static final int EXP_BLUE = 0xFF59BCE8;

    public record Entry(int slot, Pokemon pokemon) {}
    public record Eligibility(boolean selectable, Component label, Component hint) {
        public static Eligibility available() {
            return new Eligibility(true, Component.empty(), Component.empty());
        }
    }

    private final Screen parent;
    private final ItemStack stack;
    private final Component hint;
    private final java.util.function.Supplier<List<Entry>> entries;
    private final java.util.function.Function<Pokemon, Eligibility> eligibility;
    private final java.util.function.BiConsumer<Pokemon, Integer> selection;
    private List<String> partyIds = List.of();
    private final MenuTheme theme;
    private final List<ModelWidget> models = new ArrayList<>();
    private final List<PokemonButton> pokemonButtons = new ArrayList<>();
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int cardWidth;
    private int cardHeight;

    public PokemonSelectScreen(Screen parent, Component title, Component hint, ItemStack icon,
                               java.util.function.Supplier<List<Entry>> entries,
                               java.util.function.Function<Pokemon, Eligibility> eligibility,
                               java.util.function.BiConsumer<Pokemon, Integer> selection) {
        super(title);
        this.parent = parent;
        this.hint = hint;
        this.stack = icon.copy();
        this.entries = entries;
        this.eligibility = eligibility;
        this.selection = selection;
        this.theme = MenuTheme.load(Minecraft.getInstance());
    }

    public static List<Entry> currentParty() {
        List<Entry> result = new ArrayList<>();
        for (int slot = 0; slot < 6; slot++) {
            Pokemon pokemon = CobblemonClient.INSTANCE.getStorage().getParty().get(slot);
            if (pokemon != null) result.add(new Entry(slot, pokemon));
        }
        return result;
    }

    @Override
    public void tick() {
        List<String> next = entryIds();
        if (!next.equals(partyIds)) rebuildWidgets();
    }

    private List<String> entryIds() {
        return entries.get().stream().map(e -> e.slot() + ":" + e.pokemon().getUuid()).toList();
    }

    @Override
    protected void init() {
        partyIds = entryIds();
        models.clear();
        pokemonButtons.clear();
        panelWidth = Math.min(540, Math.max(300, width - 24));
        panelHeight = Math.min(310, Math.max(210, height - 16));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        int gap = 7;
        cardWidth = (panelWidth - 24 - gap) / 2;
        cardHeight = Math.max(48, (panelHeight - 96 - gap * 2) / 3);
        int cardTop = panelY + 53;
        for (Entry entry : entries.get()) {
            int slot = entry.slot();
            Pokemon pokemon = entry.pokemon();
            if (pokemon == null || slot < 0 || slot >= 6) continue;
            int column = slot % 2;
            int row = slot / 2;
            int cardX = panelX + 8 + column * (cardWidth + gap);
            int cardY = cardTop + row * (cardHeight + gap);
            PokemonButton button = addRenderableWidget(new PokemonButton(
                pokemon, slot,
                cardX, cardY, cardWidth, cardHeight
            ));
            pokemonButtons.add(button);
            int modelSize = Math.min(46, cardHeight - 6);
            ModelWidget model = CobblemonModelWidgetCompat.create(
                cardX + 3, cardY + (cardHeight - modelSize) / 2,
                modelSize, modelSize, pokemon.asRenderablePokemon(),
                Math.max(0.85F, modelSize / 42.0F), 25.0F, 0.0D, false, false
            );
            model.active = false;
            models.add(addRenderableWidget(model));
        }
        addRenderableWidget(new MenuBackButton(
            theme,
            panelX + panelWidth - 76, panelY + panelHeight - 27, 66, 19, this::onClose
        ));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ThemedOverlayPanel.draw(graphics, theme, panelX, panelY, panelWidth, panelHeight);
        graphics.fill(panelX + 12, panelY + 1, panelX + 54, panelY + 3, theme.accent);
        theme.drawText(graphics, font, title, panelX + 12, panelY + 10, MenuTheme.TextRole.HEADING);
        theme.drawText(graphics, font, hint,
            panelX + 12, panelY + 26, MenuTheme.TextRole.CAPTION);
        graphics.renderItem(stack, panelX + panelWidth - 30, panelY + 9);
        graphics.renderItemDecorations(font, stack, panelX + panelWidth - 30, panelY + 9);
        graphics.fill(panelX + 8, panelY + 45, panelX + panelWidth - 8, panelY + 46, theme.innerBorder);
        super.render(graphics, mouseX, mouseY, partialTick);
        for (PokemonButton button : pokemonButtons) button.renderHeldItemTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private void select(Pokemon pokemon, int partySlot) {
        // Do not target a replacement Pokémon if the party changed while the picker was open.
        Entry current = entries.get().stream().filter(e -> e.slot() == partySlot
            && e.pokemon().getUuid().equals(pokemon.getUuid())).findFirst().orElse(null);
        if (current == null || !eligibility.apply(current.pokemon()).selectable()) return;
        selection.accept(current.pokemon(), partySlot);
    }

    private final class PokemonButton extends AbstractButton {
        private final Pokemon pokemon;
        private final int partySlot;

        private PokemonButton(Pokemon pokemon, int partySlot, int x, int y, int width, int height) {
            super(x, y, width, height, pokemon.getDisplayName(false));
            this.pokemon = pokemon;
            this.partySlot = partySlot;
            active = eligibility.apply(pokemon).selectable();
        }

        @Override
        public void onPress() { if (active) select(pokemon, partySlot); }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            active = eligibility.apply(pokemon).selectable();
            MenuTheme.ButtonStyle style = theme.button(
                MenuTheme.ButtonVariant.SECONDARY, active, isHoveredOrFocused(), false
            );
            int border = style.border();
            int fill = style.background();
            ThemedOverlayPanel.fillRoundedRect(graphics, getX(), getY(), getX() + getWidth(), getY() + getHeight(), theme.rowRadius, border);
            ThemedOverlayPanel.fillRoundedRect(graphics, getX() + 1, getY() + 1,
                getX() + getWidth() - 1, getY() + getHeight() - 1, Math.max(0, theme.rowRadius - 1), fill);
            int detailsX = getX() + Math.min(50, getHeight());
            int detailsWidth = Math.max(38, getWidth() - (detailsX - getX()) - 7);
            theme.drawText(graphics, font,
                Component.literal(font.plainSubstrByWidth(pokemon.getDisplayName(false).getString(), detailsWidth - 42)),
                detailsX, getY() + 6, MenuTheme.TextRole.LABEL, style.text());
            String level = Component.translatable(
                "screen.cobbleventure_player_menu.bag.pokemon_select.level", pokemon.getLevel()
            ).getString();
            theme.drawText(graphics, font, Component.literal(level), getX() + getWidth() - 7
                - theme.textWidth(font, Component.literal(level), MenuTheme.TextRole.CAPTION),
                getY() + 6, MenuTheme.TextRole.CAPTION, theme.mutedTextColor);

            Eligibility status = eligibility.apply(pokemon);
            if (!status.label().getString().isEmpty()) {
                theme.drawText(graphics, font, status.label(), detailsX, getY() + 22,
                    MenuTheme.TextRole.LABEL, active ? theme.success : theme.disabledText);
                if (!status.hint().getString().isEmpty() && getHeight() >= 62) {
                    theme.drawText(graphics, font, status.hint(), detailsX, getY() + 37,
                        MenuTheme.TextRole.CAPTION);
                }
                return;
            }

            int maximumHealth = Math.max(1, pokemon.getMaxHealth());
            int currentHealth = Math.max(0, pokemon.getCurrentHealth());
            float healthRatio = Math.min(1.0F, currentHealth / (float) maximumHealth);
            boolean compact = getHeight() < 62;
            int healthBarY = getY() + (compact ? 18 : 27);
            graphics.fill(detailsX, healthBarY, detailsX + detailsWidth, healthBarY + 4, theme.inputBackground);
            int healthWidth = Math.round((detailsWidth - 2) * healthRatio);
            int healthColor = healthRatio > 0.5F ? HP_GREEN : healthRatio > 0.2F ? HP_YELLOW : HP_RED;
            if (healthWidth > 0) {
                graphics.fill(detailsX + 1, healthBarY + 1,
                    detailsX + 1 + healthWidth, healthBarY + 3, healthColor);
            }

            int levelStartExperience = pokemon.getExperienceGroup().getExperience(pokemon.getLevel());
            int currentLevelExperience = Math.max(0, pokemon.getExperience() - levelStartExperience);
            boolean maximumLevel = !pokemon.canLevelUpFurther();
            int requiredExperience = maximumLevel ? 1 : Math.max(
                1, pokemon.getExperienceGroup().getExperience(pokemon.getLevel() + 1) - levelStartExperience
            );
            float experienceRatio = maximumLevel
                ? 1.0F
                : Math.min(1.0F, currentLevelExperience / (float) requiredExperience);
            int experienceBarY = getY() + (compact ? 25 : 42);
            graphics.fill(detailsX, experienceBarY,
                detailsX + detailsWidth, experienceBarY + 4, theme.inputBackground);
            int experienceWidth = Math.round((detailsWidth - 2) * experienceRatio);
            if (experienceWidth > 0) {
                graphics.fill(detailsX + 1, experienceBarY + 1,
                    detailsX + 1 + experienceWidth, experienceBarY + 3, EXP_BLUE);
            }
            if (!compact) {
                String health = Component.translatable(
                    "screen.cobbleventure_player_menu.bag.pokemon_select.hp", currentHealth, maximumHealth
                ).getString();
                theme.drawText(graphics, font, Component.literal(health), detailsX, getY() + 17,
                    MenuTheme.TextRole.CAPTION, theme.mutedTextColor);
                String experience = maximumLevel
                    ? Component.translatable("screen.cobbleventure_player_menu.bag.pokemon_select.exp_max").getString()
                    : Component.translatable(
                        "screen.cobbleventure_player_menu.bag.pokemon_select.exp",
                        currentLevelExperience, requiredExperience
                    ).getString();
                theme.drawText(graphics, font,
                    Component.literal(font.plainSubstrByWidth(experience, detailsWidth)),
                    detailsX, getY() + 32, MenuTheme.TextRole.CAPTION, theme.mutedTextColor);
            }

            ItemStack held = pokemon.heldItem();
            int heldY = getY() + getHeight() - 18;
            if (held.isEmpty()) {
                theme.drawText(graphics, font,
                    Component.translatable("screen.cobbleventure_player_menu.bag.no_held_item"),
                    detailsX, heldY + 5, MenuTheme.TextRole.CAPTION, theme.mutedTextColor);
            } else {
                graphics.renderItem(held, detailsX, heldY);
                theme.drawText(graphics, font,
                    Component.literal(font.plainSubstrByWidth(held.getHoverName().getString(), detailsWidth - 19)),
                    detailsX + 18, heldY + 5, MenuTheme.TextRole.CAPTION, theme.mutedTextColor);
            }
        }

        private void renderHeldItemTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
            if (!eligibility.apply(pokemon).label().getString().isEmpty()) return;
            ItemStack held = pokemon.heldItem();
            int detailsX = getX() + Math.min(50, getHeight());
            int heldY = getY() + getHeight() - 18;
            if (!held.isEmpty() && mouseX >= detailsX && mouseX < detailsX + 16
                && mouseY >= heldY && mouseY < heldY + 16) {
                graphics.renderTooltip(font, held, mouseX, mouseY);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,
                getMessage().copy().append(" · ").append(eligibility.apply(pokemon).label()));
        }
    }

}
