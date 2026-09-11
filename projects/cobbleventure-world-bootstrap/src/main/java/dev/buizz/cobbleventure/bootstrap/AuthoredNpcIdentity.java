package dev.buizz.cobbleventure.bootstrap;

import java.util.Set;

/** Recognizes league NPCs imported before their presets included building identity. */
final class AuthoredNpcIdentity {
    private static final String LEAGUE_PREFIX = "cobbleventure_npc/cobbleventure/npc/league/";

    private AuthoredNpcIdentity() {}

    static boolean matches(Set<String> tags, String identity) {
        if (identity == null) return false;
        if (tags.contains(identity)) return true;
        // Generated league scripts and NPCs share this exact path. Do not infer
        // identities from arbitrary custom bindings or match another room's NPC.
        return identity.startsWith(LEAGUE_PREFIX)
            && tags.contains("cves_binding/cobbleventure/league/"
                + identity.substring(LEAGUE_PREFIX.length()));
    }
}
