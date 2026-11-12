package io.github.g4solio.mindcraft;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.beta.messages.BetaJsonOutputFormat;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A provider of Uno that asks Claude through the official Anthropic SDK.
 * Claude gets the request of the player and the context: the names of the stations and the ids of the robots of that player.
 * Claude gives back one order as JSON, or one sentence that says what is missing.
 * The answer is only a proposal. Uno validates it as each other order, and the player must confirm it.
 */
final class ClaudeProvider implements Uno.Provider {
    static final String MODEL = "claude-opus-5-5";
    static final String KEY_VARIABLE = "ANTHROPIC_API_KEY";
    /** A limit on the cost of one request. The answer is small: this number is mostly room for the thinking. */
    private static final long MAX_TOKENS = 4096;
    private static final int MAX_PROBLEM_LENGTH = 200;

    private static final String SYSTEM = """
        You interpret requests for Uno, an assistant in a Minecraft mod. Uno directs robots that carry items between named stations.
        The player writes a request in any language. Change the request into one order.

        The fields of an order:
        - module: "transport" carries a fixed quantity one time. "keep_stock" keeps at least a quantity in the destination and continues to work.
        - robot: the id of a robot from the context. If the player names no robot and exactly one robot is free, use that robot.
        - from, to: the source station and the destination station. Write each name exactly as it is in the context. \
        The player can write a name with a different case, with an article, with other words around it or with a small error.
        - item: a Minecraft item id, for example "minecraft:coal". Translate the name of the item from the language of the player.
        - quantity: for transport, the number of items to carry. For keep_stock, the level to keep in the destination.
        - reserve: the number of items that must stay in the source station. 0 if the player gives no reserve.

        The context lists only the stations and the robots of this player. Use no other station and no other robot.
        If the request does not give a necessary value, or if it does not ask for such an order, do not guess. \
        Write in "problem" one short sentence in English that says what is missing. \
        Speak only about the request and about the stations and robots in the context: you know nothing else about the world. \
        Then fill the other fields with empty text and zeros.
        If the order is complete, "problem" is empty text.""";

    private static final String SCHEMA = """
        {"type": "object", "additionalProperties": false,
         "required": ["problem", "module", "robot", "from", "to", "item", "quantity", "reserve"],
         "properties": {
          "problem": {"type": "string"},
          "module": {"type": "string", "enum": ["transport", "keep_stock"]},
          "robot": {"type": "integer"},
          "from": {"type": "string"},
          "to": {"type": "string"},
          "item": {"type": "string"},
          "quantity": {"type": "integer"},
          "reserve": {"type": "integer"}}}""";

    private final AnthropicClient client;

    ClaudeProvider(AnthropicClient client) {
        this.client = client;
    }

    /** The SDK reads the key from the environment. The key is not in the repository, in the world or in a log. */
    static ClaudeProvider fromEnv() {
        return new ClaudeProvider(AnthropicOkHttpClient.builder().fromEnv().timeout(Duration.ofMillis(Uno.TIMEOUT_MS)).build());
    }

    @Override
    public String propose(String request, String context) {
        BetaJsonOutputFormat.Schema.Builder schema = BetaJsonOutputFormat.Schema.builder();
        Map<?, ?> fields = new Gson().fromJson(SCHEMA, Map.class);
        fields.forEach((key, value) -> schema.putAdditionalProperty((String) key, JsonValue.from(value)));
        MessageCreateParams params = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(MAX_TOKENS)
            // If the safety classifiers decline the request, the API tries it again on the model that Anthropic recommends.
            .addBeta("server-side-fallback-2026-07-01")
            .fallbacksDefault()
            .system(SYSTEM)
            // Thinking is always on for this model. A low effort keeps the answer fast: the player waits in the game.
            .outputConfig(BetaOutputConfig.builder()
                .effort(BetaOutputConfig.Effort.LOW)
                .format(BetaJsonOutputFormat.builder().schema(schema.build()).build())
                .build())
            .addUserMessage("Context: " + context + "\nRequest: " + request)
            .build();

        BetaMessage message;
        try {
            message = client.beta().messages().create(params);
        } catch (UnauthorizedException | PermissionDeniedException e) {
            throw new IllegalStateException("the API key is not valid or has no permission");
        } catch (RateLimitException e) {
            throw new IllegalStateException("the model service reached its request limit");
        } catch (AnthropicServiceException e) {
            throw new IllegalStateException("the model service answered with error " + e.statusCode());
        } catch (AnthropicIoException e) {
            throw new IllegalStateException("the model service is not reachable");
        }

        // A refusal or a cut answer has no usable content. Thus the stop reason comes before the content.
        BetaStopReason stop = message.stopReason().orElse(null);
        if (BetaStopReason.REFUSAL.equals(stop)) throw new IllegalStateException("the model declined the request");
        if (!BetaStopReason.END_TURN.equals(stop)) throw new IllegalStateException("the answer of the model is incomplete");
        String json = message.content().stream().flatMap(block -> block.text().stream()).map(text -> text.text()).collect(Collectors.joining());
        JsonElement problem = JsonParser.parseString(json).getAsJsonObject().get("problem");
        if (problem != null && !problem.getAsString().isBlank()) {
            String text = problem.getAsString().trim();
            throw new IllegalArgumentException(text.substring(0, Math.min(text.length(), MAX_PROBLEM_LENGTH)));
        }
        return json;
    }
}
