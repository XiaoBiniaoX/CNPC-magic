package top.cnpcmagic.casting;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** Server thread only. Request/Pre/Impact/Effect are cancellable; notifications are not. */
public class SpellEvent extends Event {
    public final CastingService.Context context;
    public final String phase;
    public final SpellResult result;
    public SpellEvent(CastingService.Context context, String phase, SpellResult result) {
        this.context = context; this.phase = phase; this.result = result;
    }
    public static final class Before extends SpellEvent implements ICancellableEvent {
        public Before(CastingService.Context context, String phase) { super(context, phase, null); }
    }
}
