package io.github.g4solio.mindcraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.github.g4solio.mindcraft.MindRuntime.Actor;
import io.github.g4solio.mindcraft.MindRuntime.Job;
import io.github.g4solio.mindcraft.MindRuntime.Order;
import io.github.g4solio.mindcraft.MindRuntime.Refusal;
import io.github.g4solio.mindcraft.MindRuntime.State;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Uno, the central intelligence. In this increment Uno does three things.
 * It changes a written request into one action. A pause and a report run immediately. Each other action becomes a proposal.
 * It sends the request of a proposal to the runtime only when the player confirms. It reports on jobs with data from the runtime only.
 * <p>
 * Uno has no authority of its own. Each order goes through the ordinary validation of the runtime.
 * Uno speaks English to the player.
 */
// ponytail: English text in the code. Use translation keys when a second language is necessary.
public final class Uno {
    /**
     * Changes a written request into one action from a closed list (D29). An implementation can call a language model.
     * The context is JSON: the stations and robots of the player. The answer is the action as JSON, with its parameters.
     * A field that the action does not use is null or absent.
     * <ul>
     * <li>start, with the order: {"action": "start", "module": "keep_stock" or "transport", "robot": 0, "from": "...", "to": "...",
     * "item": "minecraft:coal", "quantity": 64, "reserve": 32}.</li>
     * <li>change, pause, resume, cancel, report, with the target: a robot number, a station name, or nothing.
     * {"action": "pause", "robot": 3, "station": null}. A change also has "quantity", "reserve" or both. Null is a value that does not change.</li>
     * <li>none, with one short reason for the player: {"action": "none", "reason": "the quantity is missing"}.</li>
     * </ul>
     * The answer also has the input tokens and the output tokens of the model. A provider without a model reports zero.
     * Each exception is a failure of the provider. Only the action "none" says that the request gives no action.
     */
    public interface Provider {
        Answer answer(String request, String context) throws Exception;

        /**
         * True if the provider calls a language model, thus each request can cost money. Then only an operator uses it, and
         * each actor has a limit of requests in one server session (D33). A provider with a model must say so here.
         */
        default boolean callsModel() {
            return false;
        }
    }

    /** The answer of a provider: the action as JSON, and the tokens that the answer used. */
    public record Answer(String json, long inputTokens, long outputTokens) {
    }

    /** The requests that Uno sent to a model for one actor in one server session, and the tokens of their answers. */
    record Usage(int requests, long inputTokens, long outputTokens) {
        static final Usage NONE = new Usage(0, 0, 0);

        Usage plus(Usage other) {
            return new Usage(requests + other.requests, inputTokens + other.inputTokens, outputTokens + other.outputTokens);
        }
    }

    /** Where a written request of an actor goes (D33). */
    enum Route {
        /** The provider calls no model. No counter, no limit, no operator rule. */
        NO_MODEL,
        /** An operator below the limit: the request goes to the model and counts. */
        MODEL,
        /** An operator at the limit: the fixed rules read the request. */
        AT_LIMIT,
        /** A player who is not an operator: the fixed rules read the request. */
        NOT_OPERATOR
    }

    /** The closed list of the actions of a provider (D29). */
    enum Action { START, CHANGE, PAUSE, RESUME, CANCEL, REPORT, NONE }

    /** The provider of the /uno command and of the screen. A different provider can replace it. See MindCraft.chooseProvider. */
    static volatile Provider provider = new RuleProvider();
    static final long TIMEOUT_MS = 20_000;
    /** The limit of the requests to a model for each actor in one server session (D33). An initial value, fixed in the code. */
    static final int MODEL_REQUESTS = 100;
    /** The fixed rules. They read the request of an actor who cannot use the model now. They have no limit. */
    private static final Provider RULES = new RuleProvider();
    private static final String LIMIT_REACHED = "You reached the limit of " + MODEL_REQUESTS + " model requests in this server session. "
        + "Until the server restarts, I read your requests with the fixed rules: they understand only simple sentences. ";
    private static final int SILENT_TICKS = 300;
    /** The limit of the reason of a "none" action. A provider writes the reason, and the screen has little room. */
    private static final int MAX_REASON_LENGTH = 200;
    private static final String INVALID = "I did not get a valid configuration. I created no proposal.";

    /**
     * A proposal that waits for a confirmation. The number tells it from the other proposals of the session.
     * The server writes its text. The confirmation sends the request of the proposal, and nothing else.
     */
    record Proposal(int number, String text, Request request) {
    }

    /**
     * What a confirmation asks the runtime: its ordinary request, with the values of the proposal. The runtime validates again.
     * A request about a job names the robot and the job ID. Thus a confirmation never reaches a different job.
     */
    sealed interface Request permits Start, Change, Control {
        /** Sends the request. A refusal of the runtime is a Refusal. The answer is the sentence of Uno. */
        String send(MindRuntime runtime, Actor actor);

