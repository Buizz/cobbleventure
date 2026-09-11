package dev.buizz.cobbleventure.adventure.research.client;

import com.google.gson.*;
import dev.buizz.cobbleventure.adventure.research.ResearchNetwork;
import dev.buizz.cobbleventure.adventure.research.ResearchPolicy;
import dev.buizz.cobbleventure.playermenu.BagNetwork;
import dev.buizz.cobbleventure.playermenu.client.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Shared picker followed by research choices, a quote, and explicit confirmation. */
public final class ResearchScreen extends Screen {
    private final ResearchNetwork.View view;
    private final JsonObject data;
    private final MenuTheme theme;
    private UUID selected;
    private int page, x, y, w, h;
    private boolean confirming, pending;
    private JsonObject target;
    private String feedback = "";
    private Component quote = Component.empty();
    private final List<EditBox> ivs = new ArrayList<>(), evs = new ArrayList<>();
    private EditBox level;
    private boolean factor;

    private ResearchScreen(ResearchNetwork.View view, UUID selected) {
        super(ResearchNetwork.text("title"));
        this.view = view; this.selected = selected;
        data = JsonParser.parseString(view.data()).getAsJsonObject();
        theme = MenuTheme.load(Minecraft.getInstance());
        feedback = data.get("feedback").getAsString();
        if (!data.get("crafted_item").getAsString().isEmpty()) this.selected = UUID.fromString(data.get("crafted_pokemon").getAsString());
    }
    public static void open(ResearchNetwork.View view) {
        var mc = Minecraft.getInstance();
        UUID selected = mc.screen instanceof ResearchScreen old ? old.selected : null;
        var next = new ResearchScreen(view, selected);
        BagNetwork.requestSnapshot();
        mc.setScreen(next);
        if (next.selected == null) next.pick();
    }
    private String kind() { return data.get("kind").getAsString(); }
    private JsonObject row() { return selected == null ? null : data.getAsJsonObject("party").getAsJsonObject(selected.toString()); }
    private int cost(String key) { return data.getAsJsonObject("costs").get(key).getAsInt(); }
    private boolean equipPrompt() { return !data.get("crafted_item").getAsString().isEmpty(); }
    private void pick() {
        minecraft.setScreen(new PokemonSelectScreen(this, ResearchNetwork.text(kind()), ResearchNetwork.text("choose"), ItemStack.EMPTY,
            PokemonSelectScreen::currentParty, pokemon -> {
                var p = data.getAsJsonObject("party").getAsJsonObject(pokemon.getUuid().toString());
                boolean allowed = p != null && p.get("allowed").getAsBoolean();
                return new PokemonSelectScreen.Eligibility(allowed,
                    ResearchNetwork.text(allowed ? "possible" : p == null ? "impossible" : p.get("reason").getAsString()), Component.empty());
            }, (pokemon, slot) -> { selected = pokemon.getUuid(); page = 0; minecraft.setScreen(this); }));
    }
    @Override protected void init() {
        w = Math.min(570, width - 16); h = Math.min(330, height - 16); x = (width - w) / 2; y = (height - h) / 2;
        ivs.clear(); evs.clear(); level = null;
        int footerWidth = (w - 40) / 3;
        addRenderableWidget(new MenuBackButton(theme, x + w - 10 - footerWidth, y + h - 27, footerWidth, 20, this::back));
        if (w < 304 || h < 224) { feedback = "small_screen"; return; }
        if (equipPrompt()) {
            button(x + 14, y + 88, w - 28, 24, ResearchNetwork.text("equip_yes"), () -> send("equip", new JsonObject()));
            button(x + 14, y + 120, w - 28, 24, ResearchNetwork.text("equip_no"), this::onClose);
            return;
        }
        if (confirming) {
            button(x + 10, y + h - 27, footerWidth * 2 + 10, 20, ResearchNetwork.text("confirm"), () -> send("apply", target));
            return;
        }
        button(x + 10, y + h - 27, footerWidth, 20, ResearchNetwork.text("choose"), this::pick);
        if (row() == null) return;
        if (kind().equals("stats")) {
            int gap = Math.max(20, Math.min(29, (h - 136) / 6));
            for (int i = 0; i < 6; i++) {
                ivs.add(field(x + w / 2 - 35, y + 75 + i * gap, 60, row().getAsJsonArray("ivs").get(i).getAsInt()));
                evs.add(field(x + w - 95, y + 75 + i * gap, 60, row().getAsJsonArray("evs").get(i).getAsInt()));
                ivs.get(i).setMessage(ResearchNetwork.text("stat_" + i).copy().append(" ").append(ResearchNetwork.text("iv")));
                evs.get(i).setMessage(ResearchNetwork.text("stat_" + i).copy().append(" ").append(ResearchNetwork.text("ev")));
            }
            button(x + 20 + footerWidth, y + h - 27, footerWidth, 20, ResearchNetwork.text("review"), this::reviewStats);
        } else if (kind().equals("dynamax")) {
            level = field(x + w - 100, y + 82, 70, row().get("level").getAsInt());
            factor = row().get("factor").getAsBoolean();
            button(x + 14, y + 122, w - 28, 24, ResearchNetwork.text(factor ? "factor_on" : "factor_off"), () -> {
                if (row().get("gmax").getAsBoolean() || factor) {
                    factor = !factor;
                    for (var widget : children()) if (widget instanceof ActionButton b && b.getY() == y + 122)
                        b.setMessage(ResearchNetwork.text(factor ? "factor_on" : "factor_off"));
                }
            });
            button(x + 20 + footerWidth, y + h - 27, footerWidth, 20, ResearchNetwork.text("review"), this::reviewDynamax);
        } else {
            JsonArray options = row().getAsJsonArray("options");
            int perPage = Math.max(1, (h - 116) / 29);
            int start = page * perPage;
            for (int i = start; i < Math.min(options.size(), start + perPage); i++) {
                JsonObject option = options.get(i).getAsJsonObject();
                button(x + 14, y + 72 + (i - start) * 29, w - 28, 24, itemName(option.get("item").getAsString()), () -> {
                    target = new JsonObject(); target.addProperty("item", option.get("item").getAsString());
                    quote = itemName(option.get("ingredient").getAsString()).copy().append(" × " + option.get("count").getAsInt());
                    if (option.get("extra_count").getAsInt() > 0) quote = quote.copy().append(" + ")
                        .append(itemName(option.get("extra").getAsString())).append(" × " + option.get("extra_count").getAsInt());
                    quote = quote.copy().append(" / " + option.get("money").getAsLong()).append(ResearchNetwork.text("money_unit"));
                    confirming = true; rebuildWidgets();
                });
            }
            if (page > 0) button(x + 20 + footerWidth, y + h - 27, footerWidth / 2 - 2, 20, Component.literal("◀"), () -> { page--; rebuildWidgets(); });
            if (start + perPage < options.size()) button(x + 22 + footerWidth + footerWidth / 2, y + h - 27, footerWidth / 2 - 2, 20, Component.literal("▶"), () -> { page++; rebuildWidgets(); });
        }
    }
    private EditBox field(int fx, int fy, int fw, int initial) {
        var box = new EditBox(font, fx, fy, fw, 18, Component.empty());
        box.setMaxLength(3); box.setFilter(s -> s.matches("[0-9]*")); box.setValue(Integer.toString(initial));
        box.setTextColor(theme.textColor); box.setTextColorUneditable(theme.disabledText);
        box.setBordered(false);
        addRenderableWidget(box); return box;
    }
    private static int[] values(List<EditBox> boxes) { return boxes.stream().mapToInt(b -> Integer.parseInt(b.getValue())).toArray(); }
    private static int[] array(JsonArray a) { int[] r = new int[a.size()]; for (int i = 0; i < r.length; i++) r[i] = a.get(i).getAsInt(); return r; }
    private static JsonArray array(int[] values) { JsonArray a = new JsonArray(); for (int value : values) a.add(value); return a; }
    private void reviewStats() {
        try {
            int[] iv = values(ivs), ev = values(evs);
            long price = ResearchPolicy.statCost(array(row().getAsJsonArray("ivs")), iv, 31, 186, cost("iv_point"))
                + ResearchPolicy.statCost(array(row().getAsJsonArray("evs")), ev, 252, 510, cost("ev_point"));
            target = new JsonObject(); target.add("ivs", array(iv)); target.add("evs", array(ev));
            quote = Component.literal(Long.toString(price)).append(ResearchNetwork.text("money_unit"));
            confirming = true; rebuildWidgets();
        } catch (IllegalArgumentException ex) { feedback = "invalid"; }
    }
    private void reviewDynamax() {
        try {
            int next = Integer.parseInt(level.getValue());
            int count = ResearchPolicy.mushrooms(row().get("level").getAsInt(), next, row().get("factor").getAsBoolean(), factor,
                row().get("gmax").getAsBoolean(), cost("dynamax_level_mushrooms"), cost("gmax_mushrooms"));
            target = new JsonObject(); target.addProperty("level", next); target.addProperty("factor", factor);
            quote = itemName(data.getAsJsonObject("materials").get("mushroom").getAsString()).copy().append(" × " + count);
            confirming = true; rebuildWidgets();
        } catch (IllegalArgumentException ex) { feedback = "invalid"; }
    }
    private void send(String action, JsonObject payload) {
        if (pending || selected == null) return;
        pending = true;
        for (var child : children()) if (child instanceof AbstractButton button) button.active = false;
        PacketDistributor.sendToServer(new ResearchNetwork.Action(view.token(), selected, action, payload.toString()));
    }
    private void back() { if (confirming && !pending) { confirming = false; rebuildWidgets(); } else onClose(); }
    @Override public void onClose() { minecraft.setScreen(null); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float tick) {}
    @Override public void render(GuiGraphics g, int mx, int my, float tick) {
        ThemedOverlayPanel.draw(g, theme, x, y, w, h);
        text(g, ResearchNetwork.text(kind()), 14, 12, MenuTheme.TextRole.HEADING);
        PokemonSelectScreen.currentParty().stream().filter(e -> e.pokemon().getUuid().equals(selected)).findFirst()
            .ifPresent(e -> text(g, e.pokemon().getDisplayName(false), w / 2, 12, MenuTheme.TextRole.LABEL));
        text(g, ResearchNetwork.text("balance").copy().append(data.get("balance").getAsString()), 14, 31, MenuTheme.TextRole.CAPTION);
        if (!feedback.isEmpty()) text(g, ResearchNetwork.text(feedback), 14, 49, MenuTheme.TextRole.CAPTION);
        if (w < 304 || h < 224) {
            super.render(g, mx, my, tick); return;
        }
        if (equipPrompt()) {
            text(g, itemName(data.get("crafted_item").getAsString()), 14, 66, MenuTheme.TextRole.BODY);
            text(g, ResearchNetwork.text("replace_hint"), 14, 160, MenuTheme.TextRole.CAPTION);
        } else if (confirming) {
            theme.drawWrappedText(g, font, quote, x + 14, y + 77, w - 28,
                MenuTheme.TextRole.BODY, theme.textColor, target.has("item") ? 3 : 1);
            if (target.has("ivs")) {
                for (int i = 0; i < 6; i++) text(g, ResearchNetwork.text("stat_" + i).copy().append("  ")
                    .append(row().getAsJsonArray("ivs").get(i) + " → " + target.getAsJsonArray("ivs").get(i) + " / "
                        + row().getAsJsonArray("evs").get(i) + " → " + target.getAsJsonArray("evs").get(i)),
                    14, 104 + i * 19, MenuTheme.TextRole.LABEL);
            } else if (target.has("level")) {
                text(g, ResearchNetwork.text("dmax_level").copy().append(" " + row().get("level") + " → " + target.get("level")), 14, 107, MenuTheme.TextRole.BODY);
                text(g, ResearchNetwork.text(target.get("factor").getAsBoolean() ? "factor_on" : "factor_off"), 14, 132, MenuTheme.TextRole.BODY);
            } else text(g, itemName(target.get("item").getAsString()), 14, 130, MenuTheme.TextRole.BODY);
        } else if (row() != null && kind().equals("stats")) {
            int gap = Math.max(20, Math.min(29, (h - 136) / 6));
            text(g, ResearchNetwork.text("iv"), w / 2 - 35, 61, MenuTheme.TextRole.CAPTION);
            text(g, ResearchNetwork.text("ev"), w - 95, 61, MenuTheme.TextRole.CAPTION);
            for (int i = 0; i < 6; i++) text(g, ResearchNetwork.text("stat_" + i), 14, 80 + i * gap, MenuTheme.TextRole.LABEL);
        } else if (row() != null && kind().equals("dynamax")) {
            text(g, ResearchNetwork.text("dmax_level"), 14, 87, MenuTheme.TextRole.LABEL);
            text(g, ResearchNetwork.text(row().get("gmax").getAsBoolean() ? "gmax_supported" : "gmax_unsupported"), 14, 158, MenuTheme.TextRole.BODY);
        }
        for (var child : children()) if (child instanceof EditBox box) {
            g.fill(box.getX() - 2, box.getY() - 2, box.getX() + box.getWidth() + 2, box.getY() + box.getHeight(), theme.innerBorder);
            g.fill(box.getX() - 1, box.getY() - 1, box.getX() + box.getWidth() + 1, box.getY() + box.getHeight() - 1, theme.inputBackground);
        }
        super.render(g, mx, my, tick);
    }
    private void text(GuiGraphics g, Component text, int dx, int dy, MenuTheme.TextRole role) {
        theme.drawWrappedText(g, font, text, x + dx, y + dy, w - dx - 14, role, theme.text(role).color(), 1);
    }
    private static Component itemName(String id) {
        var key = ResourceLocation.tryParse(id);
        return key == null ? Component.literal(id) : BuiltInRegistries.ITEM.getOptional(key)
            .map(item -> new ItemStack(item).getHoverName()).orElse(Component.literal(id));
    }
    private void button(int bx, int by, int bw, int bh, Component label, Runnable action) {
        addRenderableWidget(new ActionButton(bx, by, bw, bh, label, action));
    }
    private final class ActionButton extends AbstractButton {
        private final Runnable action;
        ActionButton(int x, int y, int w, int h, Component label, Runnable action) {
            super(x, y, w, h, label); this.action = action;
            setTooltip(net.minecraft.client.gui.components.Tooltip.create(label));
        }
        @Override public void onPress() { if (active && !pending) action.run(); }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float tick) {
            var style = theme.button(MenuTheme.ButtonVariant.SECONDARY, active, isHoveredOrFocused(), false);
            ThemedOverlayPanel.fillRoundedRect(g, getX(), getY(), getX() + width, getY() + height, theme.rowRadius, style.border());
            ThemedOverlayPanel.fillRoundedRect(g, getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1,
                Math.max(0, theme.rowRadius - 1), style.background());
            theme.drawWrappedText(g, font, getMessage(), getX() + 6,
                getY() + (height - theme.textHeight(font, MenuTheme.TextRole.LABEL)) / 2,
                width - 12, MenuTheme.TextRole.LABEL, style.text(), 1);
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }
}
