package dev.buizz.cobbleventure.adventure.research;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;
import com.google.gson.*;
import dev.buizz.cobbleventure.adventure.event.EventNpcBindingRepository;
import dev.buizz.cobbleventure.adventure.research.client.ResearchScreen;
import dev.buizz.cobbleventure.playermenu.BagStorage;
import fr.harmex.cobbledollars.common.utils.extensions.PlayerExtensionKt;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-authoritative quotes and single-use transactions; no client-supplied prices. */
public final class ResearchNetwork {
    public static final Stats[] STATS = {Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED};
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private record Session(UUID token, UUID npc, String kind, long expires, JsonObject config,
                           Map<UUID, String> fingerprints, UUID craftedPokemon, String craftedItem) {}
    private ResearchNetwork() {}
    public static void register(IEventBus bus) {
        bus.addListener(ResearchNetwork::payloads);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> SESSIONS.remove(event.getEntity().getUUID()));
    }
    private static void payloads(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        r.playToClient(View.TYPE, View.CODEC, (p, c) -> ResearchScreen.open(p));
        r.playToServer(Action.TYPE, Action.CODEC, ResearchNetwork::act);
    }
    static String kind(Entity npc) {
        String script = EventNpcBindingRepository.instance().findByEntityTags(npc.getTags()).map(b -> b.scriptId()).orElse("");
        for (String value : List.of("z_move", "mega", "dynamax", "stats"))
            if (script.equals("cobbleventure:event_script/facilities/research_" + value)) return value;
        return "";
    }
    public static void open(ServerPlayer player, Entity npc) {
        if (!npc.isAlive() || player.distanceToSqr(npc) > 64 || kind(npc).isEmpty()) return;
        if (PlayerExtensionsKt.getBattleState(player) != null) { player.sendSystemMessage(text("battle")); return; }
        try { send(player, npc, kind(npc), "", null, ""); }
        catch (IllegalArgumentException ex) { player.sendSystemMessage(text("unavailable")); }
    }
    private static String fingerprint(Pokemon p) {
        return p.showdownId() + "|" + p.getAspects() + "|" + p.getMoveSet().getMoves().stream().map(m -> m.getName()).toList()
            + "|" + Arrays.toString(ivs(p)) + "|" + Arrays.toString(evs(p)) + "|" + p.getDmaxLevel() + "|" + p.getGmaxFactor();
    }
    static int[] ivs(Pokemon p) { return Arrays.stream(STATS).mapToInt(s -> p.getIvs().getEffectiveBattleIV(s)).toArray(); }
    static int[] evs(Pokemon p) { return Arrays.stream(STATS).mapToInt(s -> p.getEvs().getOrDefault(s)).toArray(); }
    private static void send(ServerPlayer player, Entity npc, String kind, String feedback, UUID crafted, String item) {
        JsonObject config = ResearchCatalog.load(player.server), data = new JsonObject();
        data.addProperty("kind", kind); data.addProperty("feedback", feedback);
        data.addProperty("balance", PlayerExtensionKt.getCobbleDollars(player).toString());
        data.add("costs", config.get("costs"));
        data.add("materials", config.get("materials"));
        data.addProperty("crafted_item", item); data.addProperty("crafted_pokemon", crafted == null ? "" : crafted.toString());
        JsonObject party = new JsonObject(); Map<UUID, String> fingerprints = new HashMap<>();
        for (Pokemon p : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            JsonObject row = new JsonObject();
            var options = ResearchCatalog.options(player.server, config, kind, p);
            JsonArray choices = new JsonArray(); options.forEach(o -> choices.add(o.json())); row.add("options", choices);
            row.add("ivs", array(ivs(p))); row.add("evs", array(evs(p)));
            row.addProperty("level", p.getDmaxLevel()); row.addProperty("factor", p.getGmaxFactor());
            row.addProperty("gmax", ResearchCatalog.gmax(p));
            row.addProperty("allowed", kind.equals("stats") || kind.equals("dynamax") && ResearchCatalog.dynamax(config, p) || !options.isEmpty());
            boolean moveMega = kind.equals("mega") && options.isEmpty() && p.getSpecies().getForms().stream().anyMatch(f ->
                f.getName().toLowerCase(Locale.ROOT).contains("mega") && f.getRequiredMove() != null && !f.getRequiredMove().isBlank());
            row.addProperty("reason", moveMega ? "special_mega" : "impossible");
            party.add(p.getUuid().toString(), row); fingerprints.put(p.getUuid(), fingerprint(p));
        }
        data.add("party", party);
        UUID token = UUID.randomUUID();
        SESSIONS.put(player.getUUID(), new Session(token, npc.getUUID(), kind, player.serverLevel().getGameTime() + 12000,
            config, fingerprints, crafted, item));
        PacketDistributor.sendToPlayer(player, new View(token, data.toString()));
    }
    private static JsonArray array(int[] values) { JsonArray a = new JsonArray(); for (int v : values) a.add(v); return a; }
    private static int[] targets(JsonObject data, String key) {
        if (!data.has(key) || !data.get(key).isJsonArray()) throw new IllegalArgumentException("invalid");
        var values = data.getAsJsonArray(key);
        if (values.size() != 6) throw new IllegalArgumentException("Invalid stats");
        int[] result = new int[6];
        for (int i = 0; i < 6; i++) {
            if (!values.get(i).isJsonPrimitive() || !values.get(i).getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("invalid");
            result[i] = values.get(i).getAsBigDecimal().intValueExact();
        }
        return result;
    }
    private static void act(Action action, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !session.token.equals(action.token)) return;
        // Consume before any mutation: a replay cannot charge or create an item twice.
        SESSIONS.remove(player.getUUID());
        Entity npc = player.serverLevel().getEntity(session.npc);
        if (npc == null || !npc.isAlive() || player.distanceToSqr(npc) > 64 || !kind(npc).equals(session.kind)
            || player.serverLevel().getGameTime() > session.expires || PlayerExtensionsKt.getBattleState(player) != null) {
            player.sendSystemMessage(text("expired")); return;
        }
        UUID crafted = null; String craftedItem = "", feedback = "success";
        try {
            Pokemon pokemon = null;
            for (Pokemon p : Cobblemon.INSTANCE.getStorage().getParty(player)) if (p.getUuid().equals(action.pokemon)) pokemon = p;
            if (pokemon == null) throw new IllegalArgumentException("missing");
            if (!Objects.equals(session.fingerprints.get(pokemon.getUuid()), fingerprint(pokemon))) throw new IllegalArgumentException("changed");
            JsonObject data = JsonParser.parseString(action.data).getAsJsonObject();
            Inventory inventory = new Inventory(player);
            long price = 0;
            int[] ivs = null, evs = null; Integer level = null; Boolean factor = null;
            if (action.kind.equals("equip")) {
                if (!Objects.equals(session.craftedPokemon, pokemon.getUuid()) || session.craftedItem.isEmpty()) throw new IllegalArgumentException("missing");
                inventory.consume(session.craftedItem, 1);
                inventory.add(pokemon.heldItem());
                ItemStack offered = ResearchCatalog.item(session.craftedItem);
                if (offered.isEmpty()) throw new IllegalArgumentException("missing");
                // Use Cobblemon's cancellable held-item exchange and verify the resulting item before committing inventory.
                ItemStack previous = pokemon.heldItem();
                ItemStack returned = pokemon.swapHeldItem(offered, false, false);
                if (!ItemStack.isSameItemSameComponents(pokemon.heldItem(), offered)) throw new IllegalArgumentException("equip_failed");
                if (!ItemStack.matches(previous, returned)) {
                    pokemon.swapHeldItem(previous, false, false);
                    throw new IllegalArgumentException("equip_failed");
                }
                inventory.commit(player);
            } else if (action.kind.equals("apply")) {
                if (session.kind.equals("mega") || session.kind.equals("z_move")) {
                    if (!data.has("item") || !data.get("item").isJsonPrimitive()) throw new IllegalArgumentException("invalid");
                    String item = data.get("item").getAsString();
                    var option = ResearchCatalog.options(player.server, session.config, session.kind, pokemon).stream()
                        .filter(o -> o.item().equals(item)).findFirst().orElseThrow(() -> new IllegalArgumentException("changed"));
                    inventory.consume(option.ingredient(), option.count());
                    if (option.extraCount() > 0) inventory.consume(option.extra(), option.extraCount());
                    inventory.add(ResearchCatalog.item(option.item())); price = option.money();
                    crafted = pokemon.getUuid(); craftedItem = option.item();
                } else if (session.kind.equals("stats")) {
                    ivs = targets(data, "ivs"); evs = targets(data, "evs");
                    price = ResearchPolicy.statCost(ivs(pokemon), ivs, 31, 186, ResearchCatalog.cost(session.config, "iv_point"))
                        + ResearchPolicy.statCost(evs(pokemon), evs, 252, 510, ResearchCatalog.cost(session.config, "ev_point"));
                } else if (session.kind.equals("dynamax")) {
                    if (!ResearchCatalog.dynamax(session.config, pokemon)) throw new IllegalArgumentException("changed");
                    if (!data.has("level") || !data.get("level").isJsonPrimitive() || !data.getAsJsonPrimitive("level").isNumber()
                        || !data.has("factor") || !data.get("factor").isJsonPrimitive()
                        || !data.getAsJsonPrimitive("factor").isBoolean()) throw new IllegalArgumentException("invalid");
                    level = data.get("level").getAsBigDecimal().intValueExact(); factor = data.get("factor").getAsBoolean();
                    int count = ResearchPolicy.mushrooms(pokemon.getDmaxLevel(), level, pokemon.getGmaxFactor(), factor,
                        ResearchCatalog.gmax(pokemon), ResearchCatalog.cost(session.config, "dynamax_level_mushrooms"), ResearchCatalog.cost(session.config, "gmax_mushrooms"));
                    inventory.consume(ResearchCatalog.material(session.config, "mushroom"), count);
                } else throw new IllegalArgumentException("missing");
                BigInteger balance = PlayerExtensionKt.getCobbleDollars(player);
                if (balance.compareTo(BigInteger.valueOf(price)) < 0) throw new IllegalArgumentException("money");
                if (ivs != null) {
                    // Decrease first so reallocating a full 510-EV spread does not clamp intermediate increases.
                    for (Stats s : STATS) pokemon.setEV(s, 0);
                    for (int i = 0; i < 6; i++) {
                        pokemon.setIV(STATS[i], ivs[i]); pokemon.hyperTrainIV(STATS[i], ivs[i]);
                        pokemon.setEV(STATS[i], evs[i]);
                    }
                    pokemon.setCurrentHealth(Math.min(pokemon.getCurrentHealth(), pokemon.getMaxHealth()));
                }
                if (level != null) { pokemon.setDmaxLevel(level); pokemon.setGmaxFactor(factor); }
                inventory.commit(player);
                PlayerExtensionKt.setCobbleDollars(player, balance.subtract(BigInteger.valueOf(price)));
            } else throw new IllegalArgumentException("missing");
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException | JsonParseException ex) {
            feedback = Set.of("materials", "full", "money", "changed", "equip_failed").contains(ex.getMessage()) ? ex.getMessage() : "invalid";
            crafted = session.craftedPokemon; craftedItem = session.craftedItem;
        }
        send(player, npc, session.kind, feedback, crafted, craftedItem);
    }
    /** All item work happens on copies. Failures cannot consume a partial set of materials. */
    private static final class Inventory {
        private final NonNullList<ItemStack> bag;
        private final List<ItemStack> main;
        Inventory(ServerPlayer p) { bag = BagStorage.load(p); main = p.getInventory().items.stream().map(ItemStack::copy).toList(); }
        void consume(String id, int count) {
            if (count < 0) throw new IllegalArgumentException("invalid");
            var item = ResearchCatalog.item(id);
            if (count > 0 && item.isEmpty()) throw new IllegalArgumentException("materials");
            int remaining = count;
            for (List<ItemStack> storage : List.of(bag, main)) for (ItemStack s : storage) {
                if (!s.isEmpty() && s.is(item.getItem())) {
                    int taken = Math.min(remaining, s.getCount()); s.shrink(taken); remaining -= taken;
                }
            }
            if (remaining != 0) throw new IllegalArgumentException("materials");
        }
        void add(ItemStack item) {
            ItemStack copy = item.copy(); BagStorage.add(bag, copy);
            if (!copy.isEmpty()) throw new IllegalArgumentException("full");
        }
        void commit(ServerPlayer p) {
            BagStorage.save(p, bag);
            for (int i = 0; i < main.size(); i++) p.getInventory().items.set(i, main.get(i));
            p.getInventory().setChanged(); p.containerMenu.broadcastChanges();
        }
    }
    public static Component text(String key) { return Component.translatable("research.cobbleventure." + key); }
    private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath("cobbleventure_adventure", name); }
    public record View(UUID token, String data) implements CustomPacketPayload {
        public static final Type<View> TYPE = new Type<>(id("research_view"));
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = StreamCodec.ofMember(View::write, b -> new View(b.readUUID(), b.readUtf(131072)));
        private void write(RegistryFriendlyByteBuf b) { b.writeUUID(token); b.writeUtf(data, 131072); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Action(UUID token, UUID pokemon, String kind, String data) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(id("research_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.ofMember(Action::write, b -> new Action(b.readUUID(), b.readUUID(), b.readUtf(16), b.readUtf(2048)));
        private void write(RegistryFriendlyByteBuf b) { b.writeUUID(token); b.writeUUID(pokemon); b.writeUtf(kind, 16); b.writeUtf(data, 2048); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
