package io.github.g4solio.mindcraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
 * It changes a text request into a proposed order. It starts the order only when the player confirms.
 * It reports on jobs with data from the runtime only.
 * <p>
 * Uno has no authority of its own. Each order goes through the ordinary validation of the runtime.
 * Uno speaks English to the player.
 */
// ponytail: English text in the code. Use translation keys when a second language is necessary.
public final class Uno {
    /**
     * Changes a request into an order. An implementation can call a language model.
     * The context is JSON: the stations and robots of the player. The answer is JSON:
     * {"module": "keep_stock" or "transport", "robot": 0, "from": "...", "to": "...", "item": "minecraft:coal",
     * "quantity": 64, "reserve": 32}.
     * An IllegalArgumentException says that the request has no complete order. Its message is the reason, for the player.
     * Any other exception is a failure of the provider.
     */
    public interface Provider {
        String propose(String request, String context) throws Exception;
    }

    /** The provider of the /uno command and of the screen. A different provider can replace it. See MindCraft.chooseProvider. */
    static volatile Provider provider = new RuleProvider();
    static final long TIMEOUT_MS = 20_000;
    private static final int SILENT_TICKS = 300;
    /** Player to the time of a request that has no answer yet, in milliseconds. */
    private static final Map<UUID, Long> ASKED = new ConcurrentHashMap<>();

    /**
     * Proposals that wait for a confirmation. They are not saved: a proposal is not a decision.
     * After a server restart Uno has no proposal, and says so.
     */
    private static final Map<UUID, Order> PROPOSALS = new ConcurrentHashMap<>();
    /** The last thing that Uno said to each player on a screen. Not saved. */
    private static final Map<UUID, String> SAID = new ConcurrentHashMap<>();

    private Uno() {
    }

    static void say(Actor actor, String text) {
        SAID.put(actor.id(), text);
    }

    static String said(Actor actor) {
        return SAID.getOrDefault(actor.id(), "Tell me what you need.");
    }

    static boolean hasProposal(Actor actor) {
        return PROPOSALS.containsKey(actor.id());
    }

    /**
     * One request at a time for each player: a provider can cost money for each request.
     * A provider always answers before the time limit. Thus an older entry is of a server that stopped, and does not count.
     */
    public static void ask(MinecraftServer server, Actor actor, String request, Consumer<String> reply) {
        long now = System.currentTimeMillis();
        Long asked = ASKED.get(actor.id());
        if (asked != null && now - asked < TIMEOUT_MS) {
            reply.accept("I am still working on your previous request.");
            return;
        }
        ASKED.put(actor.id(), now);
        ask(server, actor, request, provider, TIMEOUT_MS, answer -> {
            ASKED.remove(actor.id());
            reply.accept(answer);
        });
    }

    /** The provider runs on a different thread. The server and the jobs in progress do not wait for it. */
    public static void ask(MinecraftServer server, Actor actor, String request, Provider provider, long timeoutMs, Consumer<String> reply) {
        String context = context(MindRuntime.get(server), actor);
        CompletableFuture
            .supplyAsync(() -> {
                try {
                    return provider.propose(request, context);
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            })
            .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .whenComplete((json, error) -> server.execute(() -> reply.accept(error == null
                ? propose(MindRuntime.get(server), actor, json)
                : failure(error))));
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

    private static String propose(MindRuntime runtime, Actor actor, String json) {
        Order order;
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            order = new Order(o.get("module").getAsString(), o.get("robot").getAsInt(), o.get("from").getAsString(),
                o.get("to").getAsString(), ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(o.get("item").getAsString())),
                o.get("quantity").getAsInt(), o.has("reserve") ? o.get("reserve").getAsInt() : 0);
        } catch (RuntimeException e) {
            return "I did not get a valid configuration. I created no proposal.";
        }
        try {
            runtime.check(actor, order);
        } catch (Refusal refusal) {
            return "I cannot propose this job: " + refusal.getMessage();
        }
        PROPOSALS.put(actor.id(), order);
        String item = MindRuntime.itemName(order.item());
        String what = order.module().equals(MindRuntime.KEEP_STOCK)
            ? "keep at least " + order.quantity() + " x " + item + " in '" + order.to() + "', taken from '" + order.from() + "'"
            : "carry " + order.quantity() + " x " + item + " from '" + order.from() + "' to '" + order.to() + "'";
        return "Proposal: " + what + ". Reserve to leave in '" + order.from() + "': " + order.reserve()
            + ". Robot #" + order.robot() + ". I have done nothing yet. Confirm with /uno confirm.";
    }

