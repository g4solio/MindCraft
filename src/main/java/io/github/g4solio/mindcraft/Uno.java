package io.github.g4solio.mindcraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.github.g4solio.mindcraft.MindRuntime.Actor;
import io.github.g4solio.mindcraft.MindRuntime.Cause;
import io.github.g4solio.mindcraft.MindRuntime.Entry;
import io.github.g4solio.mindcraft.MindRuntime.Job;
import io.github.g4solio.mindcraft.MindRuntime.Order;
import io.github.g4solio.mindcraft.MindRuntime.Refusal;
import io.github.g4solio.mindcraft.MindRuntime.State;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Uno, the central intelligence. In this increment Uno does three things.
 * It changes a written request into one action. A pause and a report run immediately. Each other action becomes a proposal.
 * It sends the request of a proposal to the runtime only when the player confirms. It reports on jobs with data from the runtime.
 * When Uno reads, the sentence of a provider can be the answer, after the checks of the server (D42, point 1).
 * <p>
 * Uno has no authority of its own. Each order goes through the ordinary validation of the runtime.
 * Uno speaks English to the player.
 */
// ponytail: English text in the code. Use translation keys when a second language is necessary.
public final class Uno {
    /**
     * Changes a written request into one action from a closed list (D29). An implementation can call a language model.
     * The context holds only the stations and the robots of the player. The answer is one Action, with typed values.
     * A provider that reads JSON from a model uses parse(): the one reader of the JSON of an action.
     * The answer also has the input tokens and the output tokens of the model. A provider without a model reports zero.
     * Each exception is a failure of the provider. Only the action "none" says that the request gives no action.
     * A report and a "none" also have one sentence for the player: the voice. The fixed rules give no sentence (D42, point 1).
     * After a failure of a provider with a model, the fixed rules read the request (D42, point 17).
     */
    public interface Provider {
        Answer answer(String request, Context context) throws Exception;

        /**
         * True if the provider calls a language model, thus each request can cost money. Then only an operator uses it, and
         * each actor has a limit of requests in one server session (D33). A provider with a model must say so here.
         */
        default boolean callsModel() {
            return false;
        }

        /** How the server log names the provider: "Uno interprets requests with (name)". */
        default String name() {
            return "the fixed rules, without a language model";
        }

        /** The model that a provider with a model asks, for the record of each request (D41, decision 3). Null without a model. */
        default String model() {
            return null;
        }

        /**
         * The line of the screen that says where the written requests of the player go on this route, or null for no line.
         * A provider that sends the text out of the server must say so here (D33).
         */
        default String notice(Route route) {
            return null;
        }
    }

    /** The answer of a provider: one action, and the tokens that the answer used. */
    public record Answer(Action action, long inputTokens, long outputTokens) {
    }

    /**
     * What a provider knows: only the data of the player. The names of the stations, and the robots, each with a "free" flag (D21).
     * The facts of each job of the player, and the newest lines of the diary of the player (D42, point 2).
     * Never a coordinate, the name of a player or the free text of a reason: a recorded reason can hold a position.
     */
    public record Context(List<String> stations, List<Robot> robots, List<Facts> jobs, List<Line> diary) {
        public record Robot(int id, boolean free) {
        }

        /**
         * The facts of one job: the part of its account that goes to the model. JobAccount builds them (D42, point 2).
         * The model does not get an uncertain number (D32). Such a number is null, and a sentence of "uncertain" says which value
         * is not given, and why (D42, point 3). "Still to carry" and "above the reserve" are derived numbers: the model does not
         * calculate (D42, point 4). The age of an observation is in words. The fuel level and the trips that it gives between
         * the two docks are the values of the last contact of the robot with an active job (D42, point 19). The trips are a derived number too.
         */
        public record Facts(int job, int robot, String module, String item, String from, String to, int quantity, int reserve,
                            State state, Cause cause, Integer delivered, Integer onBoard, Stock source, Stock destination,
                            Integer stillToCarry, Integer aboveReserve, Integer fuel, Integer trips, List<String> uncertain) {
        }

        /** An observed stock: the count, and the age of the observation in words, for example "12 min ago". */
        public record Stock(int count, String age) {
        }

        /**
         * One line of the diary (D31), as data: the age in words, the job, the robot, the kind, the state, the cause, and the quantities
         * of the job at that moment. A number that the facts of its job do not give is null here too.
         */
        public record Line(String age, int job, int robot, Entry.Kind kind, State state, Cause cause, int quantity, int reserve,
                           Integer delivered, Integer onBoard) {
        }

        /** The context as JSON, for a model. The stations and the robots have the shape of S2. A value that is null is not in the JSON. */
        String toJson() {
            JsonArray names = new JsonArray();
            stations.forEach(names::add);
            JsonArray list = new JsonArray();
            for (Robot robot : robots) {
                JsonObject item = new JsonObject();
                item.addProperty("id", robot.id());
                item.addProperty("free", robot.free());
                list.add(item);
            }
            JsonArray facts = new JsonArray();
            for (Facts job : jobs) {
                JsonObject item = new JsonObject();
                item.addProperty("job", job.job());
                item.addProperty("robot", job.robot());
                item.addProperty("module", job.module());
                item.addProperty("item", job.item());
                item.addProperty("from", job.from());
                item.addProperty("to", job.to());
                item.addProperty("quantity", job.quantity());
                item.addProperty("reserve", job.reserve());
                item.addProperty("state", job.state().name());
                item.addProperty("cause", job.cause().name());
                put(item, "delivered", job.delivered());
                put(item, "onBoard", job.onBoard());
                put(item, "sourceStock", job.source());
                put(item, "destinationStock", job.destination());
                put(item, "stillToCarry", job.stillToCarry());
                put(item, "aboveReserve", job.aboveReserve());
                put(item, "fuel", job.fuel());
                put(item, "fuelForTrips", job.trips());
                JsonArray uncertain = new JsonArray();
                job.uncertain().forEach(uncertain::add);
                item.add("uncertain", uncertain);
                facts.add(item);
            }
            JsonArray lines = new JsonArray();
            for (Line line : diary) {
                JsonObject item = new JsonObject();
                item.addProperty("age", line.age());
                item.addProperty("job", line.job());
                item.addProperty("robot", line.robot());
                item.addProperty("kind", line.kind().name());
                item.addProperty("state", line.state().name());
                item.addProperty("cause", line.cause().name());
                item.addProperty("quantity", line.quantity());
                item.addProperty("reserve", line.reserve());
                put(item, "delivered", line.delivered());
                put(item, "onBoard", line.onBoard());
                lines.add(item);
            }
            JsonObject context = new JsonObject();
            context.add("stations", names);
            context.add("robots", list);
            context.add("jobs", facts);
            context.add("diary", lines);
            return context.toString();
        }

