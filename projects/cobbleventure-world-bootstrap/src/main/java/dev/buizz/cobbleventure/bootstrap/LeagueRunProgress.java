package dev.buizz.cobbleventure.bootstrap;

/** Progress within a single challenge; permanent trainer-card achievements are independent. */
record LeagueRunProgress(int completed, int stages, boolean relay) {
    LeagueRunProgress {
        if (stages < 1 || completed < 0 || completed > stages) {
            throw new IllegalArgumentException("Invalid league progress");
        }
    }

    boolean canBattle(int stage) { return stage == completed && stage < stages; }

    boolean cleared() { return completed == stages; }

    boolean redirectsLobbyToHall(boolean returningFromLeagueRoom) {
        return cleared() && !returningFromLeagueRoom;
    }

    LeagueRunProgress win(int stage) {
        return canBattle(stage) ? new LeagueRunProgress(completed + 1, stages, relay) : this;
    }

    LeagueRunProgress interrupt() {
        return new LeagueRunProgress(relay && !cleared() ? 0 : completed, stages, relay);
    }
}