    /** Starts the proposed order. The runtime validates it again: the world can be different now. */
    public static String confirm(MinecraftServer server, Actor actor) {
        Order order = PROPOSALS.remove(actor.id());
        if (order == null) return "I have no pending proposal. A proposal that is not confirmed is not saved.";
        try {
            Job job = MindRuntime.get(server).start(actor, order);
            return "Job " + job.id + " started on robot #" + order.robot() + ".";
        } catch (Refusal refusal) {
            return "I cannot start the job: " + refusal.getMessage();
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
        if (job.carried > 0) text.append("The record says that I have ").append(job.carried).append(" on board. ");
        boolean live = RobotApi.live(robotId) != null;
        text.append(switch (job.state) {
            case RUNNING -> live ? "I am working. " : "The job is open, but the robot is stopped. ";
            case SATISFIED -> "At the last observation the stock was at the requested level. ";
            case DONE -> "The job is complete. ";
            case PAUSED -> "I am paused at your request. ";
            case CANCELLED -> "You cancelled the job. ";
            case WAITING -> job.carried > 0
                ? "The destination is full. I am waiting for free space. "
                : job.fromSeen + " remain in " + from + ": that is the reserve you asked me to keep. To reach "
                    + job.quantity + " in " + to + " I need more materials. ";
            case BLOCKED -> "I am stopped and I need your help. Recorded reason: " + job.reason + ". ";
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

    static String seen(int count, long at, long now) {
        return count == MindRuntime.UNKNOWN ? "never observed" : count + " items, " + (now - at) / 20 + " seconds ago";
    }

    /** A request without a complete order is not a failure of the provider. The player must see the difference. */
    private static String failure(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        String reason = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        if (cause instanceof IllegalArgumentException) {
            return "I cannot propose a job: " + reason + (reason.endsWith(".") ? "" : ".");
        }
        return "I cannot interpret the request now (" + reason + "). The jobs in progress continue.";
    }

    /**
     * A provider without a language model. It gives the same answer for the same request, thus it also serves the checks.
     * It reads the kind of job, two numbers, an item and the two station names of the player.
     */
    // ponytail: a few fixed word patterns in Italian and English. A language model replaces this for free text.
    static final class RuleProvider implements Provider {
        private static final Pattern RESERVE = Pattern.compile("(?:lascia|riserva|leave|reserve)\\D*(\\d+)");
        private static final Pattern NUMBER = Pattern.compile("\\b\\d+\\b");
        private static final Pattern ROBOT = Pattern.compile("robot\\D{0,2}(\\d+)");
        private static final Pattern SOURCE_WORD = Pattern.compile("\\b(?:da|dal|dalla|dallo|from)\\s+(?:il\\s+|la\\s+|the\\s+)?$");
        private static final Map<String, String> ITEM_WORDS = Map.of("carbone", "minecraft:coal");

        @Override
        public String propose(String request, String context) {
            JsonObject known = JsonParser.parseString(context).getAsJsonObject();
            String text = request.toLowerCase(Locale.ROOT);

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
                Matcher mention = Pattern.compile("\\b" + Pattern.quote(name.toLowerCase(Locale.ROOT)) + "\\b").matcher(text);
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
            order.addProperty("module", text.matches("(?s).*\\b(mantieni|mantenere|almeno|keep)\\b.*") ? MindRuntime.KEEP_STOCK : MindRuntime.TRANSPORT);
            order.addProperty("robot", robot);
            order.addProperty("from", from);
            order.addProperty("to", to);
            order.addProperty("item", item);
            order.addProperty("quantity", quantity);
            order.addProperty("reserve", reserve);
            return order.toString();
        }
    }
}
