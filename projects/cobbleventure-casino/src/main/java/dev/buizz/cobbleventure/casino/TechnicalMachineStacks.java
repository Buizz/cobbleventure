package dev.buizz.cobbleventure.casino;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Resolves native Cobblemon 1.8 TMs while remaining loadable with Cobblemon 1.7.3. */
public final class TechnicalMachineStacks {
    private static final String TECHNICAL_MACHINE = "cobblemon:technical_machine";

    private TechnicalMachineStacks() {}

    public static ItemStack create(String itemId, String move) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(id);
        if (move == null || move.isBlank()) return new ItemStack(item);
        if (!TECHNICAL_MACHINE.equals(itemId)) return ItemStack.EMPTY;

        try {
            Class<?> registryClass = Class.forName(
                "com.cobblemon.mod.common.api.tms.TechnicalMachines"
            );
            Field instanceField = registryClass.getField("INSTANCE");
            Object registry = instanceField.get(null);
            Method lookup = registryClass.getMethod(
                "getByResourceLocation", ResourceLocation.class
            );
            Object technicalMachine = lookup.invoke(
                registry, ResourceLocation.fromNamespaceAndPath("cobblemon", move)
            );
            if (technicalMachine == null) return ItemStack.EMPTY;
            Object created = technicalMachine.getClass().getMethod("createItemStack")
                .invoke(technicalMachine);
            return created instanceof ItemStack stack && stack.is(item)
                ? stack
                : ItemStack.EMPTY;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return ItemStack.EMPTY;
        }
    }
}
