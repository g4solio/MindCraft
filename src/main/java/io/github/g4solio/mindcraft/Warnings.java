package io.github.g4solio.mindcraft;

import io.github.g4solio.mindcraft.MindRuntime.Entry.Kind;

import java.util.EnumSet;
import java.util.Set;

/**
 * The one-time warnings of a job (D41, decision 5). A kind of warning is a kind of diary entry other than a change.
 * Each kind has a condition. The warning comes one time while its condition holds: one diary entry of its kind, thus one notice (D30).
 * The end of the condition arms the warning again.
 * <p>
 * The long wait is the first kind. Its condition is a wait of LONG_WAIT ticks without an interruption.
 * MindRuntime.checkWait checks the condition. The end of the wait, in MindRuntime.set, ends it.
 * <p>
 * The record of the job saves the given warnings. Thus a save and a load, or a restart, give no second warning.
 */
final class Warnings {
    private final EnumSet<Kind> given = EnumSet.noneOf(Kind.class);

    /** The condition of the kind holds. True only the first time while it holds: then the warning comes. */
    boolean give(Kind kind) {
        return given.add(kind);
    }

    /** The condition of the kind ended. Its warning comes again the next time that the condition holds. */
    void ended(Kind kind) {
        given.remove(kind);
    }

    /** The warning of the kind came, and its condition did not end since. */
    boolean given(Kind kind) {
        return given.contains(kind);
    }

    /** The given warnings, for the save of the record. */
    Set<Kind> given() {
        return EnumSet.copyOf(given);
    }
}