        /** A number that is not null. An uncertain number is not in the JSON: the model cannot repeat it. */
        private static void put(JsonObject json, String name, Integer value) {
            if (value != null) json.addProperty(name, value);
        }

        /** A stock that is not null: its count and its age. */
        private static void put(JsonObject json, String name, Stock stock) {
            if (stock == null) return;
            JsonObject item = new JsonObject();
            item.addProperty("count", stock.count());
            item.addProperty("age", stock.age());
            json.add(name, item);
        }
    }

    /**
     * One action of the closed list (D29), with typed values. Uno still checks each value against the world: a provider can give anything.
     * An answer that Uno cannot read is an action too: Unreadable. Its sentence says that the answer was not valid.
     * From a provider with a model, the fixed rules read the request instead (D42, point 17).
     */
    public sealed interface Action {
        /** A new job, with its order. */
        record Start(Order order) implements Action {
        }

        /**
         * An action about one job: change, pause, resume, cancel or report. A change has the new values: null keeps a value.
         * A report of a provider also has one sentence for the player: the voice (D42, point 1). Null without a sentence.
         * Uno does not use a sentence for the other kinds: the server writes the answer when Uno acts.
         */
        record OnJob(Kind kind, Target target, Integer quantity, Integer reserve, String sentence) implements Action {
            /** An action without a sentence. The fixed rules give no sentence. */
            public OnJob(Kind kind, Target target, Integer quantity, Integer reserve) {
                this(kind, target, quantity, reserve, null);
            }
        }

        /**
         * No action of the list. A provider gives one sentence for the player: the voice (D42, point 15). It takes the place of the
         * reason of increment A. The fixed rules give no sentence: they give one short reason, which is not empty.
         * Exactly one of the two is not null.
         */
        record None(String reason, String sentence) implements Action {
            public None {
                if ((reason == null) == (sentence == null)) throw new IllegalArgumentException("\"none\" has a reason or a sentence");
                if (reason != null) {
                    reason = reason.trim();
                    if (reason.isEmpty()) throw new IllegalArgumentException("the reason of \"none\" is empty");
                }
            }

            /** The answer of the fixed rules: a reason, and no sentence. */
            public None(String reason) {
                this(reason, null);
            }
        }

        /** An answer outside the closed list, or with a value of a wrong type. */
        record Unreadable() implements Action {
        }
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

    /** The kinds of action of the closed list (D29). */
    enum Kind { START, CHANGE, PAUSE, RESUME, CANCEL, REPORT, NONE }

    /** The result of the checks of the sentence of a provider (D42, points 4 and 5). The record of a request holds it: see ModelRequest. */
    enum Voice {
        /** The sentence of a report or of "none" passed the checks: Uno says it. */
        PASSED,
        /** The sentence failed a check: Uno uses the fixed template. An empty sentence fails too. */
        FAILED,
        /** The answer needs no sentence: the server writes the text of an action. The fixed rules give no sentence either. */
        NOT_NEEDED,
        /** The model gave no valid action: the fixed rules read the request (D42, point 17). See noValidAction. */
        NO_VALID_ACTION
    }

    /**
     * The record of one written request to a provider with a model (D33, and D42): the model that the provider names, the input tokens
     * and the output tokens of the answer, the time of the answer in milliseconds, the action of the model and the result of the checks.
     * The action is null when the provider gave no answer: a failure or the time limit. Then the tokens are zero, as in the counter.
     * Uno keeps the record of the newest request of each actor in the server session, and nothing more (D24).
     */
    record ModelRequest(String model, long inputTokens, long outputTokens, long millis, Action action, Voice voice) {
        /** The sentence that Uno did not use: the sentence of the model after a failed check. Null in each other case. */
        String unused() {
            return voice == Voice.FAILED ? sentence(action) : null;
        }
    }

