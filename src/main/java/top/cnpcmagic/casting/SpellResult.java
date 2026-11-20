package top.cnpcmagic.casting;

import java.util.UUID;

/** Stable Java bean getters are directly visible to CNPC's JavaScript engine. */
public record SpellResult(String code, String message, UUID instanceId, String state, double resourceSpent) {
    public boolean isSuccess() { return code.equals("SUCCESS"); }
    public String getCode() { return code; }
    public String getMessage() { return message; }
    public String getInstanceId() { return instanceId == null ? "" : instanceId.toString(); }
    public String getState() { return state; }
    public double getResourceSpent() { return resourceSpent; }
    public static SpellResult fail(String code, String message) { return new SpellResult(code, message, null, "FAILED", 0); }
}