        /** What the request does, for the sentence of a refusal: "I cannot (what): (reason)". */
        String what();
    }

    /** A new job. */
    record Start(Order order) implements Request {
        @Override
        public String send(MindRuntime runtime, Actor actor) {
            return "Job " + runtime.start(actor, order).id + " started on robot #" + order.robot() + ".";
        }

        @Override
        public String what() {
            return "start the job";
        }
    }

    /** New values of a job. The request also names the revision of the job: a confirmation never sends values about an older state. */
    record Change(int robot, int job, int revision, int quantity, int reserve) implements Request {
        @Override
        public String send(MindRuntime runtime, Actor actor) {
            runtime.update(actor, robot, job, revision, quantity, reserve);
            Job changed = runtime.jobs.get(robot);
            return "Job " + job + " on robot #" + robot + " changed. " + quantityName(changed) + ": " + quantity + ". "
                + reserveName(changed) + ": " + reserve + ". The robot uses the new values at its next step.";
        }

        @Override
        public String what() {
            return "change the job";
        }
    }

    /** A resume or a cancellation of a job: "resume" or "cancel". */
    record Control(String action, int robot, int job) implements Request {
        @Override
        public String send(MindRuntime runtime, Actor actor) {
            runtime.control(actor, robot, job, action);
            return action.equals("resume") ? "I resume job " + job + " on robot #" + robot + "."
                : "Job " + job + " on robot #" + robot + " is cancelled. The cargo stays on board the robot.";
        }

        @Override
        public String what() {
            return action + " the job";
        }
    }

    /**
     * What Uno keeps for the players during one server session. Nothing here is saved.
     * A proposal is not a decision: after a server restart Uno has no proposal, and says so.
     */
    private static final class Session {
        final Map<UUID, Proposal> proposals = new ConcurrentHashMap<>();
        /** The last thing that Uno said to each player on a screen. */
        final Map<UUID, String> said = new ConcurrentHashMap<>();
        /** The players with a request that has no answer yet. */
        final Set<UUID> open = ConcurrentHashMap.newKeySet();
        /** The requests to a model and their tokens, for each actor. The console is an actor too. */
        final Map<UUID, Usage> usage = new ConcurrentHashMap<>();
        /** The actors that Uno told that they reached the limit. Uno says it one time in a session. */
        final Set<UUID> toldLimit = ConcurrentHashMap.newKeySet();
        int proposalCount;
    }

    private static volatile Session session = new Session();

    private Uno() {
    }

    /**
     * Each server start calls this. See MindCraft.aboutToStart.
     * A world that opens later in the same Java process gets no message, no proposal and no open request of the world before it.
     * The counters of the model requests start again.
     */
    static void newSession() {
        session = new Session();
    }

    static void say(Actor actor, String text) {
        session.said.put(actor.id(), text);
    }

    static String said(Actor actor) {
        return session.said.getOrDefault(actor.id(), "Tell me what you need.");
    }

    /** The proposal that waits for the confirmation of the actor, or null. */
    static Proposal proposal(Actor actor) {
        return session.proposals.get(actor.id());
    }

    static boolean hasProposal(Actor actor) {
        return proposal(actor) != null;
    }

    /** The counter of the actor in this server session. */
    static Usage usage(Actor actor) {
        return session.usage.getOrDefault(actor.id(), Usage.NONE);
    }

    /** Where a written request of this actor to this provider goes now. */
    static Route route(Actor actor, Provider provider) {
        return route(session, actor, provider);
    }

    /** Only an operator uses a provider with a model, and only below the limit of the session. */
    private static Route route(Session mine, Actor actor, Provider provider) {
        if (!provider.callsModel()) return Route.NO_MODEL;
        if (!actor.admin()) return Route.NOT_OPERATOR;
        return mine.usage.getOrDefault(actor.id(), Usage.NONE).requests() < MODEL_REQUESTS ? Route.MODEL : Route.AT_LIMIT;
    }

    /** A request to the provider of the server, with the ordinary time limit. */
    public static void ask(MinecraftServer server, Actor actor, String request, Consumer<String> reply) {
        ask(server, actor, request, provider, TIMEOUT_MS, reply);
    }

