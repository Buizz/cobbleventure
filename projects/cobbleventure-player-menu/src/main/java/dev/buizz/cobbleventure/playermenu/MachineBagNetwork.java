package dev.buizz.cobbleventure.playermenu;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.CobblemonSounds;
import com.cobblemon.mod.common.api.tms.TechnicalMachine;
import com.cobblemon.mod.common.api.tms.TechnicalMachineRecipe;
import com.cobblemon.mod.common.api.tms.TechnicalMachines;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;
import dev.buizz.cobbleventure.playermenu.client.MachineBagClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Opens and validates server-authoritative sessions for bag-integrated machines. */
public final class MachineBagNetwork {
    private static final String VERSION = "2";
    private static final int MAX_CRAFT_QUANTITY = 64;
    private static final long SESSION_TICKS = 20L * 60L * 10L;
    private static final double MAX_DISTANCE_SQR = 64.0D;
    private static final Set<ResourceLocation> REGISTERED_MACHINES = Set.of(
        ResourceLocation.fromNamespaceAndPath("cobblemon", "tm_machine")
    );
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private MachineBagNetwork() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(MachineBagNetwork::registerPayloads);
        // Protection handlers get the opportunity to reject use before this lowest-priority adapter.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, MachineBagNetwork::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(MachineBagNetwork::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(MachineBagNetwork::onPlayerLogout);
        NeoForge.EVENT_BUS.addListener(MachineBagNetwork::onPlayerChangedDimension);
    }

    public static void close(UUID token) {
        PacketDistributor.sendToServer(new ClosePayload(token));
    }

    public static void craft(UUID token, ResourceLocation tmId, int quantity) {
        PacketDistributor.sendToServer(new CraftPayload(token, UUID.randomUUID(), tmId, quantity));
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToClient(OpenPayload.TYPE, OpenPayload.STREAM_CODEC,
            (payload, context) -> MachineBagClient.open(payload));
        registrar.playToClient(ExpiredPayload.TYPE, ExpiredPayload.STREAM_CODEC,
            (payload, context) -> MachineBagClient.expire(payload.token(), payload.reasonKey()));
        registrar.playToClient(UpdatePayload.TYPE, UpdatePayload.STREAM_CODEC,
            (payload, context) -> MachineBagClient.update(payload));
        registrar.playToServer(CraftPayload.TYPE, CraftPayload.STREAM_CODEC, MachineBagNetwork::handleCraft);
        registrar.playToServer(ClosePayload.TYPE, ClosePayload.STREAM_CODEC, MachineBagNetwork::handleClose);
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        ResourceLocation machineId = BuiltInRegistries.BLOCK.getKey(
            event.getLevel().getBlockState(event.getPos()).getBlock()
        );
        if (!REGISTERED_MACHINES.contains(machineId)) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (PlayerExtensionsKt.getBattleState(player) != null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "screen.cobbleventure_player_menu.machine.error.battle"
            ), true);
            return;
        }

        UUID token = UUID.randomUUID();
        Session session = new Session(
            token,
            machineId,
            player.level().dimension(),
            event.getPos().immutable(),
            player.serverLevel().getGameTime() + SESSION_TICKS,
            0L,
            new MachineOperationLedger()
        );
        SESSIONS.put(player.getUUID(), session);
        PacketDistributor.sendToPlayer(player, new OpenPayload(
            token, machineId, event.getPos(), workshopEntries(player)
        ));
    }

    private static void handleCraft(CraftPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        String fingerprint = payload.tmId() + ":" + payload.quantity();
        MachineOperationLedger.BeginResult begin = beginOperation(
            player, payload.token(), payload.operationId(), fingerprint
        );
        if (begin == MachineOperationLedger.BeginResult.INVALID) {
            PacketDistributor.sendToPlayer(player, new ExpiredPayload(
                payload.token(), "screen.cobbleventure_player_menu.machine.error.expired"
            ));
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (begin == MachineOperationLedger.BeginResult.REPLAYED) {
            String replayed = session.operations().result(payload.operationId());
            sendUpdate(player, payload.token(), replayed,
                replayed.equals("screen.cobbleventure_player_menu.tm_workshop.success"));
            return;
        }
        if (begin != MachineOperationLedger.BeginResult.ACCEPTED) {
            sendUpdate(player, payload.token(), begin == MachineOperationLedger.BeginResult.CONFLICT
                ? "screen.cobbleventure_player_menu.tm_workshop.error.conflict"
                : "screen.cobbleventure_player_menu.tm_workshop.error.processing", false);
            return;
        }

        String resultKey;
        try {
            resultKey = craft(player, payload.tmId(), payload.quantity());
        } catch (RuntimeException error) {
            resultKey = "screen.cobbleventure_player_menu.tm_workshop.error.transaction";
        }
        session.operations().complete(payload.operationId(), resultKey);
        sendUpdate(player, payload.token(), resultKey,
            resultKey.equals("screen.cobbleventure_player_menu.tm_workshop.success"));
    }

    private static String craft(ServerPlayer player, ResourceLocation tmId, int quantity) {
        if (quantity <= 0 || quantity > MAX_CRAFT_QUANTITY) {
            return "screen.cobbleventure_player_menu.tm_workshop.error.quantity";
        }
        TechnicalMachine tm = TechnicalMachines.INSTANCE.getByResourceLocation(tmId);
        if (tm == null) return "screen.cobbleventure_player_menu.tm_workshop.error.invalid";
        if (!unlocked(player, tm)) return "screen.cobbleventure_player_menu.tm_workshop.error.locked";

        List<ItemStack> costs = resolveCosts(player, tm, quantity);
        if (costs == null) return "screen.cobbleventure_player_menu.tm_workshop.error.materials";
        List<ItemStack> outputs = split(tm.createItemStack(), quantity);
        BagTransaction.Result transaction = BagTransaction.execute(player, costs, outputs);
        if (!transaction.success()) {
            return switch (transaction.status()) {
                case INSUFFICIENT_INPUT -> "screen.cobbleventure_player_menu.tm_workshop.error.materials";
                case OUTPUT_FULL -> "screen.cobbleventure_player_menu.tm_workshop.error.full";
                default -> "screen.cobbleventure_player_menu.tm_workshop.error.transaction";
            };
        }
        player.playNotifySound(CobblemonSounds.TM_MACHINE_CRAFT, SoundSource.BLOCKS, 1.0F, 1.0F);
        return "screen.cobbleventure_player_menu.tm_workshop.success";
    }

    private static List<ItemStack> resolveCosts(ServerPlayer player, TechnicalMachine tm, int quantity) {
        List<ItemStack> available = new ArrayList<>();
        for (ItemStack stack : BagStorage.load(player)) available.add(stack.copy());
        for (int slot = 0; slot < 36; slot++) available.add(player.getInventory().getItem(slot).copy());
        List<ItemStack> costs = new ArrayList<>();
        if (!take(available, Ingredient.of(CobblemonItems.BLANK_TM), quantity, costs)) return null;
        List<TechnicalMachineRecipe> recipe = tm.getClampedRecipe(3);
        if (recipe == null) return null;
        for (TechnicalMachineRecipe requirement : recipe) {
            long needed = (long)requirement.getCount() * quantity;
            if (needed <= 0 || needed > BagTransaction.MAX_ITEMS_PER_REQUEST
                || !take(available, requirement.getIngredient(), (int)needed, costs)) return null;
        }
        return List.copyOf(costs);
    }

    private static boolean take(
        List<ItemStack> available, Ingredient ingredient, int amount, List<ItemStack> costs
    ) {
        int remaining = amount;
        for (int index = 0; index < available.size() && remaining > 0; index++) {
            ItemStack stack = available.get(index);
            if (stack.isEmpty() || !ingredient.test(stack)) continue;
            int taken = Math.min(remaining, stack.getCount());
            costs.add(stack.copyWithCount(taken));
            stack.shrink(taken);
            remaining -= taken;
        }
        return remaining == 0;
    }

    private static List<ItemStack> split(ItemStack prototype, int amount) {
        List<ItemStack> result = new ArrayList<>();
        int remaining = amount;
        while (remaining > 0) {
            int count = Math.min(remaining, prototype.getMaxStackSize());
            result.add(prototype.copyWithCount(count));
            remaining -= count;
        }
        return List.copyOf(result);
    }

    private static void sendUpdate(ServerPlayer player, UUID token, String resultKey, boolean success) {
        PacketDistributor.sendToPlayer(player, new UpdatePayload(
            token, success, resultKey, workshopEntries(player)
        ));
    }

    private static List<TmEntry> workshopEntries(ServerPlayer player) {
        List<TmEntry> entries = new ArrayList<>();
        for (TechnicalMachine tm : TechnicalMachines.INSTANCE.getTmMap().values()) {
            var move = tm.getMoveName();
            List<CostEntry> costs = new ArrayList<>();
            costs.add(cost(Ingredient.of(CobblemonItems.BLANK_TM), 1, player));
            List<TechnicalMachineRecipe> recipe = tm.getClampedRecipe(3);
            if (recipe != null) {
                for (TechnicalMachineRecipe requirement : recipe) {
                    costs.add(cost(requirement.getIngredient(), requirement.getCount(), player));
                }
            }
            entries.add(new TmEntry(
                tm.getId(), tm.createItemStack(), move.getDisplayName(), move.getDescription(),
                tm.getType(), move.getDamageCategory().getDisplayName(), move.getPower(),
                move.getAccuracy(), move.getPp(), unlocked(player, tm), List.copyOf(costs)
            ));
        }
        entries.sort(java.util.Comparator.comparing(entry -> entry.id().toString()));
        return List.copyOf(entries);
    }

    private static CostEntry cost(Ingredient ingredient, int count, ServerPlayer player) {
        ItemStack[] choices = ingredient.getItems();
        ItemStack display = choices.length == 0 ? ItemStack.EMPTY : choices[0].copyWithCount(1);
        return new CostEntry(display, Math.max(0, count), countMatching(player, ingredient));
    }

    private static int countMatching(ServerPlayer player, Ingredient ingredient) {
        long total = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (ingredient.test(stack)) total += stack.getCount();
        }
        NonNullList<ItemStack> bag = BagStorage.load(player);
        for (ItemStack stack : bag) if (ingredient.test(stack)) total += stack.getCount();
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)total;
    }

    private static boolean unlocked(ServerPlayer player, TechnicalMachine tm) {
        return Cobblemon.INSTANCE.getConfig().getUnlockAllMoveDexMovesByDefault()
            || tm.isPassivelyObtained()
            || Cobblemon.INSTANCE.getPlayerDataManager().getTMData(player).getLearnedTMs().contains(tm.getId());
    }

    private static void handleClose(ClosePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        Session session = SESSIONS.get(player.getUUID());
        if (session != null && session.token().equals(payload.token())) {
            SESSIONS.remove(player.getUUID());
        }
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 10 != 0) return;
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || valid(player, session)) return;
        SESSIONS.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player, new ExpiredPayload(
            session.token(), "screen.cobbleventure_player_menu.machine.error.expired"
        ));
    }

    private static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }

    private static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Session session = SESSIONS.remove(player.getUUID());
        if (session != null) PacketDistributor.sendToPlayer(player, new ExpiredPayload(
            session.token(), "screen.cobbleventure_player_menu.machine.error.expired"
        ));
    }

    static MachineOperationLedger.BeginResult beginOperation(
        ServerPlayer player, UUID token, UUID operationId, String fingerprint
    ) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !session.token().equals(token) || !valid(player, session)) {
            return MachineOperationLedger.BeginResult.INVALID;
        }
        return session.operations().begin(operationId, fingerprint);
    }

    private static boolean valid(ServerPlayer player, Session session) {
        if (!player.level().dimension().equals(session.dimension())) return false;
        if (player.serverLevel().getGameTime() > session.expiresAt()) return false;
        if (PlayerExtensionsKt.getBattleState(player) != null) return false;
        if (player.distanceToSqr(
            session.pos().getX() + 0.5D,
            session.pos().getY() + 0.5D,
            session.pos().getZ() + 0.5D
        ) > MAX_DISTANCE_SQR) return false;
        ResourceLocation currentBlock = BuiltInRegistries.BLOCK.getKey(
            player.level().getBlockState(session.pos()).getBlock()
        );
        return currentBlock.equals(session.machineId()) && REGISTERED_MACHINES.contains(currentBlock);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(CobbleventurePlayerMenu.MOD_ID, path);
    }

    private record Session(
        UUID token,
        ResourceLocation machineId,
        ResourceKey<Level> dimension,
        BlockPos pos,
        long expiresAt,
        long stateVersion,
        MachineOperationLedger operations
    ) {}

    public record OpenPayload(
        UUID token, ResourceLocation machineId, BlockPos pos, List<TmEntry> entries
    )
        implements CustomPacketPayload {
        public static final Type<OpenPayload> TYPE = new Type<>(id("machine_bag_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenPayload> STREAM_CODEC =
            StreamCodec.ofMember(OpenPayload::write, OpenPayload::read);

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUUID(token);
            buffer.writeResourceLocation(machineId);
            buffer.writeBlockPos(pos);
            buffer.writeVarInt(entries.size());
            for (TmEntry entry : entries) entry.write(buffer);
        }

        private static OpenPayload read(RegistryFriendlyByteBuf buffer) {
            UUID token = buffer.readUUID();
            ResourceLocation machineId = buffer.readResourceLocation();
            BlockPos pos = buffer.readBlockPos();
            int size = Math.clamp(buffer.readVarInt(), 0, 512);
            List<TmEntry> entries = new ArrayList<>(size);
            for (int index = 0; index < size; index++) entries.add(TmEntry.read(buffer));
            return new OpenPayload(token, machineId, pos, List.copyOf(entries));
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record UpdatePayload(UUID token, boolean success, String resultKey, List<TmEntry> entries)
        implements CustomPacketPayload {
        public static final Type<UpdatePayload> TYPE = new Type<>(id("machine_bag_update"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UpdatePayload> STREAM_CODEC =
            StreamCodec.ofMember(UpdatePayload::write, UpdatePayload::read);

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUUID(token);
            buffer.writeBoolean(success);
            buffer.writeUtf(resultKey, 200);
            buffer.writeVarInt(entries.size());
            for (TmEntry entry : entries) entry.write(buffer);
        }

        private static UpdatePayload read(RegistryFriendlyByteBuf buffer) {
            UUID token = buffer.readUUID();
            boolean success = buffer.readBoolean();
            String resultKey = buffer.readUtf(200);
            int size = Math.clamp(buffer.readVarInt(), 0, 512);
            List<TmEntry> entries = new ArrayList<>(size);
            for (int index = 0; index < size; index++) entries.add(TmEntry.read(buffer));
            return new UpdatePayload(token, success, resultKey, List.copyOf(entries));
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record CraftPayload(UUID token, UUID operationId, ResourceLocation tmId, int quantity)
        implements CustomPacketPayload {
        public static final Type<CraftPayload> TYPE = new Type<>(id("machine_bag_craft"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CraftPayload> STREAM_CODEC =
            StreamCodec.ofMember(CraftPayload::write, CraftPayload::read);

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUUID(token);
            buffer.writeUUID(operationId);
            buffer.writeResourceLocation(tmId);
            buffer.writeVarInt(quantity);
        }

        private static CraftPayload read(RegistryFriendlyByteBuf buffer) {
            return new CraftPayload(
                buffer.readUUID(), buffer.readUUID(), buffer.readResourceLocation(), buffer.readVarInt()
            );
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record CostEntry(ItemStack stack, int required, int owned) {
        private void write(RegistryFriendlyByteBuf buffer) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, stack);
            buffer.writeVarInt(required);
            buffer.writeVarInt(owned);
        }

        private static CostEntry read(RegistryFriendlyByteBuf buffer) {
            return new CostEntry(
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer), buffer.readVarInt(), buffer.readVarInt()
            );
        }
    }

    public record TmEntry(
        ResourceLocation id,
        ItemStack stack,
        net.minecraft.network.chat.Component name,
        net.minecraft.network.chat.Component description,
        String type,
        net.minecraft.network.chat.Component category,
        double power,
        double accuracy,
        int pp,
        boolean unlocked,
        List<CostEntry> costs
    ) {
        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeResourceLocation(id);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, stack);
            net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.encode(buffer, name);
            net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.encode(buffer, description);
            buffer.writeUtf(type, 40);
            net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.encode(buffer, category);
            buffer.writeDouble(power);
            buffer.writeDouble(accuracy);
            buffer.writeVarInt(pp);
            buffer.writeBoolean(unlocked);
            buffer.writeVarInt(costs.size());
            for (CostEntry cost : costs) cost.write(buffer);
        }

        private static TmEntry read(RegistryFriendlyByteBuf buffer) {
            ResourceLocation id = buffer.readResourceLocation();
            ItemStack stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
            var name = net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.decode(buffer);
            var description = net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.decode(buffer);
            String type = buffer.readUtf(40);
            var category = net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.decode(buffer);
            double power = buffer.readDouble();
            double accuracy = buffer.readDouble();
            int pp = buffer.readVarInt();
            boolean unlocked = buffer.readBoolean();
            int size = Math.clamp(buffer.readVarInt(), 0, 8);
            List<CostEntry> costs = new ArrayList<>(size);
            for (int index = 0; index < size; index++) costs.add(CostEntry.read(buffer));
            return new TmEntry(
                id, stack, name, description, type, category, power, accuracy, pp, unlocked, List.copyOf(costs)
            );
        }
    }

    public record ExpiredPayload(UUID token, String reasonKey) implements CustomPacketPayload {
        public static final Type<ExpiredPayload> TYPE = new Type<>(id("machine_bag_expired"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ExpiredPayload> STREAM_CODEC =
            StreamCodec.ofMember(ExpiredPayload::write, ExpiredPayload::read);

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUUID(token);
            buffer.writeUtf(reasonKey, 160);
        }

        private static ExpiredPayload read(RegistryFriendlyByteBuf buffer) {
            return new ExpiredPayload(buffer.readUUID(), buffer.readUtf(160));
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ClosePayload(UUID token) implements CustomPacketPayload {
        public static final Type<ClosePayload> TYPE = new Type<>(id("machine_bag_close"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ClosePayload> STREAM_CODEC =
            StreamCodec.ofMember(ClosePayload::write, ClosePayload::read);

        private void write(RegistryFriendlyByteBuf buffer) { buffer.writeUUID(token); }
        private static ClosePayload read(RegistryFriendlyByteBuf buffer) {
            return new ClosePayload(buffer.readUUID());
        }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
