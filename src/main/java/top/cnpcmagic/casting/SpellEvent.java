package top.cnpcmagic.casting;

import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.Cancelable;

/** Server thread only. Request/Pre/Impact/Effect are cancellable; notifications are not. */
public class SpellEvent extends Event {
    public final CastingService.Context context;
    public final String phase;
    public final SpellResult result;
    public SpellEvent(CastingService.Context context, String phase, SpellResult result) {
        this.context = context; this.phase = phase; this.result = result;
    }
    @Cancelable public static final class Before extends SpellEvent {
        public Before(CastingService.Context context, String phase) { super(context, phase, null); }
    }
}
