package io.github.g4solio.mindcraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.g4solio.mindcraft.MindRuntime.Actor;
import io.github.g4solio.mindcraft.MindRuntime.Job;
import io.github.g4solio.mindcraft.MindRuntime.Refusal;
import io.github.g4solio.mindcraft.MindRuntime.State;
import net.minecraft.server.MinecraftServer;

import java.util.Map;

/**
 * The screen of Uno, as data. The server builds a description of the screen for one player: a list of widgets.
 * The client only draws the widgets and sends back the actions. All checks are on the server.
 * The server keeps no state for an open screen. Thus a closed screen changes nothing.
 * <p>
 * Widget types: "text", "number" (a value that the player can change), "bar", "buttons", "input".
 * The tablet shows this view. A projector can show the same view later.
 */
final class UnoView {
    private static final Map<State, String> STATES = Map.of(
        State.RUNNING, "in progress", State.WAITING, "waiting", State.SATISFIED, "stock at level", State.BLOCKED, "blocked",
        State.PAUSED, "paused", State.RECOVERY, "needs a check", State.DONE, "complete", State.CANCELLED, "cancelled");

    private UnoView() {
    }

    /** Only the jobs that the actor owns. */
    static String build(MindRuntime runtime, Actor actor, long now) {
        JsonArray widgets = new JsonArray();
        runtime.jobs.forEach((robotId, job) -> {
            if (actor.owns(job.owner)) job(widgets, robotId, job, now);
        });
        if (widgets.isEmpty()) widgets.add(text("I have no record of a job of yours.", "muted"));
        long places = runtime.places.stream().filter(place -> actor.id().equals(place.owner())).count();
        if (places > 0) widgets.add(text("Places of the base with a projector: " + places, "muted"));
        widgets.add(text("Uno › " + Uno.said(actor), "uno"));
        // The player must know that the text of a request leaves the server.
        // ponytail: the view knows the Claude provider. Put this notice in the provider interface when a second real provider exists.
        if (Uno.provider instanceof ClaudeProvider) widgets.add(text("Written requests go to Claude, a model of Anthropic.", "muted"));

        JsonObject input = new JsonObject();
        input.addProperty("type", "input");
        input.addProperty("id", "request");
        input.addProperty("hint", "Write to Uno");
        widgets.add(input);

        JsonArray buttons = new JsonArray();
        buttons.add(button("ask", "Send"));
        if (Uno.hasProposal(actor)) buttons.add(button("confirm", "Confirm"));
        buttons.add(button("report", "Report"));
        widgets.add(row(buttons));

        JsonObject view = new JsonObject();
        view.addProperty("title", "Uno");
        view.add("widgets", widgets);
        return view.toString();
    }

    private static void job(JsonArray widgets, int robotId, Job job, long now) {
        String from = "'" + job.from + "'", to = "'" + job.to + "'";
        widgets.add(text("Job " + job.id + " · robot #" + robotId + " · " + STATES.get(job.state), "heading"));
        String what = job.quantity + " x " + MindRuntime.itemName(job.item);
        widgets.add(text(job.keepStock() ? "Keep at least " + what + " in " + to + ", taken from " + from
            : "Carry " + what + " from " + from + " to " + to, "normal"));
        if (job.state == State.BLOCKED || job.state == State.RECOVERY) widgets.add(text("Recorded reason: " + job.reason, "muted"));
        if (job.state.open()) {
            widgets.add(number("quantity:" + robotId, job.keepStock() ? "Level to keep" : "Quantity to carry", job.quantity));
            widgets.add(number("reserve:" + robotId, "Reserve in the source", job.reserve));
        }
        if (!job.keepStock()) {
            widgets.add(bar("delivered", job.delivered, job.quantity, ""));
        } else if (job.toSeen != MindRuntime.UNKNOWN) {
            widgets.add(bar(job.to, job.toSeen, job.quantity, "seen " + (now - job.toSeenAt) / 20 + " s ago"));
        }
        widgets.add(text(from + ": " + Uno.seen(job.fromSeen, job.fromSeenAt, now) + " · delivered " + job.delivered
            + " · on board " + job.carried, "muted"));
        if (!job.state.open()) return;
        // Old data must not look like current data.
        if (RobotApi.live(robotId) == null) widgets.add(text("The robot is not running. These data are not the current state.", "muted"));
        JsonArray buttons = new JsonArray();
        // The revision goes with the action. The runtime refuses a change that comes from an old view.
        buttons.add(button("apply:" + robotId + ":" + job.revision, "Apply"));
        buttons.add(job.state.active() ? button("pause:" + robotId, "Pause") : button("resume:" + robotId, "Resume"));
        buttons.add(button("cancel:" + robotId, "Cancel"));
        widgets.add(row(buttons));
    }

