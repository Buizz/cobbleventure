package dev.buizz.cobbleventure.experience;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.ExperienceGainedEvent;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.buizz.cobbleventure.experience.client.ExperienceOverlay;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ExperienceNetwork {
    private static final String VERSION = "2";

    private ExperienceNetwork() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(ExperienceNetwork::registerPayloads);
        CobblemonEvents.EXPERIENCE_GAINED_EVENT_POST.subscribe(ExperienceNetwork::onExperience);
    }

    private static void onExperience(ExperienceGainedEvent.Post event) {
        if (event.getExperience() <= 0) return;
        Pokemon pokemon = event.getPokemon();
        ServerPlayer owner = pokemon.getOwnerPlayer();
        if (owner == null) return;

        int currentExperience = pokemon.getExperience();
        int previousExperience = Math.max(0, currentExperience - event.getExperience());
        int levelStart = pokemon.getExperienceGroup().getExperience(event.getCurrentLevel());
        int nextLevel = event.getCurrentLevel() >= Cobblemon.INSTANCE.getConfig().getMaxPokemonLevel()
            ? levelStart : pokemon.getExperienceGroup().getExperience(event.getCurrentLevel() + 1);
        PacketDistributor.sendToPlayer(owner, new ExperiencePayload(
            pokemon.getUuid(), pokemon.getDisplayName(false), event.getExperience(), previousExperience,
            currentExperience, event.getPreviousLevel(), event.getCurrentLevel(),
            levelStart, nextLevel
        ));
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToClient(
            ExperiencePayload.TYPE, ExperiencePayload.STREAM_CODEC, ExperienceNetwork::handle
        );
    }

    private static void handle(ExperiencePayload payload, IPayloadContext context) {
        ExperienceOverlay.show(payload);
    }

    public record ExperiencePayload(
        java.util.UUID pokemonId, Component pokemonName, int gained, int previousExperience, int currentExperience,
        int previousLevel, int currentLevel, int levelStart, int nextLevel
    ) implements CustomPacketPayload {
        private static final Type<ExperiencePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CobbleventureExperience.MOD_ID, "experience")
        );
        private static final StreamCodec<RegistryFriendlyByteBuf, ExperiencePayload> STREAM_CODEC =
            StreamCodec.of(ExperiencePayload::write, ExperiencePayload::read);

        private static void write(RegistryFriendlyByteBuf buffer, ExperiencePayload payload) {
            buffer.writeUUID(payload.pokemonId());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, payload.pokemonName());
            buffer.writeVarInt(payload.gained());
            buffer.writeVarInt(payload.previousExperience());
            buffer.writeVarInt(payload.currentExperience());
            buffer.writeVarInt(payload.previousLevel());
            buffer.writeVarInt(payload.currentLevel());
            buffer.writeVarInt(payload.levelStart());
            buffer.writeVarInt(payload.nextLevel());
        }

        private static ExperiencePayload read(RegistryFriendlyByteBuf buffer) {
            return new ExperiencePayload(
                buffer.readUUID(),
                ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()
            );
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
