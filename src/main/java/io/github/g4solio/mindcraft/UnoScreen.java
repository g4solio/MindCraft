package io.github.g4solio.mindcraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Draws a view that the server built. See UnoView. This class has no game logic:
 * it shows the widgets, keeps the text that the player types, and sends actions.
 * Client only. Only Net refers to this class, in code that runs on a client.
 * <p>
 * A new view arrives each second. Usually only its text changes, for example the age of an observation.
 * Then the fields and buttons stay as they are: the player can continue to type.
 * The screen makes new fields and buttons only when the shape of the view changes.
 */
public class UnoScreen extends Screen {
    private static final int WIDTH = 300, PAD = 8, TOP = 24, ROW = 22;
    private static final int PANEL = 0xFFC6C6C6, HEADING = 0xFF202020, NORMAL = 0xFF3F3F3F, MUTED = 0xFF5A5A5A, VOICE = 0xFF1D4E89;

    private String json = "";
    private String shape = "";
    private JsonObject view;
    private final Map<String, EditBox> boxes = new HashMap<>();
    private final Map<String, Button> buttons = new HashMap<>();
    /** The text that the player changed and did not send. New fields must not lose it. */
    private final Map<String, String> typed = new HashMap<>();
    private final List<Consumer<GuiGraphics>> drawing = new ArrayList<>();
    private int contentHeight, scroll, age;

    private UnoScreen() {
        super(Component.literal("Uno"));
    }