    /**
     * Runs one action of a screen. The values are the text of the fields, as JSON.
     * The runtime checks the actor in each request. Uno says the result in the next view.
     */
    static void act(MinecraftServer server, Actor actor, String action, String valuesJson) {
        MindRuntime runtime = MindRuntime.get(server);
        try {
            JsonObject values = JsonParser.parseString(valuesJson).getAsJsonObject();
            String[] part = action.split(":");
            switch (part[0]) {
                case "refresh" -> {
                }
                case "report" -> Uno.say(actor, Uno.report(server, actor));
                case "confirm" -> Uno.say(actor, Uno.confirm(server, actor));
                case "ask" -> {
                    String request = values.get("request").getAsString().trim();
                    if (request.isEmpty()) {
                        Uno.say(actor, "Write a request first.");
                    } else {
                        Uno.say(actor, "Let me think.");
                        Uno.ask(server, actor, request, answer -> Uno.say(actor, answer));
                    }
                }
                case "pause", "resume", "cancel" -> {
                    runtime.control(actor, Integer.parseInt(part[1]), part[0]);
                    Uno.say(actor, switch (part[0]) {
                        case "pause" -> "Job paused. The cargo stays on board.";
                        case "resume" -> "I resume the job.";
                        default -> "Job cancelled. The cargo stays on board the robot.";
                    });
                }
                case "apply" -> {
                    int robotId = Integer.parseInt(part[1]);
                    runtime.update(actor, robotId, Integer.parseInt(part[2]),
                        values.get("quantity:" + robotId).getAsInt(), values.get("reserve:" + robotId).getAsInt());
                    Uno.say(actor, "I updated the quantity and the reserve.");
                }
                default -> throw new IllegalArgumentException(action);
            }
        } catch (Refusal refusal) {
            Uno.say(actor, "I cannot: " + refusal.getMessage());
        } catch (RuntimeException e) {
            Uno.say(actor, "I did not understand the action. Check the values and try again.");
        }
    }

    private static JsonObject text(String text, String style) {
        JsonObject widget = new JsonObject();
        widget.addProperty("type", "text");
        widget.addProperty("text", text);
        widget.addProperty("style", style);
        return widget;
    }

    private static JsonObject number(String id, String label, int value) {
        JsonObject widget = new JsonObject();
        widget.addProperty("type", "number");
        widget.addProperty("id", id);
        widget.addProperty("label", label);
        widget.addProperty("value", value);
        return widget;
    }

    private static JsonObject bar(String label, int value, int max, String note) {
        JsonObject widget = new JsonObject();
        widget.addProperty("type", "bar");
        widget.addProperty("label", label);
        widget.addProperty("value", value);
        widget.addProperty("max", max);
        widget.addProperty("note", note);
        return widget;
    }

    private static JsonObject button(String action, String label) {
        JsonObject button = new JsonObject();
        button.addProperty("action", action);
        button.addProperty("label", label);
        return button;
    }

    private static JsonObject row(JsonArray buttons) {
        JsonObject widget = new JsonObject();
        widget.addProperty("type", "buttons");
        widget.add("buttons", buttons);
        return widget;
    }
}
