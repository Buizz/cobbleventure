package dev.buizz.cobbleventure.playermenu;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProgressIdArgumentTest {
    @Test void acceptsBareAndLegacyQuotedIdsThroughDispatcher() throws Exception {
        var dispatcher = new CommandDispatcher<Object>();
        dispatcher.register(LiteralArgumentBuilder.literal("grant")
            .then(RequiredArgumentBuilder.argument("id", StringArgumentType.greedyString())
                .executes(context -> {
                    assertEquals("cobbleventure:badge/kanto/boulder", ProgressIdArgument.parse(context.getArgument("id", String.class)));
                    return 1;
                })));
        assertEquals(1, dispatcher.execute("grant cobbleventure:badge/kanto/boulder", new Object()));
        assertEquals(1, dispatcher.execute("grant \"cobbleventure:badge/kanto/boulder\"", new Object()));
        assertThrows(CommandSyntaxException.class,
            () -> dispatcher.execute("grant cobbleventure:badge/kanto/boulder extra", new Object()));
    }

    @Test void preservesLeagueIdsAndRejectsMalformedInput() throws Exception {
        assertEquals("cobbleventure:league/kanto/champion",
            ProgressIdArgument.parse("cobbleventure:league/kanto/champion"));
        assertThrows(CommandSyntaxException.class, () -> ProgressIdArgument.parse(""));
        assertThrows(CommandSyntaxException.class, () -> ProgressIdArgument.parse("\"unfinished"));
    }
}
