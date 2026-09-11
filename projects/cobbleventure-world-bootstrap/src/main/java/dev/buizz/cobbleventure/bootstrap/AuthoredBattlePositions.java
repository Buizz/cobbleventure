package dev.buizz.cobbleventure.bootstrap;

import com.cobblemon.mod.common.battles.BattleRegistry;
import dev.buizz.cobbleventure.playermenu.BattleIntro;
import dev.buizz.cobbleventure.playermenu.BattlePositioningEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;

/** Pairs an assigned NPC slot with its optional <slot>_battle_player anchor. */
final class AuthoredBattlePositions {
    private static final Map<String, Arena> ARENAS = new LinkedHashMap<>();

    private AuthoredBattlePositions() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(AuthoredBattlePositions::position);
    }

    static void clear() { ARENAS.clear(); }

    static void add(String key, ServerLevel level, String npc, BlockPos player, BlockPos opponent) {
        ARENAS.put(key, new Arena(level, "cobbleventure_npc/" + npc.replace(':', '/'),
            player, opponent));
    }

    static boolean inBattle(ServerPlayer player) {
        return BattleIntro.isPreparingBattle(player)
            || BattleRegistry.getBattleByParticipatingPlayerId(player.getUUID()) != null;
    }

    private static void position(BattlePositioningEvent event) {
        if (event.isCanceled()) return;
        ServerPlayer player = event.player();
        Entity npc = event.opponent();
        for (Arena arena : ARENAS.values()) {
            if (player.serverLevel() != arena.level || npc.level() != arena.level
                || !AuthoredNpcIdentity.matches(npc.getTags(), arena.npcTag)
                || npc.distanceToSqr(Vec3.atBottomCenterOf(arena.opponent)) > 16) continue;
            AABB room = new AABB(Vec3.atLowerCornerOf(arena.player),
                Vec3.atLowerCornerOf(arena.opponent)).inflate(8, 4, 8);
            boolean occupied = arena.level.players().stream().anyMatch(other -> other != player
                && room.contains(other.position()) && inBattle(other));
            if (occupied || !room.contains(player.position())
                || !safe(arena.level, arena.player) || !safe(arena.level, arena.opponent)) {
                event.setCanceled(true);
                player.displayClientMessage(Component.literal("지금은 이 방에서 전투를 준비할 수 없습니다."), true);
                return;
            }
            float yaw = (float) Math.toDegrees(Math.atan2(
                -(arena.opponent.getX() - arena.player.getX()),
                arena.opponent.getZ() - arena.player.getZ()));
            player.teleportTo(arena.level, arena.player.getX() + 0.5, arena.player.getY(),
                arena.player.getZ() + 0.5, yaw, 0);
            if (npc instanceof Mob mob) mob.getNavigation().stop();
            npc.teleportTo(arena.opponent.getX() + 0.5, arena.opponent.getY(), arena.opponent.getZ() + 0.5);
            npc.setYRot(yaw + 180);
            if (npc instanceof LivingEntity living) {
                living.setYHeadRot(yaw + 180);
                living.setYBodyRot(yaw + 180);
            }
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
            npc.setDeltaMovement(Vec3.ZERO);
            return;
        }
    }

    private static boolean safe(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
            && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
            && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
            && level.getFluidState(pos).isEmpty() && level.getFluidState(pos.above()).isEmpty();
    }

    private record Arena(ServerLevel level, String npcTag, BlockPos player, BlockPos opponent) {}
}
