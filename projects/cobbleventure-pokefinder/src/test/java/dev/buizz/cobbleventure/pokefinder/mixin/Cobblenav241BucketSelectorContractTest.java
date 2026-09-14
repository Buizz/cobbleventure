package dev.buizz.cobbleventure.pokefinder.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

final class Cobblenav241BucketSelectorContractTest {
    private static final String TEXT_OWNER = "com/cobblemon/mod/common/api/text/TextKt";
    private static final String SELF_HOVER_DESCRIPTOR =
        "(Lnet/minecraft/network/chat/MutableComponent;Lnet/minecraft/network/chat/MutableComponent;)"
            + "Lnet/minecraft/network/chat/MutableComponent;";

    @Test
    void selfHoverIsSkippedWithoutChangingTheRenderedComponent() {
        var component = Component.literal("bucket");

        assertSame(component, CobblenavBucketSelectorMixin.safeHover(component, component));
        assertNull(component.getStyle().getHoverEvent());
    }

    @Test
    void distinctHoverContentsKeepCobblemonBehavior() throws Exception {
        ClassNode mixin = classNode(
            "dev/buizz/cobbleventure/pokefinder/mixin/CobblenavBucketSelectorMixin.class"
        );
        var safeHover = mixin.methods.stream()
            .filter(method -> method.name.equals("safeHover"))
            .findFirst()
            .orElseThrow();
        long delegatedCalls = countHoverCalls(safeHover.instructions);

        assertEquals(1, delegatedCalls, "Distinct components must retain Cobblemon's hover behavior");
    }

    @Test
    void cobblenav241StillContainsTheSinglePatchedCallSite() throws Exception {
        ClassNode target = classNode(
            "com/metacontent/cobblenav/client/gui/widget/location/BucketSelectorWidget.class"
        );
        var renderWidget = target.methods.stream()
            .filter(method -> method.name.equals("renderWidget"))
            .findFirst()
            .orElseThrow();
        long matchingCalls = countHoverCalls(renderWidget.instructions);

        assertEquals(1, matchingCalls, "CobbleNav update changed the guarded 2.4.1 call site");
    }

    @Test
    void clientMixinIsRegistered() throws Exception {
        try (var stream = getClass().getResourceAsStream("/cobbleventure_pokefinder.mixins.json")) {
            assertNotNull(stream);
            var root = JsonParser.parseReader(new InputStreamReader(stream)).getAsJsonObject();
            assertTrue(root.getAsJsonArray("client").asList().stream().anyMatch(value ->
                value.getAsString().equals("CobblenavBucketSelectorMixin")));
        }
    }

    private static ClassNode classNode(String resource) throws Exception {
        try (var stream = Cobblenav241BucketSelectorContractTest.class
            .getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            var node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }

    private static long countHoverCalls(Iterable<AbstractInsnNode> instructions) {
        long matchingCalls = 0;
        for (AbstractInsnNode instruction : instructions) {
            if (instruction instanceof MethodInsnNode call
                && call.getOpcode() == Opcodes.INVOKESTATIC
                && call.owner.equals(TEXT_OWNER)
                && call.name.equals("onHover")
                && call.desc.equals(SELF_HOVER_DESCRIPTOR)) {
                matchingCalls++;
            }
        }
        return matchingCalls;
    }
}