    /** The provider of the /uno command and of the screen. A different provider can replace it. See MindCraft.chooseProvider. */
    static volatile Provider provider = new RuleProvider();
    static final long TIMEOUT_MS = 20_000;
    /** The limit of the requests to a model for each actor in one server session (D33). An initial value, fixed in the code. */
    static final int MODEL_REQUESTS = 100;
    /**
     * The fixed rules. They read the request of an actor who cannot use the model now, and a request for which the model gave no
     * valid action. They have no limit.
     */
    private static final RuleProvider RULES = new RuleProvider();
    private static final String LIMIT_REACHED = "You reached the limit of " + MODEL_REQUESTS + " model requests in this server session. "
        + "Until the server restarts, I read your requests with the fixed rules: they understand only simple sentences. ";
    /** The sentence before the answer of the fixed rules when the model gave no valid action (D42, point 17). %s is the reason. */
    private static final String NO_VALID_ACTION = "The model gave no valid answer (%s), "
        + "thus I read your request with the fixed rules: they understand only simple sentences. ";
    private static final String INVALID = "I did not get a valid configuration. I created no proposal.";
    /** The answer to "none" when its sentence fails a check (D42, point 15). */
    private static final String CANNOT_ANSWER = "I cannot answer that. I did nothing.";
    /** The limits of a sentence of a provider (D42, point 5). Initial values. A cut can remove a caution: a longer sentence fails. */
    static final int MAX_SENTENCES = 2, MAX_SENTENCE_LENGTH = 300;
    /** A run of digits, in each script: a number as a sentence writes it. */
    private static final Pattern DIGITS = Pattern.compile("\\p{Nd}+");
    /** The end of a sentence: a full stop, a question mark or an exclamation mark, a closing quote or bracket, and a space. */
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?]+['\"’”)]*\\s+");
    /** The sentence of Uno before its first answer in a session. */
    private static final String GREETING = "Tell me what you need.";

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
    sealed interface Request permits Start, Replace, Change, Control {
        /** Sends the request. A refusal of the runtime is a Refusal. The answer is the sentence of Uno. */
        String send(MindRuntime runtime, Actor actor);

        /** What the request does, for the sentence of a refusal: "I cannot (what): (reason)". */
        String what();
    }

    /** A new job. */
    record Start(Order order) implements Request {
        @Override
        public String send(MindRuntime runtime, Actor actor) {
            return started(runtime.start(actor, order));
        }

        @Override
        public String what() {
            return "start the job";
        }
    }

    /**
     * A new job in the place of the open job of the robot (D42, point 10). The request names the job that it replaces.
     * The answer gives the load that the new job adopted: it can differ from the load in the text of the proposal.
     */
    record Replace(int job, Order order) implements Request {
        @Override
        public String send(MindRuntime runtime, Actor actor) {
            Job next = runtime.replace(actor, job, order);
            return Notices.capital(Notices.job(job, order.robot())) + " is cancelled. " + started(next)
                + (next.carried > 0 ? "" : " The robot holds no " + MindRuntime.itemName(next.item) + ": the new job starts without a load.");
        }

        @Override
        public String what() {
            return "replace the job";
        }
    }

    /** New values of a job. The request also names the revision of the job: a confirmation never sends values about an older state. */
    record Change(int robot, int job, int revision, int quantity, int reserve) implements Request {
        @Override
        public String send(MindRuntime runtime, Actor actor) {
            Job changed = runtime.update(actor, robot, job, revision, quantity, reserve);
            return Notices.capital(Notices.job(job, robot)) + " changed. " + quantityName(changed) + ": " + quantity + ". "
                + reserveName(changed) + ": " + reserve + ". The robot uses the new values at its next step.";
        }

        @Override
        public String what() {
            return "change the job";
        }
    }

    /**
     * A pause, a resume or a cancellation of a job: "pause", "resume" or "cancel". It also holds the state of the job when Uno read it.
     * The text of a resume proposal says what the resume accepts in that state (story 12). Thus a resume in a different state is refused.
     * A pause and a cancellation mean the same in each state: the cargo stays on board.
     */
    record Control(String action, int robot, int job, State state) implements Request {
        @Override
        public String send(MindRuntime runtime, Actor actor) {
            if (action.equals("resume")) {
                State now = runtime.checkControl(actor, robot, job, action).state;
                if (now != state) throw new Refusal("Job " + job + " changed: its state is " + now + " now, not " + state + ". Read it again.");
            }
            runtime.control(actor, robot, job, action);
            return switch (action) {
                case "pause" -> Notices.capital(Notices.job(job, robot)) + " is paused. The cargo stays on board.";
                case "resume" -> "I resume " + Notices.job(job, robot) + ".";
                default -> Notices.capital(Notices.job(job, robot)) + " is cancelled. The cargo stays on board the robot.";
            };
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
        /** The players whose last sentence of Uno is the summary of an opening. Its ages are the ages at that opening. */
        final Set<UUID> summaries = ConcurrentHashMap.newKeySet();
        /** The players with a request that has no answer yet. */
        final Set<UUID> pending = ConcurrentHashMap.newKeySet();
        /** The requests to a model and their tokens, for each actor. The console is an actor too. */
        final Map<UUID, Usage> usage = new ConcurrentHashMap<>();
        /** The actors that Uno told that they reached the limit. Uno says it one time in a session. */
        final Set<UUID> toldLimit = ConcurrentHashMap.newKeySet();
        /** The record of the newest written request of each actor to a model. */
        final Map<UUID, ModelRequest> requests = new ConcurrentHashMap<>();
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
        session.summaries.remove(actor.id());
    }

    static String said(Actor actor) {
        return session.said.getOrDefault(actor.id(), GREETING);
    }

    /**
     * The sentence of Uno at an opening (D30): the summary, or null with nothing to say. With nothing to say, Uno keeps its sentence.
     * A summary of an earlier opening does not stay: its ages are old, and old data must not look like current data (story 43).
     * Then Uno says the greeting.
     */
    static void sayAtOpening(Actor actor, String summary) {
        if (summary != null) {
            say(actor, summary);
            session.summaries.add(actor.id());
        } else if (session.summaries.contains(actor.id())) {
            say(actor, GREETING);
        }
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

    /**
     * The record of the newest written request of the actor to a model in this server session, or null. Uno writes it before the
     * answer: thus the consumer of the answer of a request reads the record of that request. A test reads it.
     */
    static ModelRequest modelRequest(Actor actor) {
        return session.requests.get(actor.id());
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
     * If the model gives no valid action, the fixed rules read the same request, and Uno says so first (D42, point 17).
     * A provider without a model keeps the failure sentence.
     */
    public static void ask(MinecraftServer server, Actor actor, String request, Provider provider, long timeoutMs, Consumer<String> reply) {
        Session mine = session;
        Context context = context(MindRuntime.get(server), actor, server.overworld().getGameTime());
        if (!mine.pending.add(actor.id())) {
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
        long sent = System.nanoTime();
        CompletableFuture
            .supplyAsync(() -> {
                try {
                    return chosen.answer(request, context);
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            })
            .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .whenComplete((answer, error) -> {
                // The time of the answer of the provider, or of the time limit. The wait for the server thread does not count.
                long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - sent);
                server.execute(() -> {
                    // The answer belongs to the session of its request. An answer for an earlier session changes nothing.
                    // After a server stop, execute() runs this immediately on the thread of the provider. Then it must touch nothing.
                    if (server.isStopped() || mine != session) return;
                    mine.pending.remove(actor.id());
                    if (error == null && route == Route.MODEL) {
                        mine.usage.merge(actor.id(), new Usage(0, answer.inputTokens(), answer.outputTokens()), Usage::plus);
                    }
                    MindRuntime runtime = MindRuntime.get(server);
                    long now = server.overworld().getGameTime();
                    // A model that gives no valid action (D42, point 17): the request still counts, and the fixed rules read it with the same context.
                    String why = route == Route.MODEL ? noValidAction(answer, error) : null;
                    // The record of a request to a model, and its line in the server log (D33, D42). It comes before the answer.
                    if (route == Route.MODEL) {
                        ModelRequest record = new ModelRequest(provider.model(), error == null ? answer.inputTokens() : 0,
                            error == null ? answer.outputTokens() : 0, millis, error == null ? answer.action() : null,
                            why != null ? Voice.NO_VALID_ACTION : voice(answer.action(), request, context));
                        mine.requests.put(actor.id(), record);
                        MindCraft.LOGGER.info("{}", line(record, request));
                    }
                    String said = why != null ? NO_VALID_ACTION.formatted(why) + act(mine, runtime, actor, request, context, RULES.answer(request, context), now)
                        : error == null ? act(mine, runtime, actor, request, context, answer, now) : failure(error);
                    reply.accept(limit + said);
                });
            });
    }

    /**
     * The line of the server log for a written request to a model (D42, choices of the implementation): the record, and the text of
     * the request of the player. After a failed check, also the sentence that Uno did not use. Each text is in JSON quotes: a line break
     * in a text does not start a new line of the log. The line has no key: only the client of the provider holds it. It has no
     * coordinate: the context of the request is not in the line, and the model gets no coordinate. The request is the text of the player.
     */
    private static String line(ModelRequest record, String request) {
        String unused = record.unused();
        return "Uno request: model " + record.model() + ", " + record.inputTokens() + " input tokens, " + record.outputTokens()
            + " output tokens, " + record.millis() + " ms, action " + kind(record.action()) + ", checks " + record.voice()
            + (unused == null ? "" : ", unused sentence " + new JsonPrimitive(unused)) + ", request " + new JsonPrimitive(request);
    }

    /** The action of a record in the server log: its kind, "unreadable", or "no answer" after a failure or the time limit. */
    private static String kind(Action action) {
        if (action instanceof Action.Start) return "start";
        if (action instanceof Action.OnJob job) return job.kind().name().toLowerCase(Locale.ROOT);
        if (action instanceof Action.None) return "none";
        return action == null ? "no answer" : "unreadable";
    }

    /**
     * Why a model gave no valid action, or null if it gave one (D42, point 17): a failure of the service, the time limit of Uno,
     * a refusal, or an answer that Uno cannot read. The Claude provider gives a refusal as an exception with its reason.
     */
    private static String noValidAction(Answer answer, Throwable error) {
        if (error instanceof TimeoutException) return "the request reached the time limit";
        if (error != null) return reason(error);
        return answer.action() instanceof Action.Unreadable ? "I cannot read its answer" : null;
    }

    /**
     * Only the data that the actor owns. A provider must not see the stations, the robots, the jobs or the diary of other players.
     * "Now" is the game time of the request: the ages and the doubts of the facts come from it. A test gives a later moment.
     */
    static Context context(MindRuntime runtime, Actor actor, long now) {
        List<String> stations = new ArrayList<>();
        runtime.stations.forEach((name, station) -> {
            if (actor.owns(station.owner())) stations.add(name);
        });
        List<Context.Robot> robots = new ArrayList<>();
        runtime.robots.forEach((id, owner) -> {
            if (!actor.owns(owner)) return;
            Job job = runtime.jobs.get(id);
            robots.add(new Context.Robot(id, job == null || !job.state.open()));
        });
        List<Context.Facts> jobs = runtime.jobs(actor).stream().map(job -> JobAccount.of(job, now).facts()).toList();
        return new Context(List.copyOf(stations), List.copyOf(robots), jobs, JobAccount.lines(runtime.diary(actor), jobs, now));
    }

    /**
     * Reads the JSON of an action, as a model writes it (D29). Each provider that answers in JSON uses this function.
     * <ul>
     * <li>start, with the order: {"action": "start", "module": "keep_stock" or "transport", "robot": 0, "from": "...", "to": "...",
     * "item": "minecraft:coal", "quantity": 64, "reserve": 32}. A reserve that is null or absent is 0.</li>
     * <li>change, pause, resume, cancel, report, with the target: a robot number, a station name, or nothing.
     * {"action": "pause", "robot": 3, "station": null}. A change also has "quantity", "reserve" or both. Null is a value that does not change.
     * A report also has its sentence for the player in "reason" (D42, point 1).</li>
     * <li>none, with its sentence for the player: {"action": "none", "reason": "You did not give the quantity."}.</li>
     * </ul>
     * A field that the action does not use is ignored. A model does not guarantee the case of a value of its schema.
     * Text that is not such an action, a value of a wrong type, and a "none" without the field "reason" give Unreadable.
     */
    static Action parse(String json) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            Kind kind = Kind.valueOf(o.get("action").getAsString().toUpperCase(Locale.ROOT));
            if (kind == Kind.START) {
                JsonElement reserve = o.get("reserve");
                return new Action.Start(new Order(o.get("module").getAsString(), o.get("robot").getAsInt(), o.get("from").getAsString(),
                    o.get("to").getAsString(), ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(o.get("item").getAsString())),
                    o.get("quantity").getAsInt(), reserve == null || reserve.isJsonNull() ? 0 : reserve.getAsInt()));
            }
            // The field "reason" carries the sentence of "none" (D42, point 15). An empty sentence fails the check of the length.
            if (kind == Kind.NONE) return new Action.None(null, o.get("reason").getAsString());
            Target target = Target.of(o);
            if (target == null) return new Action.Unreadable();
            boolean change = kind == Kind.CHANGE;
            // The field "reason" carries the sentence of a report. A sentence for an action of the server is not read (D42, point 1).
            return new Action.OnJob(kind, target, change ? integer(o, "quantity") : null, change ? integer(o, "reserve") : null,
                kind == Kind.REPORT ? text(o, "reason") : null);
        } catch (RuntimeException e) {
            return new Action.Unreadable();
        }
    }

    /**
     * The sentence of Uno for the answer of a provider. Uno reads the answer and does not trust it: a provider can give anything.
     * "Pause" and "report" run immediately (D29). "Start", "change", "resume" and "cancel" become a proposal.
     * "None" gives its sentence, or the reason of the fixed rules.
     * The request and the context are the ones that the provider got: the check of the numbers of a sentence reads them (D42, point 4).
     */
    private static String act(Session mine, MindRuntime runtime, Actor actor, String request, Context context, Answer answer, long now) {
        Action action = answer.action();
        if (action instanceof Action.Start start) return start(mine, runtime, actor, start.order());
        if (action instanceof Action.None none) return none(none, request, context);
        // An Unreadable answer, or no action at all.
        if (!(action instanceof Action.OnJob job) || job.target() == null) return INVALID;
        return switch (job.kind()) {
            case PAUSE -> pause(runtime, actor, job.target());
            // A report with a sentence that passes the checks gets the voice. Each other report gets the fixed report of A (D42, point 16).
            case REPORT -> voice(action, request, context) == Voice.PASSED ? voiced(runtime, actor, job, now) : report(runtime, actor, job.target(), now);
            case CHANGE -> change(mine, runtime, actor, job);
            case RESUME, CANCEL -> control(mine, runtime, actor, job.target(), job.kind());
            // A start and a "none" have actions of their own.
            case START, NONE -> INVALID;
        };
    }

    /**
     * The result of the checks for the action of a provider (D42, points 4 and 5). Only a report and a "none" have a sentence.
     * The fixed rules give no sentence: their answer needs none. A model without a valid action is NO_VALID_ACTION: see noValidAction.
     */
    static Voice voice(Action action, String request, Context context) {
        String sentence = sentence(action);
        if (sentence == null) return Voice.NOT_NEEDED;
        return passes(sentence, request, context) ? Voice.PASSED : Voice.FAILED;
    }

    /** The sentence of the action of a provider, or null: only a report and a "none" have one. */
    private static String sentence(Action action) {
        return action instanceof Action.None none ? none.sentence()
            : action instanceof Action.OnJob job && job.kind() == Kind.REPORT ? job.sentence() : null;
    }

    /**
     * The checks of a sentence of a provider. The length (D42, point 5): two sentences and 300 characters at most, and not empty.
     * The numbers (D42, point 4): each run of digits of the sentence is a run of digits of the context that the server sent,
     * or of the request of the player. Thus the model cannot invent a number. A number in words passes: a known limit (D32).
     */
    static boolean passes(String sentence, String request, Context context) {
        String text = sentence.strip();
        if (text.isEmpty() || text.length() > MAX_SENTENCE_LENGTH || SENTENCE_END.split(text).length > MAX_SENTENCES) return false;
        Set<String> sent = runs(context.toJson());
        sent.addAll(runs(request));
        return sent.containsAll(runs(text));
    }

    /** The runs of digits of a text. */
    private static Set<String> runs(String text) {
        Set<String> runs = new HashSet<>();
        Matcher digits = DIGITS.matcher(text);
        while (digits.find()) runs.add(digits.group());
        return runs;
    }

    /**
     * The answer to "none". The voice of a provider stands alone: the sentence, or one fixed sentence if it fails a check (D42, point 15).
     * The fixed rules give a reason: their answer is the template of increment A.
     */
    private static String none(Action.None none, String request, Context context) {
        if (none.sentence() == null) return "I cannot propose a job: " + none.reason() + (none.reason().endsWith(".") ? "" : ".");
        return voice(none, request, context) == Voice.PASSED ? none.sentence().strip() : CANNOT_ANSWER;
    }

    /**
     * The voice of a report that passed the checks (D42, point 16). First the fixed sentences about the uncertain numbers, then the voice.
     * The fixed sentences are about the job that the target names. With no target, or with a target that does not fit exactly one job,
     * they are about each job of the actor: the model had the facts of each job. They read the record now, as the fixed report does.
     */
    private static String voiced(MindRuntime runtime, Actor actor, Action.OnJob report, long now) {
        Job named = report.target().none() ? null : find(runtime, actor, report.target()).job();
        List<String> said = new ArrayList<>();
        for (Job job : named != null ? List.of(named) : runtime.jobs(actor)) said.addAll(JobAccount.of(job, now).cautions());
        said.add(report.sentence().strip());
        return String.join(" ", said);
    }

    /**
     * The job that an action names: a robot number, a station name, or nothing (D29).
     * A null field, an absent field and an empty station are the same: the request does not give that value.
     */
    record Target(Integer robot, String station) {
        /** Null if a value has a wrong type: then the answer is not valid. */
        static Target of(JsonObject json) {
            try {
                JsonElement robot = json.get("robot"), station = json.get("station");
                String name = station == null || station.isJsonNull() ? "" : station.getAsString().trim();
                return new Target(robot == null || robot.isJsonNull() ? null : robot.getAsInt(), name.isEmpty() ? null : name);
            } catch (RuntimeException e) {
                return null;
            }
        }

        boolean none() {
            return robot == null && station == null;
        }
    }

    /** The result of the search of a job: the job, or the sentence that says why Uno did not find one. */
    record Found(Job job, String problem) {
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
     * A target that is not valid (null) gets the sentence of an answer that is not valid.
     */
    static Found find(MindRuntime runtime, Actor actor, Target target) {
        if (target == null) return new Found(null, INVALID);
        String ask = " Tell me the robot number of the job. I did nothing.";
        if (target.robot() != null) {
            Job job = runtime.jobs.get(target.robot());
            if (job != null && actor.owns(job.owner)) return new Found(job, null);
            return new Found(null, "You have no job on robot #" + target.robot() + "." + ask);
        }
        String station = target.station();
        List<Job> fit = new ArrayList<>();
        for (Job job : runtime.jobs.values()) {
            if (actor.owns(job.owner) && job.state.open()
                && (station == null || job.from.equalsIgnoreCase(station) || job.to.equalsIgnoreCase(station))) fit.add(job);
        }
        if (fit.size() == 1) return new Found(fit.get(0), null);
        String count = station == null
            ? (fit.isEmpty() ? "You have no open job." : "You have " + fit.size() + " open jobs.")
            : (fit.isEmpty() ? "None of your open jobs uses the station '" + station + "'."
                : fit.size() + " of your open jobs use the station '" + station + "'.");
        return new Found(null, count + ask);
    }

    /**
     * A pause runs immediately, without a proposal: it only stops the robot (D29).
     * It is the ordinary request of the runtime, with the job ID. A refusal of the runtime becomes the sentence of Uno.
     */
    private static String pause(MindRuntime runtime, Actor actor, Target target) {
        Found found = find(runtime, actor, target);
        if (found.problem() != null) return found.problem();
        Job job = found.job();
        return send(runtime, actor, new Control("pause", job.robot, job.id, job.state));
    }

    /**
     * An action of a button of a job on the screen: "pause", "resume" or "cancel" (D41). The button names the job, as the words do.
     * A button acts at once: the player sees the job and clicks. "Cancel" is the exception: it becomes a proposal, as for the words (D40).
     * The sentences are the sentences of the words.
     */
    static String button(MindRuntime runtime, Actor actor, String action, int robot, int jobId) {
        Job job;
        try {
            job = runtime.checkControl(actor, robot, jobId, action);
        } catch (Refusal refusal) {
            return "I cannot " + action + " the job: " + refusal.getMessage();
        }
        if (action.equals("cancel")) return proposeControl(session, runtime, actor, job, Kind.CANCEL);
        return send(runtime, actor, new Control(action, robot, jobId, job.state));
    }

    /** Sends a request at once. A refusal of the runtime becomes the sentence of Uno. */
    static String send(MindRuntime runtime, Actor actor, Request request) {
        try {
            return request.send(runtime, actor);
        } catch (Refusal refusal) {
            return "I cannot " + request.what() + ": " + refusal.getMessage();
        }
    }

    /** A report runs immediately. Without a target it is the report on all jobs of the actor, as the "Report" button gives it. */
    private static String report(MindRuntime runtime, Actor actor, Target target, long now) {
        if (target.none()) return report(runtime, actor, now);
        Found found = find(runtime, actor, target);
        return found.problem() != null ? found.problem() : JobAccount.of(found.job(), now).report();
    }

    /**
     * A change becomes a proposal (D29). It holds the robot, the job ID and the revision, as the "Apply" button does (D19).
     * A value that the request does not give stays as it is. The runtime checks the change now, and again at the confirmation.
     */
    private static String change(Session mine, MindRuntime runtime, Actor actor, Action.OnJob action) {
        Integer quantity = action.quantity(), reserve = action.reserve();
        if (quantity == null && reserve == null) {
            return "I cannot propose a change: the request gives no new quantity and no new reserve. I created no proposal.";
        }
        Found found = find(runtime, actor, action.target());
        if (found.problem() != null) return found.problem();
        Job job = found.job();
        Change change = new Change(job.robot, job.id, job.revision, quantity == null ? job.quantity : quantity, reserve == null ? job.reserve : reserve);
        try {
            runtime.checkUpdate(actor, change.robot(), change.job(), change.revision(), change.quantity(), change.reserve());
        } catch (Refusal refusal) {
            return "I cannot propose this change: " + refusal.getMessage();
        }
        return propose(mine, actor, "change " + name(job) + ". " + value(quantityName(job), job.quantity, change.quantity())
            + ". " + value(reserveName(job), job.reserve, change.reserve()), change);
    }

    /**
     * A resume or a cancellation becomes a proposal (D29). It holds the robot and the job ID, as the buttons of the screen do (D19),
     * and the state of the job. The runtime checks the request now, and again at the confirmation.
     */
    private static String control(Session mine, MindRuntime runtime, Actor actor, Target target, Kind action) {
        Found found = find(runtime, actor, target);
        return found.problem() != null ? found.problem() : proposeControl(mine, runtime, actor, found.job(), action);
    }

    /** The proposal of a resume or a cancellation of this job, after the check of the runtime. The words and the button "Cancel" use it. */
    private static String proposeControl(Session mine, MindRuntime runtime, Actor actor, Job job, Kind action) {
        String verb = action.name().toLowerCase(Locale.ROOT);
        try {
            runtime.checkControl(actor, job.robot, job.id, verb);
        } catch (Refusal refusal) {
            return "I cannot propose to " + verb + " the job: " + refusal.getMessage();
        }
        String text = verb + " " + name(job);
        if (action == Kind.CANCEL) {
            text += ". The cargo stays on board the robot";
        } else if (job.state == State.RECOVERY) {
            // The confirmation accepts the load in the robot as the truth. The player must know this before.
            text += ". The job needs a check: the runtime accepts the load that it observes in the robot, and the totals can be inexact";
        }
        return propose(mine, actor, text, new Control(verb, job.robot, job.id, job.state));
    }

    /** A whole number of an answer, or null if the answer does not give it. A value of a wrong type is a RuntimeException. */
    private static Integer integer(JsonObject json, String field) {
        JsonElement value = json.get(field);
        return value == null || value.isJsonNull() ? null : value.getAsInt();
    }

    /** A text of an answer, or null if the answer does not give it. A value of a wrong type is a RuntimeException. */
    private static String text(JsonObject json, String field) {
        JsonElement value = json.get(field);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    /** A job in a proposal: its ID, its robot and what it does. */
    private static String name(Job job) {
        String item = MindRuntime.itemName(job.item), from = "'" + job.from + "'", to = "'" + job.to + "'";
        return Notices.job(job.id, job.robot) + " ("
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
    private static String value(String name, int before, int after) {
        return name + ": " + (before == after ? before + ", unchanged" : "from " + before + " to " + after);
    }

    /**
     * A start becomes a proposal, after the ordinary validation of the order. The proposal says what the robot holds.
     * A start for a robot with an open job of the actor is the proposal of a replacement (D42, point 10). The new order must be
     * valid as for a free robot. The text names the job that it replaces. The robot can work until the confirmation.
     */
    private static String start(Session mine, MindRuntime runtime, Actor actor, Order order) {
        Job open = runtime.jobs.get(order.robot());
        boolean replace = open != null && open.state.open() && actor.owns(open.owner);
        RobotApi robot;
        try {
            robot = replace ? runtime.checkReplace(actor, open.id, order) : runtime.check(actor, order);
        } catch (Refusal refusal) {
            return "I cannot propose this job: " + refusal.getMessage();
        }
        String text = describe(order) + adoption(order, robot);
        if (!replace) return propose(mine, actor, text, new Start(order));
        // The text of a replacement gives the load at the moment of the proposal, also when it is zero (D42, point 10).
        String empty = robot.held(order.item()) > 0 ? "" : ". The robot holds no " + MindRuntime.itemName(order.item()) + " now";
        return propose(mine, actor, "cancel " + name(open) + ", and start in its place: " + text + empty
            + ". The load can change until the confirmation: the new job counts the load that the robot holds then", new Replace(open.id, order));
    }

    /**
     * What a new job does with the load of the robot, in the words of a proposal (D42, point 9). Empty for a robot that holds nothing.
     * The items of the job are its first load. A load above the quantity follows D16.1. The items of other kinds stay on board.
     * Each sentence starts with ". ", as the other parts of the text of a proposal. The proposal of a replacement repeats these sentences.
     */
    static String adoption(Order order, RobotApi robot) {
        String item = MindRuntime.itemName(order.item()), text = "";
        int held = robot.held(order.item()), others = robot.others(order.item());
        if (held > 0) {
            text += ". The robot holds " + held + " x " + item + ": the job counts them as its first load";
            // A job that ends stops with the rest on board. A job that does not end keeps the rest for its next need.
            if (held > order.quantity()) {
                text += MindRuntime.Behaviour.of(order.module()).ends
                    ? ". That is more than the quantity: the job delivers at most " + order.quantity() + ", then it stops with the rest on board for your decision"
                    : ". That is more than the level: the rest stays on board for the next need";
            }
        }
        // The Lua module burns each item that is not the item of the job when the fuel ends (D12). The runtime does not know which items burn.
        if (others > 0) {
            text += ". " + others + " items that are not " + item + " stay on board: the job does not count them"
                + ". When its fuel ends, the robot burns those that can burn";
        }
        return text;
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

    /**
     * The answer to a start that the runtime accepted, for each source of orders: a confirmation, a replacement and the start command.
     * It gives the load that the job adopted at the start (D42, point 9). That load can differ from the load at the proposal.
     */
    static String started(Job job) {
        return "Job " + job.id + " started on robot #" + job.robot + "."
            + (job.carried > 0 ? " It counts the " + job.carried + " x " + MindRuntime.itemName(job.item) + " on board as its first load." : "");
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
        if (mine.pending.contains(actor.id())) return "I am still working on your request. I have no proposal to confirm yet.";
        Proposal proposal = mine.proposals.get(actor.id());
        if (proposal == null) {
            return "I have no pending proposal. A new request replaces the proposal before it. A proposal that is not confirmed is not saved.";
        }
        if (proposal.number() != number) return "That is not my current proposal. I started nothing. Read the proposal again.";
        mine.proposals.remove(actor.id());
        return send(MindRuntime.get(server), actor, proposal.request());
    }

    public static String report(MinecraftServer server, Actor actor) {
        return report(MindRuntime.get(server), actor, server.overworld().getGameTime());
    }

    /** A report on the jobs of the actor. Each number comes from the record of the runtime. */
    static String report(MindRuntime runtime, Actor actor, long now) {
        List<String> lines = new ArrayList<>();
        runtime.jobs.forEach((robotId, job) -> {
            if (actor.owns(job.owner)) lines.add(JobAccount.of(job, now).report());
        });
        return lines.isEmpty() ? "I have no record of a job of yours." : String.join("\n", lines);
    }

    /**
     * A failure of a provider without a model: an exception or the time limit. It is not an answer, thus each exception gets this sentence.
     * For a provider with a model the fixed rules read the request instead (D42, point 17).
     */
    private static String failure(Throwable error) {
        return "I cannot interpret the request now (" + reason(error) + "). The jobs in progress continue.";
    }

    /** The reason of a failure of a provider: the message of the exception, or its name. */
    private static String reason(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
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
        private static final List<Map.Entry<Kind, Pattern>> ACTION_WORDS = List.of(
            Map.entry(Kind.RESUME, Pattern.compile("\\b(?:resume|continue|riprendi|continua)\\b")),
            Map.entry(Kind.CANCEL, Pattern.compile("\\b(?:cancel|annulla|cancella)\\b")),
            Map.entry(Kind.PAUSE, Pattern.compile("\\b(?:pause|pausa|sospendi)\\b")),
            Map.entry(Kind.CHANGE, Pattern.compile(QUANTITY.pattern() + "|" + RESERVE.pattern())),
            Map.entry(Kind.REPORT, Pattern.compile("\\b(?:report|status|rapporto|stato|how(?:'s|’s| is| are)|come (?:va|vanno|sta|stanno))\\b")));

        /**
         * The action "start", an action that names a job, or "none" with the reason.
         * The fixed rules call no model, thus they report zero tokens.
         */
        @Override
        public Answer answer(String request, Context context) {
            String text = request.toLowerCase(Locale.ROOT);
            List<String> stations = new ArrayList<>();
            for (String station : context.stations()) {
                if (mention(text, station).find()) stations.add(station);
            }
            Action action;
            try {
                action = stations.size() < 2 ? jobAction(text, stations) : null;
                if (action == null) action = start(text, context);
            } catch (IllegalArgumentException missing) {
                action = new Action.None(missing.getMessage());
            }
            return new Answer(action, 0, 0);
        }

        /** The name of a station as a whole word of the text. */
        private static Matcher mention(String text, String station) {
            return Pattern.compile("\\b" + Pattern.quote(station.toLowerCase(Locale.ROOT)) + "\\b").matcher(text);
        }

        /**
         * The action about a job: the action of the first word that fits, with "robot N" and the one station of the text as the target.
         * Null if no word fits. A change also has the new quantity and the new reserve. A value that the text does not give is null.
         */
        private static Action.OnJob jobAction(String text, List<String> stations) {
            // "robot N" names the job. Its number is not a new value.
            Matcher robot = ROBOT.matcher(text);
            Integer robotId = robot.find() ? Integer.valueOf(robot.group(1)) : null;
            String rest = robot.reset().replaceFirst(" ");
            for (Map.Entry<Kind, Pattern> word : ACTION_WORDS) {
                if (!word.getValue().matcher(rest).find()) continue;
                Target target = new Target(robotId, stations.isEmpty() ? null : stations.get(0));
                boolean change = word.getKey() == Kind.CHANGE;
                return new Action.OnJob(word.getKey(), target, change ? number(QUANTITY, rest) : null, change ? number(RESERVE, rest) : null);
            }
            return null;
        }

        /** The first number after the word of the pattern, or null. */
        private static Integer number(Pattern word, String text) {
            Matcher matcher = word.matcher(text);
            return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
        }

        /** The action "start" with the order of the request. An IllegalArgumentException says what is missing. */
        private static Action.Start start(String text, Context known) {
            Matcher reserveMatch = RESERVE.matcher(text);
            int reserve = reserveMatch.find() ? Integer.parseInt(reserveMatch.group(1)) : 0;
            String rest = reserveMatch.reset().replaceFirst(" ");
            Matcher robotMatch = ROBOT.matcher(rest);
            Integer robot = robotMatch.find() ? Integer.valueOf(robotMatch.group(1)) : null;
            Matcher quantityMatch = NUMBER.matcher(robotMatch.reset().replaceFirst(" "));
            if (!quantityMatch.find()) throw new IllegalArgumentException("the quantity is missing");
            int quantity = Integer.parseInt(quantityMatch.group());

            String from = null, to = null;
            for (String name : known.stations()) {
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

            // No robot named: the only free robot. With no free robot and one robot, that robot: Uno proposes to replace its job (D42, point 10).
            if (robot == null) {
                List<Integer> free = known.robots().stream().filter(Context.Robot::free).map(Context.Robot::id).toList();
                if (free.isEmpty() && known.robots().size() == 1) {
                    robot = known.robots().get(0).id();
                } else if (free.size() == 1) {
                    robot = free.get(0);
                } else {
                    throw new IllegalArgumentException("you have " + free.size() + " free robots: write 'robot N'");
                }
            }

            String module = text.matches("(?s).*\\b(mantieni|mantenere|almeno|keep)\\b.*") ? MindRuntime.KEEP_STOCK : MindRuntime.TRANSPORT;
            return new Action.Start(new Order(module, robot, from, to, ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(item)), quantity, reserve));
        }
    }
}
