package dev.buizz.cobbleventure.playermenu;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CheatTargetArgumentTest {
    @Test void bareUuidReproducesPlayerOnlyArgumentFailure() {
        assertThrows(CommandSyntaxException.class, () -> EntityArgument.players().parse(
            new StringReader("12345678-1234-1234-1234-123456789abc")));
    }

    @Test void everyGeneratedCommandPassesTheRealPlayerArgumentParser() {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        var root = LiteralArgumentBuilder.<Object>literal("cobbleventure_cheat");
        List<String> targets = new ArrayList<>();
        for (String action : CheatBatch.ACTIONS) {
            root.then(LiteralArgumentBuilder.<Object>literal(action)
                .then(RequiredArgumentBuilder.<Object, EntitySelector>argument("players", EntityArgument.players())
                    .executes(context -> {
                        targets.add(context.getInput());
                        return 1;
                    })));
        }
        dispatcher.register(root);
        for (String playerName : List.of("TestPlayer", "Other_Player")) {
            assertTrue(CheatBatch.failures(action -> dispatcher.execute(
                CheatBatch.command(action, playerName), new Object())).isEmpty());
        }
        assertEquals(18, targets.size());
        assertTrue(targets.subList(0, 9).stream().allMatch(command -> command.endsWith(" TestPlayer")));
        assertTrue(targets.subList(9, 18).stream().allMatch(command -> command.endsWith(" Other_Player")));
    }
}
