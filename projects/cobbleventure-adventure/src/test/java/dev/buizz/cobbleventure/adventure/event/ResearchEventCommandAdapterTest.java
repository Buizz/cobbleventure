package dev.buizz.cobbleventure.adventure.event;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

final class ResearchEventCommandAdapterTest {
    @Test
    void opensTheNpcFromTheActiveV5SessionAndCompletesImmediately() {
        EventSessionKey key = new EventSessionKey(
            UUID.fromString("81000000-0000-0000-0000-000000000001"),
            UUID.fromString("81000000-0000-0000-0000-000000000002"),
            "cobbleventure:event_script/facilities/research_mega", "interact"
        );
        AtomicReference<EventSessionKey> opened = new AtomicReference<>();
        ResearchEventCommandAdapter adapter = new ResearchEventCommandAdapter(
            opened::set,
            context -> { throw new AssertionError("fallback"); }
        );

        EventCommandAdapter.StartResult result = adapter.start(
            new EventCommandAdapter.CommandContext(
                key, "9".repeat(64), instruction(), Map.of()
            )
        );

        assertInstanceOf(EventCommandAdapter.Completed.class, result);
        assertEquals(key, opened.get());
    }

    private static EventScript.Instruction instruction() {
        JsonObject payload = new JsonObject();
        payload.addProperty("command", "open_research");
        payload.add("arguments", new JsonArray());
        payload.add("properties", new JsonArray());
        payload.addProperty("await", false);
        payload.addProperty("await_explicit", false);
        payload.addProperty("next", 1);
        return new EventScript.Instruction(
            0, "page/0/open_research/0", "command", payload
        );
    }
}