    /** Opens the screen, or gives a new view to the open screen. */
    static void show(String json) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof UnoScreen)) minecraft.setScreen(new UnoScreen());
        if (minecraft.screen instanceof UnoScreen screen && !json.equals(screen.json)) {
            screen.json = json;
            screen.view = JsonParser.parseString(json).getAsJsonObject();
            String shape = shape(screen.view);
            if (shape.equals(screen.shape)) {
                screen.layout();
            } else {
                screen.shape = shape;
                screen.rebuild();
            }
        }
    }

    /** The parts of a view that need a field or a button: types, field names, number values, button actions. */
    private static String shape(JsonObject view) {
        StringBuilder shape = new StringBuilder();
        for (JsonElement element : view.getAsJsonArray("widgets")) {
            JsonObject widget = element.getAsJsonObject();
            String type = widget.get("type").getAsString();
            shape.append(type);
            if (type.equals("number")) shape.append(widget.get("id")).append(widget.get("value"));
            if (type.equals("input")) shape.append(widget.get("id"));
            if (type.equals("buttons")) shape.append(widget.get("buttons"));
            shape.append('|');
        }
        return shape.toString();
    }

    /** Makes new fields and buttons. Minecraft removes the focus first, thus this method puts it back. */
    private void rebuild() {
        String focused = null;
        int cursor = 0;
        for (Map.Entry<String, EditBox> box : boxes.entrySet()) {
            if (box.getValue().isFocused()) {
                focused = box.getKey();
                cursor = box.getValue().getCursorPosition();
            }
        }
        rebuildWidgets();
        EditBox box = focused == null ? null : boxes.get(focused);
        if (box != null) {
            setFocused(box);
            box.setCursorPosition(Math.min(cursor, box.getValue().length()));
            box.setHighlightPos(box.getCursorPosition());
        }
    }

    @Override
    protected void init() {
        boxes.clear();
        buttons.clear();
        layout();
    }

    /** Puts each widget of the view in its place. Makes a field or a button only if it does not exist. */
    private void layout() {
        drawing.clear();
        if (view == null) return;
        int x = (width - WIDTH) / 2, y = TOP - scroll;
        for (JsonElement element : view.getAsJsonArray("widgets")) {
            JsonObject widget = element.getAsJsonObject();
            int top = y;
            switch (widget.get("type").getAsString()) {
                case "text" -> {
                    Component text = Component.literal(widget.get("text").getAsString());
                    String style = widget.get("style").getAsString();
                    int colour = switch (style) {
                        case "heading" -> HEADING;
                        case "muted" -> MUTED;
                        case "uno" -> VOICE;
                        default -> NORMAL;
                    };
                    int gap = style.equals("heading") ? 6 : 0;
                    drawing.add(graphics -> graphics.drawWordWrap(font, text, x, top + gap, WIDTH, colour));
                    y += gap + font.wordWrapHeight(text, WIDTH) + 4;
                }
                case "number" -> {
                    String label = widget.get("label").getAsString();
                    drawing.add(graphics -> graphics.drawString(font, label, x, top + 5, NORMAL, false));
                    box(widget.get("id").getAsString(), widget.get("value").getAsString(), 60, 6, "").setPosition(x + WIDTH - 60, top);
                    y += ROW;
                }
                case "input" -> {
                    box(widget.get("id").getAsString(), "", WIDTH, 256, widget.get("hint").getAsString()).setPosition(x, top);
                    y += ROW;
                }
                case "bar" -> {
                    int value = widget.get("value").getAsInt(), max = Math.max(1, widget.get("max").getAsInt());
                    String label = widget.get("label").getAsString(), note = widget.get("note").getAsString();
                    String amount = value + "/" + max + (note.isEmpty() ? "" : " · " + note);
                    int barX = x + 90, barWidth = 80;
                    drawing.add(graphics -> {
                        graphics.drawString(font, font.plainSubstrByWidth(label, 86), x, top + 2, NORMAL, false);
                        graphics.fill(barX, top + 1, barX + barWidth, top + 10, 0xFF373737);
                        graphics.fill(barX, top + 1, barX + Math.min(barWidth, barWidth * value / max), top + 10, value >= max ? 0xFF3E8E1F : 0xFFD08A16);
                        graphics.drawString(font, amount, barX + barWidth + 6, top + 2, NORMAL, false);
                    });
                    y += 14;
                }
                case "buttons" -> {
                    JsonArray row = widget.getAsJsonArray("buttons");
                    int buttonWidth = (WIDTH - 4 * (row.size() - 1)) / row.size();
                    for (int i = 0; i < row.size(); i++) {
                        JsonObject description = row.get(i).getAsJsonObject();
                        String action = description.get("action").getAsString();
                        Button button = buttons.get(action);
                        if (button == null) {
                            button = addRenderableWidget(Button.builder(Component.literal(description.get("label").getAsString()),
                                pressed -> send(action)).bounds(0, 0, buttonWidth, 20).build());
                            buttons.put(action, button);
                        }
                        button.setPosition(x + i * (buttonWidth + 4), top);
                    }
                    y += ROW + 2;
                }
                default -> {
                }
            }
        }
        contentHeight = y + scroll - TOP;
    }

    /**
     * The text field with this name. A new field shows the text of the player if the player changed it,
     * else the value of the server. The length limit must be set before the text, or the field cuts the text.
     */
    private EditBox box(String id, String serverValue, int boxWidth, int maxLength, String hint) {
        EditBox box = boxes.get(id);
        if (box != null) return box;
        box = new EditBox(font, 0, 0, boxWidth, 18, Component.literal(id));
        box.setMaxLength(maxLength);
        if (hint.isEmpty()) box.setFilter(text -> text.matches("\\d*"));
        else box.setHint(Component.literal(hint));
        box.setValue(typed.getOrDefault(id, serverValue));
        box.setResponder(text -> {
            if (text.equals(serverValue)) typed.remove(id);
            else typed.put(id, text);
        });
        boxes.put(id, box);
        return addRenderableWidget(box);
    }

    private void send(String action) {
        JsonObject values = new JsonObject();
        boxes.forEach((id, box) -> values.addProperty(id, box.getValue()));
        Net.CHANNEL.sendToServer(new Net.Action(action, values.toString()));
        // The sent text is not a draft now.
        String[] part = action.split(":");
        if (part[0].equals("ask") && boxes.containsKey("request")) boxes.get("request").setValue("");
        if (part[0].equals("apply")) {
            typed.remove("quantity:" + part[1]);
            typed.remove("reserve:" + part[1]);
        }
    }

    @Override
    public void tick() {
        boxes.values().forEach(EditBox::tick);
        // The screen asks for a new view each second. The server keeps no list of open screens.
        if (++age % 20 == 0) Net.CHANNEL.sendToServer(new Net.Action("refresh", "{}"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int x = (width - WIDTH) / 2, top = TOP - scroll - PAD, bottom = TOP - scroll + contentHeight + PAD;
        graphics.fill(x - PAD - 2, top - 2, x + WIDTH + PAD + 2, bottom + 2, 0xFF000000);
        graphics.fill(x - PAD, top, x + WIDTH + PAD, bottom, PANEL);
        drawing.forEach(draw -> draw.accept(graphics));
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int limit = Math.max(0, contentHeight + TOP + PAD - height);
        scroll = Math.max(0, Math.min(limit, scroll - (int) (delta * 16)));
        layout();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        // Enter in the request field sends the request.
        if ((key == 257 || key == 335) && boxes.containsKey("request") && boxes.get("request").isFocused()) {
            send("ask");
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    /** The game must continue while the screen is open: the robots work and the server answers. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
