package top.cnpcmagic.verify;

import noppes.npcs.controllers.ScriptController;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.io.StringWriter;
import java.util.Map;

/** Verification-only adapter to the scripting engine actually supplied by CNPC 1.21.1. */
final class CnpcScriptExecutor implements AutoCloseable {
    private ScriptEngine engine;
    private String code;
    private boolean evaluated, errored;
    void initialize(String language, Map<String, Object> bindings) {
        engine = ScriptController.Instance.getEngineByName(language);
        if (engine == null) throw new IllegalStateException("CNPC script engine unavailable: " + language);
        bindings.forEach(engine::put);
    }
    void setScript(String script) { code = script; evaluated = false; errored = false; }
    String run(String function, Object event) {
        StringWriter output = new StringWriter();
        engine.getContext().setWriter(output); engine.getContext().setErrorWriter(output);
        try {
            if (!evaluated) { engine.eval(code); evaluated = true; }
            ((Invocable) engine).invokeFunction(function, event);
        } catch (ScriptException | NoSuchMethodException error) {
            errored = true; output.append(error.toString());
        }
        return output.toString();
    }
    boolean isErrored() { return errored; }
    @Override public void close() { engine = null; }
}
