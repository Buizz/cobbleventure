package dev.buizz.cobbleventure.adventure.event;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import java.util.Objects;

/** Opens the research screen after the authored V5 dialogue completes. */
public final class ResearchEventCommandAdapter implements EventCommandAdapter {
    private final java.util.function.Consumer<EventSessionKey> gateway;
    private final EventCommandAdapter fallback;

    public ResearchEventCommandAdapter(
        java.util.function.Consumer<EventSessionKey> gateway, EventCommandAdapter fallback
    ) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
    }

    @Override
    public StartResult start(CommandContext context) {
        EventScript.Instruction instruction = context.instruction();
        if (!"command".equals(instruction.operation())
            || !"open_research".equals(instruction.command())) {
            return fallback.start(context);
        }
        if (instruction.awaitsResult()
            || instruction.resumeAddress() != null
            || instruction.resultVariable() != null
            || instruction.operationId() != null) {
            throw new EventRuntimeException(
                "open_research는 await, 결과 변수 또는 안정 ID를 사용하지 않습니다."
            );
        }
        JsonArray arguments = instruction.rawPayload().getAsJsonArray("arguments");
        JsonArray properties = instruction.rawPayload().getAsJsonArray("properties");
        if (arguments == null || !arguments.isEmpty()
            || properties == null || !properties.isEmpty()) {
            throw new EventRuntimeException("open_research는 인자와 속성을 받지 않습니다.");
        }
        gateway.accept(context.sessionKey());
        return new Completed(JsonNull.INSTANCE);
    }
}
