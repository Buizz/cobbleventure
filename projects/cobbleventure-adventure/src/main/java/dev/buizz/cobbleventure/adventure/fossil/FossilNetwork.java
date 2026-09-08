package dev.buizz.cobbleventure.adventure.fossil;

import com.cobblemon.mod.common.api.fossil.Fossils;
import dev.buizz.cobbleventure.adventure.event.EventNpcBindingRepository;
import dev.buizz.cobbleventure.adventure.fossil.client.FossilClient;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class FossilNetwork {
    public static final String SCRIPT = "cobbleventure:event_script/facilities/fossil_researcher";
    private FossilNetwork() {}
    public static void register(IEventBus bus) { bus.addListener(FossilNetwork::payloads); }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("cobbleventure_adventure", path); }
    private static void payloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(View.TYPE, View.CODEC, (payload, context) -> FossilClient.open(payload));
        registrar.playToServer(Action.TYPE, Action.CODEC, FossilNetwork::act);
    }
    public static void open(ServerPlayer player, Entity npc) { send(player, npc, Component.empty(), false); }
    private static void act(Action action, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        var npc = player.serverLevel().getEntity(action.npc());
        if (npc == null || !npc.isAlive() || player.distanceToSqr(npc) > 64
            || EventNpcBindingRepository.instance().findByEntityTags(npc.getTags())
                .filter(binding -> SCRIPT.equals(binding.scriptId())).isEmpty()) {
            PacketDistributor.sendToPlayer(player, new View(action.npc(), List.of(), Component.empty(),
                FossilLaboratoryService.text("too_far"), false, false, true));
            return;
        }
        Component feedback = Component.empty();
        boolean close = false;
        if (action.kind() == Kind.START) {
            var recipeId = ResourceLocation.tryParse(action.recipe());
            var fossil = recipeId == null ? null : Fossils.getByIdentifier(recipeId);
            feedback = fossil == null ? FossilLaboratoryService.text("missing") : FossilLaboratoryService.start(player, npc, fossil);
            var machine = FossilLaboratoryService.findMachine(npc);
            close = machine != null && machine.isRunning()
                && FossilLaboratoryService.job(machine, player.level()).hasUUID("owner")
                && FossilLaboratoryService.job(machine, player.level()).getUUID("owner").equals(player.getUUID());
        } else if (action.kind() == Kind.COLLECT) feedback = FossilLaboratoryService.collect(player, npc);
        send(player, npc, feedback, close);
    }
    private static void send(ServerPlayer player, Entity npc, Component feedback, boolean close) {
        var machine = FossilLaboratoryService.findMachine(npc);
        boolean ready = false, selectable = false;
        Component status = FossilLaboratoryService.text("no_machine");
        if (machine != null) {
            var job = FossilLaboratoryService.job(machine, player.level());
            if (!job.isEmpty()) {
                boolean own = job.hasUUID("owner") && job.getUUID("owner").equals(player.getUUID());
                ready = own && machine.getHasCreatedPokemon();
                status = FossilLaboratoryService.text(!own ? "busy" : ready ? "ready" : "remaining",
                    Math.max(0, (machine.getTimeRemaining() + 479) / 480));
            } else {
                selectable = !machine.isRunning() && !machine.getHasCreatedPokemon()
                    && machine.getFossilInventory().isEmpty() && machine.getOrganicMaterialInside() == 0;
                status = FossilLaboratoryService.text(selectable ? "select" : "busy");
            }
        }
        List<Entry> entries = new ArrayList<>();
        for (var fossil : FossilLaboratoryService.available(player)) {
            int[] slots = FossilLaboratoryService.ingredients(player, fossil);
            List<ItemStack> items = new ArrayList<>();
            for (int slot : slots) items.add(player.getInventory().items.get(slot).copyWithCount(1));
            if (Fossils.getFossilByItemStacks(items) != fossil) continue;
            entries.add(new Entry(fossil.getIdentifier().toString(), fossil.getName(), items));
        }
        PacketDistributor.sendToPlayer(player, new View(npc.getUUID(), entries, status, feedback, selectable, ready, close));
    }
    public enum Kind { START, COLLECT, REFRESH }
    public record Entry(String id, Component name, List<ItemStack> items) {}
    public record Action(UUID npc, Kind kind, String recipe) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(id("fossil_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.ofMember(Action::write, Action::read);
        private void write(RegistryFriendlyByteBuf b) { b.writeUUID(npc); b.writeEnum(kind); b.writeUtf(recipe, 256); }
        private static Action read(RegistryFriendlyByteBuf b) { return new Action(b.readUUID(), b.readEnum(Kind.class), b.readUtf(256)); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record View(UUID npc, List<Entry> entries, Component status, Component feedback,
                       boolean selectable, boolean ready, boolean close) implements CustomPacketPayload {
        public static final Type<View> TYPE = new Type<>(id("fossil_view"));
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = StreamCodec.ofMember(View::write, View::read);
        private void write(RegistryFriendlyByteBuf b) {
            b.writeUUID(npc); b.writeVarInt(entries.size());
            for (Entry e : entries) {
                b.writeUtf(e.id(), 256); ComponentSerialization.TRUSTED_STREAM_CODEC.encode(b, e.name());
                b.writeVarInt(e.items().size());
                for (ItemStack item : e.items()) ItemStack.STREAM_CODEC.encode(b, item);
            }
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(b, status);
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(b, feedback);
            b.writeBoolean(selectable); b.writeBoolean(ready); b.writeBoolean(close);
        }
        private static View read(RegistryFriendlyByteBuf b) {
            UUID npc = b.readUUID(); int count = b.readVarInt();
            if (count < 0 || count > 1024) throw new IllegalArgumentException("Too many fossil recipes");
            List<Entry> entries = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String id = b.readUtf(256); Component name = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(b);
                int size = b.readVarInt();
                if (size < 1 || size > 64) throw new IllegalArgumentException("Invalid fossil ingredients");
                List<ItemStack> items = new ArrayList<>();
                for (int j = 0; j < size; j++) items.add(ItemStack.STREAM_CODEC.decode(b));
                entries.add(new Entry(id, name, items));
            }
            return new View(npc, entries, ComponentSerialization.TRUSTED_STREAM_CODEC.decode(b),
                ComponentSerialization.TRUSTED_STREAM_CODEC.decode(b), b.readBoolean(), b.readBoolean(), b.readBoolean());
        }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
