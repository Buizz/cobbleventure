package dev.buizz.cobbleventure.adventure.mixin;

import com.cobblemon.mod.common.block.multiblock.FossilMultiblockStructure;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Field contract shared by Cobblemon 1.7.3 and 1.8. */
@Mixin(value = FossilMultiblockStructure.class, remap = false)
public interface FossilMachineAccess {
    @Accessor("timeRemaining") void cobbleventure$setTimeRemaining(int ticks);
    @Accessor("organicMaterialInside") void cobbleventure$setOrganicMaterial(int amount);
    @Accessor("fossilOwnerUUID") void cobbleventure$setOwner(UUID owner);
    @Accessor("protectionTime") void cobbleventure$setProtection(int ticks);
}
