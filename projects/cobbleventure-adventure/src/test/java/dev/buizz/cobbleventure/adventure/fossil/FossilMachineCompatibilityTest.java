package dev.buizz.cobbleventure.adventure.fossil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import static org.junit.jupiter.api.Assertions.*;

/** Runs against the selected Cobblemon artifact without loading Minecraft or client-only classes. */
class FossilMachineCompatibilityTest {
    @Test void installedMachineSupportsTheLaboratoryHooks() throws Exception {
        String path = "com/cobblemon/mod/common/block/multiblock/FossilMultiblockStructure.class";
        Map<String, String> fields = new HashMap<>();
        Map<String, Object> constants = new HashMap<>();
        Set<String> methods = new HashSet<>();
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    fields.put(name, descriptor); constants.put(name, value); return null;
                }
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    methods.add(name + descriptor); return null;
                }
            }, ClassReader.SKIP_CODE);
        }
        for (String field : ListHolder.INT_FIELDS) assertEquals("I", fields.get(field), field);
        assertEquals("Ljava/util/UUID;", fields.get("fossilOwnerUUID"));
        assertEquals(14400, constants.get("TIME_TO_TAKE"));
        for (String name : new String[]{"tick", "startMachine", "updateProgress", "syncToClient", "markDirty"})
            assertTrue(methods.contains(name + "(Lnet/minecraft/world/level/Level;)V"), name);
        assertTrue(methods.contains("useWithoutItem(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"));
    }
    private static class ListHolder {
        static final String[] INT_FIELDS = {"timeRemaining", "organicMaterialInside", "protectionTime"};
    }
}
