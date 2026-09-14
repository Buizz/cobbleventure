package dev.buizz.cobbleventure.playermenu;

import java.util.ArrayList;
import java.util.List;
import com.mojang.brigadier.arguments.StringArgumentType;

/** Runs the same individual cheats, including commands contributed by other modules. */
final class CheatBatch {
    static final List<String> ACTIONS = List.of(
        "badges", "hm", "menus", "destinations", "tm", "team", "money", "key_items", "items"
    );

    private CheatBatch() {}

    static String command(String action, String playerName) {
        return "cobbleventure_cheat " + action + " " + StringArgumentType.escapeIfRequired(playerName);
    }

    interface Action {
        int execute(String name) throws Exception;
    }

    static List<String> failures(Action action) {
        List<String> failed = new ArrayList<>();
        for (String name : ACTIONS) {
            try {
                if (action.execute(name) <= 0) failed.add(name);
            } catch (Exception error) {
                failed.add(name + " (" + error.getMessage() + ")");
            }
        }
        return List.copyOf(failed);
    }
}