    /**
     * The provider runs on a different thread. The server and the jobs in progress do not wait for it.
     * One request at a time for each player: a provider can cost money for each request.
     * A request that Uno accepts removes the proposal of the player. Thus a confirmation is always about the newest request.
     * <p>
     * A provider with a model serves only an operator, and only below the limit of the session (D33).
     * In each other case the fixed rules read the request.
     */
    public static void ask(MinecraftServer server, Actor actor, String request, Provider provider, long timeoutMs, Consumer<String> reply) {
        Session mine = session;
        String context = context(MindRuntime.get(server), actor);
        if (!mine.open.add(actor.id())) {
            reply.accept("I am still working on your previous request.");
            return;
        }
        mine.proposals.remove(actor.id());
        Route route = route(mine, actor, provider);
        Provider chosen = route == Route.MODEL || route == Route.NO_MODEL ? provider : RULES;
        // A request counts when Uno sends it. A failure and the time limit add no tokens.
        if (route == Route.MODEL) mine.usage.merge(actor.id(), new Usage(1, 0, 0), Usage::plus);
        // Uno says one time that the limit is reached. The screen tells a player who is not an operator why the fixed rules answer.
        String limit = route == Route.AT_LIMIT && mine.toldLimit.add(actor.id()) ? LIMIT_REACHED : "";
        CompletableFuture
            .supplyAsync(() -> {
                try {
                    return chosen.answer(request, context);
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            })
            .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .whenComplete((answer, error) -> server.execute(() -> {
                // The answer belongs to the session of its request. An answer for an earlier session changes nothing.
                // After a server stop, execute() runs this immediately on the thread of the provider. Then it must touch nothing.
                if (server.isStopped() || mine != session) return;
                mine.open.remove(actor.id());
                if (error == null && route == Route.MODEL) {
                    mine.usage.merge(actor.id(), new Usage(0, answer.inputTokens(), answer.outputTokens()), Usage::plus);
                }
                reply.accept(limit + (error == null ? act(mine, MindRuntime.get(server), actor, answer, server.overworld().getGameTime()) : failure(error)));
            }));
    }

    /** Only the data that the actor owns. A provider must not see the stations or robots of other players. */
    static String context(MindRuntime runtime, Actor actor) {
        JsonObject context = new JsonObject();
        JsonArray stations = new JsonArray();
        runtime.stations.forEach((name, station) -> {
            if (actor.owns(station.owner())) stations.add(name);
        });
        JsonArray robots = new JsonArray();
        runtime.robots.forEach((id, owner) -> {
            if (!actor.owns(owner)) return;
            Job job = runtime.jobs.get(id);
            JsonObject robot = new JsonObject();
            robot.addProperty("id", id);
            robot.addProperty("free", job == null || !job.state.open());
            robots.add(robot);
        });
        context.add("stations", stations);
        context.add("robots", robots);
        return context.toString();
    }

    /**
     * The sentence of Uno for the answer of a provider. Uno reads the answer and does not trust it: a provider can give anything.
     * "Pause" and "report" run immediately (D29). "Start", "change", "resume" and "cancel" become a proposal. "None" gives its reason.
     */
    private static String act(Session mine, MindRuntime runtime, Actor actor, Answer answer, long now) {
        JsonObject json;
        Action action;
        try {
            json = JsonParser.parseString(answer.json()).getAsJsonObject();
            // A model does not guarantee the case of a value of its schema.
            action = Action.valueOf(json.get("action").getAsString().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            return INVALID;
        }
        return switch (action) {
            case START -> start(mine, runtime, actor, json);
            case NONE -> none(json);
            case PAUSE -> pause(runtime, actor, json);
            case REPORT -> report(runtime, actor, json, now);
            case CHANGE -> change(mine, runtime, actor, json);
            case RESUME, CANCEL -> control(mine, runtime, actor, json, action);
        };
    }

    /**
     * The job that an action names: a robot number, a station name, or nothing (D29).
     * A null field, an absent field and an empty station are the same: the request does not give that value.
     */
    record Target(Integer robot, String station) {
        /** A value of a wrong type is a RuntimeException: the answer is not valid. */
        static Target of(JsonObject json) {
            JsonElement robot = json.get("robot"), station = json.get("station");
            String name = station == null || station.isJsonNull() ? "" : station.getAsString().trim();
            return new Target(robot == null || robot.isJsonNull() ? null : robot.getAsInt(), name.isEmpty() ? null : name);
        }

        boolean none() {
            return robot == null && station == null;
        }
    }

    /** The result of the search of a job: the robot and its job, or the sentence that says why Uno did not find one. */
    record Found(int robot, Job job, String problem) {
    }

    /**
     * Finds the job of a target on the server, only among the jobs that the actor owns. The rule of D29.2:
     * <ul>
     * <li>a robot number names the job that the robot has now, open or not;</li>
     * <li>a station name names the one open job that uses the station as source or as destination;</li>
     * <li>no target names the one open job of the actor.</li>
     * </ul>
     * In each other case the problem says how many jobs fit and asks for the robot number. Uno never chooses for the player.
     * A job of a different player gets the same sentence as a job that does not exist.
     */
    static Found find(MindRuntime runtime, Actor actor, Target target) {
        String ask = " Tell me the robot number of the job. I did nothing.";
        if (target.robot() != null) {
            Job job = runtime.jobs.get(target.robot());
            if (job != null && actor.owns(job.owner)) return new Found(target.robot(), job, null);
            return new Found(MindRuntime.UNKNOWN, null, "You have no job on robot #" + target.robot() + "." + ask);
        }
        String station = target.station();
        List<Integer> fit = new ArrayList<>();
        runtime.jobs.forEach((robotId, job) -> {
            if (actor.owns(job.owner) && job.state.open()
                && (station == null || job.from.equalsIgnoreCase(station) || job.to.equalsIgnoreCase(station))) fit.add(robotId);
        });
        if (fit.size() == 1) return new Found(fit.get(0), runtime.jobs.get(fit.get(0)), null);
        String count = station == null
            ? (fit.isEmpty() ? "You have no open job." : "You have " + fit.size() + " open jobs.")
            : (fit.isEmpty() ? "None of your open jobs uses the station '" + station + "'."
                : fit.size() + " of your open jobs use the station '" + station + "'.");
        return new Found(MindRuntime.UNKNOWN, null, count + ask);
    }

    /**
     * A pause runs immediately, without a proposal: it only stops the robot (D29).
     * It is the ordinary request of the runtime, with the job ID. A refusal of the runtime becomes the sentence of Uno.
     */
    private static String pause(MindRuntime runtime, Actor actor, JsonObject json) {
        Target target;
        try {
            target = Target.of(json);
        } catch (RuntimeException e) {
            return INVALID;
        }
        Found found = find(runtime, actor, target);
        if (found.problem() != null) return found.problem();
        try {
            runtime.control(actor, found.robot(), found.job().id, "pause");
        } catch (Refusal refusal) {
            return "I cannot pause the job: " + refusal.getMessage();
        }
        return "Job " + found.job().id + " on robot #" + found.robot() + " is paused. The cargo stays on board.";
    }

    /** A report runs immediately. Without a target it is the report on all jobs of the actor, as the "Report" button gives it. */
    private static String report(MindRuntime runtime, Actor actor, JsonObject json, long now) {
        Target target;
        try {
            target = Target.of(json);
        } catch (RuntimeException e) {
            return INVALID;
        }
        if (target.none()) return report(runtime, actor, now);
        Found found = find(runtime, actor, target);
        return found.problem() != null ? found.problem() : report(found.robot(), found.job(), now);
    }

    /**
     * A change becomes a proposal (D29). It holds the robot, the job ID and the revision, as the "Apply" button does (D19).
     * A value that the request does not give stays as it is. The runtime checks the change now, and again at the confirmation.
     */
    private static String change(Session mine, MindRuntime runtime, Actor actor, JsonObject json) {
        Target target;
        Integer quantity, reserve;
        try {
            target = Target.of(json);
            quantity = integer(json, "quantity");
            reserve = integer(json, "reserve");
        } catch (RuntimeException e) {
            return INVALID;
        }
        if (quantity == null && reserve == null) {
            return "I cannot propose a change: the request gives no new quantity and no new reserve. I created no proposal.";
        }
        Found found = find(runtime, actor, target);
        if (found.problem() != null) return found.problem();
        Job job = found.job();
        Change change = new Change(found.robot(), job.id, job.revision, quantity == null ? job.quantity : quantity, reserve == null ? job.reserve : reserve);
        try {
            runtime.checkUpdate(actor, change.robot(), change.job(), change.revision(), change.quantity(), change.reserve());
        } catch (Refusal refusal) {
            return "I cannot propose this change: " + refusal.getMessage();
        }
        return propose(mine, actor, "change " + name(found.robot(), job) + ". " + value(quantityName(job), job.quantity, change.quantity())
            + ". " + value(reserveName(job), job.reserve, change.reserve()), change);
    }

    /**
     * A resume or a cancellation becomes a proposal (D29). It holds the robot and the job ID, as the buttons of the screen do (D19).
     * The runtime checks the request now, and again at the confirmation.
     */
    private static String control(Session mine, MindRuntime runtime, Actor actor, JsonObject json, Action action) {
        Target target;
        try {
            target = Target.of(json);
        } catch (RuntimeException e) {
            return INVALID;
        }
        Found found = find(runtime, actor, target);
        if (found.problem() != null) return found.problem();
        Job job = found.job();
        String verb = action.name().toLowerCase(Locale.ROOT);
        try {
            runtime.checkControl(actor, found.robot(), job.id, verb);
        } catch (Refusal refusal) {
            return "I cannot propose to " + verb + " the job: " + refusal.getMessage();
        }
        String text = verb + " " + name(found.robot(), job);
        if (action == Action.CANCEL) {
            text += ". The cargo stays on board the robot";
        } else if (job.state == State.RECOVERY) {
            // The confirmation accepts the load in the robot as the truth. The player must know this before.
            text += ". The job needs a check: the runtime accepts the load that it observes in the robot, and the totals can be inexact";
        }
        return propose(mine, actor, text, new Control(verb, found.robot(), job.id));
    }

    /** A whole number of an answer, or null if the answer does not give it. A value of a wrong type is a RuntimeException. */
    private static Integer integer(JsonObject json, String field) {
        JsonElement value = json.get(field);
        return value == null || value.isJsonNull() ? null : value.getAsInt();
    }

    /** A job in a proposal: its ID, its robot and what it does. */
    private static String name(int robot, Job job) {
        String item = MindRuntime.itemName(job.item), from = "'" + job.from + "'", to = "'" + job.to + "'";
        return "job " + job.id + " on robot #" + robot + " ("
            + (job.keepStock() ? "keep " + item + " in " + to + ", taken from " + from : "carry " + item + " from " + from + " to " + to) + ")";
    }

    /** The names of the two values of a job, as the screen gives them. */
    private static String quantityName(Job job) {
        return job.keepStock() ? "Level to keep in '" + job.to + "'" : "Quantity to carry";
    }

    private static String reserveName(Job job) {
        return "Reserve to leave in '" + job.from + "'";
    }

    /** One value of a change: the old and the new value, or the value that stays. */
    private static String value(String name, int old, int now) {
        return name + ": " + (old == now ? old + ", unchanged" : "from " + old + " to " + now);
    }

    /** A request without an action is not a failure of the provider. The player must see the difference. */
    private static String none(JsonObject json) {
        String reason;
        try {
            reason = json.get("reason").getAsString().trim();
        } catch (RuntimeException e) {
            return INVALID;
        }
        if (reason.isEmpty()) return INVALID;
        reason = reason.substring(0, Math.min(reason.length(), MAX_REASON_LENGTH));
        return "I cannot propose a job: " + reason + (reason.endsWith(".") ? "" : ".");
    }

    /** A start becomes a proposal, after the ordinary validation of the order. */
    private static String start(Session mine, MindRuntime runtime, Actor actor, JsonObject o) {
        Order order;
        try {
            JsonElement reserve = o.get("reserve");
            order = new Order(o.get("module").getAsString(), o.get("robot").getAsInt(), o.get("from").getAsString(),
                o.get("to").getAsString(), ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(o.get("item").getAsString())),
                o.get("quantity").getAsInt(), reserve == null || reserve.isJsonNull() ? 0 : reserve.getAsInt());
        } catch (RuntimeException e) {
            return INVALID;
        }
        try {
            runtime.check(actor, order);
        } catch (Refusal refusal) {
            return "I cannot propose this job: " + refusal.getMessage();
        }
        return propose(mine, actor, describe(order), new Start(order));
    }

    /** The proposal of the actor: one for each player, the newest (D23). The server writes its text. The screen shows the same text. */
    private static String propose(Session mine, Actor actor, String text, Request request) {
        mine.proposals.put(actor.id(), new Proposal(++mine.proposalCount, text, request));
        return "Proposal: " + text + ". I have done nothing yet. Confirm with /uno confirm.";
    }

    /** An order in words. */
    static String describe(Order order) {
        String item = MindRuntime.itemName(order.item());
        String what = order.module().equals(MindRuntime.KEEP_STOCK)
            ? "keep at least " + order.quantity() + " x " + item + " in '" + order.to() + "', taken from '" + order.from() + "'"
            : "carry " + order.quantity() + " x " + item + " from '" + order.from() + "' to '" + order.to() + "'";
        return what + ". Reserve to leave in '" + order.from() + "': " + order.reserve() + ". Robot #" + order.robot();
    }

    /** The /uno confirm command: starts the proposal that the player has now. */
    public static String confirm(MinecraftServer server, Actor actor) {
        Proposal proposal = proposal(actor);
        return confirm(server, actor, proposal == null ? MindRuntime.UNKNOWN : proposal.number());
    }

    /**
     * Confirms the proposal with this number. A screen sends the number of the proposal that it showed.
     * Thus a button of an older proposal starts nothing. The runtime validates the request again: the world can be different now.
     */
    static String confirm(MinecraftServer server, Actor actor, int number) {
        Session mine = session;
        if (mine.open.contains(actor.id())) return "I am still working on your request. I have no proposal to confirm yet.";
        Proposal proposal = mine.proposals.get(actor.id());
        if (proposal == null) {
            return "I have no pending proposal. A new request replaces the proposal before it. A proposal that is not confirmed is not saved.";
        }
        if (proposal.number() != number) return "That is not my current proposal. I started nothing. Read the proposal again.";
        mine.proposals.remove(actor.id());
        Request request = proposal.request();
        try {
            return request.send(MindRuntime.get(server), actor);
        } catch (Refusal refusal) {
            return "I cannot " + request.what() + ": " + refusal.getMessage();
        }
    }

    public static String report(MinecraftServer server, Actor actor) {
        return report(MindRuntime.get(server), actor, server.overworld().getGameTime());
    }

    /** A report on the jobs of the actor. Each number comes from the record of the runtime. */
    static String report(MindRuntime runtime, Actor actor, long now) {
        List<String> lines = new ArrayList<>();
        runtime.jobs.forEach((robotId, job) -> {
            if (actor.owns(job.owner)) lines.add(report(robotId, job, now));
        });
        return lines.isEmpty() ? "I have no record of a job of yours." : String.join("\n", lines);
    }

    private static String report(int robotId, Job job, long now) {
        String from = "'" + job.from + "'", to = "'" + job.to + "'";
        StringBuilder text = new StringBuilder("Job " + job.id + ", robot #" + robotId + ": ");
        text.append(job.keepStock()
            ? "keep at least " + job.quantity + " items in " + to + ", reserve " + job.reserve + " in " + from + ". "
            : "carry " + job.quantity + " items from " + from + " to " + to + ", reserve " + job.reserve + ". ");
        text.append("I delivered ").append(job.delivered).append(" items. ");
        // The owner can lower the quantity below the deliveries. The record keeps the deliveries: the report must not hide the difference.
        if (!job.keepStock() && job.delivered > job.quantity) text.append("That is more than the quantity of the job: the quantity became lower after those deliveries. ");
        if (job.carried > 0) text.append("The record says that I have ").append(job.carried).append(" on board. ");
        boolean live = RobotApi.live(robotId) != null;
        text.append(switch (job.state) {
            case RUNNING -> live ? "I am working. " : "The job is open, but the robot is stopped. ";
            case SATISFIED -> "At the last observation the stock was at the requested level. "
                + (job.carried > 0 ? "I keep the load on board for the next need. " : "");
            case DONE -> "The job is complete. ";
            case PAUSED -> "I am paused at your request. ";
            case CANCELLED -> "You cancelled the job. ";
            case WAITING -> waiting(job, from, to);
            case BLOCKED -> blocked(job, from, to);
            case RECOVERY -> "I cannot say how it went. Recorded reason: " + job.reason + ". You decide whether to resume or cancel. ";
        });
        if (job.recoveries > 0) text.append("After a recovery the totals can be inexact. ");
        text.append("Last observations: ").append(from).append(' ').append(seen(job.fromSeen, job.fromSeenAt, now))
            .append(", ").append(to).append(' ').append(seen(job.toSeen, job.toSeenAt, now)).append(". ");
        // The state of the link is not the state of the job. Old data must not look like current data.
        if (job.state.open() && job.state != State.PAUSED) {
            if (!live) {
                text.append("The robot is not running or not loaded. These are the last recorded data, not the current state.");
            } else if (job.lastContact == MindRuntime.UNKNOWN || now - job.lastContact > SILENT_TICKS) {
                text.append("The robot has not contacted me for a while. I do not know if it is working.");
            }
        }
        return text.toString().trim();
    }

    /** Why the job waits. The sentence comes from the cause that the runtime recorded, not from a guess. */
    private static String waiting(Job job, String from, String to) {
        String missing = job.keepStock() ? "To reach " + job.quantity + " in " + to + " I need more materials. "
            : "I must carry " + (job.quantity - job.delivered) + " more. I need more materials. ";
        return switch (job.cause) {
            case SOURCE_AT_RESERVE -> (job.reserve == 0 ? "Nothing remains in " + from + ". "
                : job.fromSeen + " remain in " + from + (job.fromSeen == job.reserve ? ": that is the reserve you asked me to keep. "
                    : ": that is less than the reserve of " + job.reserve + " you asked me to keep. ")) + missing;
            case SOURCE_KEEPS -> from + " has the items, but it does not let me take them. ";
            case ROBOT_FULL -> "My inventory is full: I cannot take the items from " + from + ". Make room in the robot. ";
            case DESTINATION_FULL -> "The destination is full. I am waiting for free space. ";
            case SOURCE_UNLOADED -> "The area of " + from + " is not loaded: I cannot see its inventory and I do not know its stock. ";
            case DESTINATION_UNLOADED -> "The area of " + to + " is not loaded: I cannot see its inventory and I do not know its stock. ";
            case PATH_OBSTRUCTED -> "My path is obstructed. I try to move again every few seconds, and I continue by myself when the path is free. ";
            // A record of an older version has no cause. The recorded text is all that Uno knows.
            default -> "I am waiting. Recorded reason: " + job.reason + ". ";
        };
    }

    /** Why the job is blocked. A block needs the hands of the owner. The sentence comes from the cause that the runtime recorded. */
    private static String blocked(Job job, String from, String to) {
        return switch (job.cause) {
            case OUT_OF_FUEL -> "I am stopped: I have no fuel. Give me fuel, then resume the job. ";
            case SOURCE_NO_INVENTORY -> "I am stopped: I find no inventory at " + from + ". Put it back, then resume the job. ";
            case DESTINATION_NO_INVENTORY -> "I am stopped: I find no inventory at " + to + ". Put it back, then resume the job. ";
            case LOAD_ABOVE_QUANTITY -> "I am stopped: the quantity of the job is reached, and " + job.carried
                + " stay on board. Raise the quantity and resume, or cancel the job. ";
            // A record of an older version, or an impediment of a kind that the runtime does not know. The recorded text is all that Uno knows.
            default -> "I am stopped and I need your help. Recorded reason: " + job.reason + ". ";
        };
    }

    static String seen(int count, long at, long now) {
        return count == MindRuntime.UNKNOWN ? "never observed" : count + " items, " + (now - at) / 20 + " seconds ago";
    }

    /** A failure of the provider: an exception or the time limit. It is not an answer, thus each exception gets this sentence. */
    private static String failure(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        String reason = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        return "I cannot interpret the request now (" + reason + "). The jobs in progress continue.";
    }

    /**
     * A provider without a language model. It gives the same answer for the same request, thus it also serves the checks.
     * A request that names two stations of the player is a start: the rules read the kind of job, two numbers, an item and the
     * two stations. With fewer stations, a word of an action decides, and the rules read the job: "robot N" or the station.
     */
    // ponytail: a few fixed word patterns in Italian and English. A language model replaces this for free text.
    static final class RuleProvider implements Provider {
        private static final Pattern RESERVE = Pattern.compile("(?:lascia|riserva|leave|reserve)\\D*(\\d+)");
        private static final Pattern QUANTITY = Pattern.compile("(?:quantity|level|quantità|livello)\\D*(\\d+)");
        private static final Pattern NUMBER = Pattern.compile("\\b\\d+\\b");
        private static final Pattern ROBOT = Pattern.compile("robot\\D{0,2}(\\d+)");
        private static final Pattern SOURCE_WORD = Pattern.compile("\\b(?:da|dal|dalla|dallo|from)\\s+(?:il\\s+|la\\s+|the\\s+)?$");
        private static final Map<String, String> ITEM_WORDS = Map.of("carbone", "minecraft:coal");
        /**
         * The words of the actions that name a job, in English and in Italian. The first action that fits decides.
         * A resume comes before a pause: "riprendi il lavoro in pausa" is a resume.
         * A change needs a new value: a number after a word of the quantity or of the reserve.
         */
        private static final List<Map.Entry<Action, Pattern>> ACTION_WORDS = List.of(
            Map.entry(Action.RESUME, Pattern.compile("\\b(?:resume|continue|riprendi|continua)\\b")),
            Map.entry(Action.CANCEL, Pattern.compile("\\b(?:cancel|annulla|cancella)\\b")),
            Map.entry(Action.PAUSE, Pattern.compile("\\b(?:pause|pausa|sospendi)\\b")),
            Map.entry(Action.CHANGE, Pattern.compile(QUANTITY.pattern() + "|" + RESERVE.pattern())),
            Map.entry(Action.REPORT, Pattern.compile("\\b(?:report|status|rapporto|stato|how(?:'s|’s| is| are)|come (?:va|vanno|sta|stanno))\\b")));

        /**
         * The action "start", an action that names a job, or "none" with the reason.
         * The fixed rules call no model, thus they report zero tokens.
         */
        @Override
        public Answer answer(String request, String context) {
            JsonObject known = JsonParser.parseString(context).getAsJsonObject();
            String text = request.toLowerCase(Locale.ROOT);
            List<String> stations = new ArrayList<>();
            for (JsonElement element : known.getAsJsonArray("stations")) {
                if (mention(text, element.getAsString()).find()) stations.add(element.getAsString());
            }
            JsonObject action;
            try {
                action = stations.size() < 2 ? target(text, stations) : null;
                if (action == null) action = start(text, known);
            } catch (IllegalArgumentException missing) {
                action = new JsonObject();
                action.addProperty("action", "none");
                action.addProperty("reason", missing.getMessage());
            }
            return new Answer(action.toString(), 0, 0);
        }

        /** The name of a station as a whole word of the text. */
        private static Matcher mention(String text, String station) {
            return Pattern.compile("\\b" + Pattern.quote(station.toLowerCase(Locale.ROOT)) + "\\b").matcher(text);
        }

        /**
         * The action of the first word that fits, with "robot N" and the one station of the text. Null if no word fits.
         * A change also has the new quantity and the new reserve. A value that the text does not give is null.
         */
        private static JsonObject target(String text, List<String> stations) {
            // "robot N" names the job. Its number is not a new value.
            Matcher robot = ROBOT.matcher(text);
            JsonElement robotId = robot.find() ? new JsonPrimitive(Integer.parseInt(robot.group(1))) : JsonNull.INSTANCE;
            String rest = robot.reset().replaceFirst(" ");
            for (Map.Entry<Action, Pattern> word : ACTION_WORDS) {
                if (!word.getValue().matcher(rest).find()) continue;
                JsonObject action = new JsonObject();
                action.addProperty("action", word.getKey().name().toLowerCase(Locale.ROOT));
                action.add("robot", robotId);
                action.add("station", stations.isEmpty() ? JsonNull.INSTANCE : new JsonPrimitive(stations.get(0)));
                if (word.getKey() == Action.CHANGE) {
                    action.add("quantity", number(QUANTITY, rest));
                    action.add("reserve", number(RESERVE, rest));
                }
                return action;
            }
            return null;
        }

        /** The first number after the word of the pattern, or null. */
        private static JsonElement number(Pattern word, String text) {
            Matcher matcher = word.matcher(text);
            return matcher.find() ? new JsonPrimitive(Integer.parseInt(matcher.group(1))) : JsonNull.INSTANCE;
        }

        /** The action "start" with the order of the request. An IllegalArgumentException says what is missing. */
        private static JsonObject start(String text, JsonObject known) {
            Matcher reserveMatch = RESERVE.matcher(text);
            int reserve = reserveMatch.find() ? Integer.parseInt(reserveMatch.group(1)) : 0;
            String rest = reserveMatch.reset().replaceFirst(" ");
            Matcher robotMatch = ROBOT.matcher(rest);
            Integer robot = robotMatch.find() ? Integer.valueOf(robotMatch.group(1)) : null;
            Matcher quantityMatch = NUMBER.matcher(robotMatch.reset().replaceFirst(" "));
            if (!quantityMatch.find()) throw new IllegalArgumentException("the quantity is missing");
            int quantity = Integer.parseInt(quantityMatch.group());

            String from = null, to = null;
            for (JsonElement element : known.getAsJsonArray("stations")) {
                String name = element.getAsString();
                Matcher mention = mention(text, name);
                boolean mentioned = false, source = false;
                while (mention.find()) {
                    mentioned = true;
                    source |= SOURCE_WORD.matcher(text.substring(0, mention.start())).find();
                }
                if (!mentioned) continue;
                if (source && from == null) from = name;
                else if (!source && to == null) to = name;
                else throw new IllegalArgumentException("I cannot tell the source from the destination");
            }
            if (from == null || to == null) {
                throw new IllegalArgumentException("I need two of your stations: the destination, and the source after 'from'");
            }

            String item = null;
            for (String word : text.split("[^a-z0-9_:]+")) {
                String id = ITEM_WORDS.getOrDefault(word, word.contains(":") ? word : "minecraft:" + word);
                ResourceLocation key = ResourceLocation.tryParse(id);
                if (!word.isEmpty() && !word.equals(from.toLowerCase(Locale.ROOT)) && !word.equals(to.toLowerCase(Locale.ROOT))
                    && key != null && ForgeRegistries.ITEMS.containsKey(key)) {
                    item = id;
                    break;
                }
            }
            if (item == null) throw new IllegalArgumentException("I do not know the item");

            if (robot == null) {
                List<Integer> free = new ArrayList<>();
                for (JsonElement element : known.getAsJsonArray("robots")) {
                    if (element.getAsJsonObject().get("free").getAsBoolean()) free.add(element.getAsJsonObject().get("id").getAsInt());
                }
                if (free.size() != 1) throw new IllegalArgumentException("you have " + free.size() + " free robots: write 'robot N'");
                robot = free.get(0);
            }

            JsonObject order = new JsonObject();
            order.addProperty("action", "start");
            order.addProperty("module", text.matches("(?s).*\\b(mantieni|mantenere|almeno|keep)\\b.*") ? MindRuntime.KEEP_STOCK : MindRuntime.TRANSPORT);
            order.addProperty("robot", robot);
            order.addProperty("from", from);
            order.addProperty("to", to);
            order.addProperty("item", item);
            order.addProperty("quantity", quantity);
            order.addProperty("reserve", reserve);
            return order;
        }
    }
}
