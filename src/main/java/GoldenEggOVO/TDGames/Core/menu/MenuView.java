package GoldenEggOVO.TDGames.Core.menu;

import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;

public record MenuView(Component title, List<Body> body, List<Input> inputs,
                       List<Button> buttons, Button exit, int columns, String page, long expiresAt) {
    public static final java.time.Duration LIFETIME = java.time.Duration.ofMinutes(5);

    public MenuView(Component title, List<Body> body, List<Input> inputs, List<Button> buttons, Button exit, int columns, String page) {
        this(title, body, inputs, buttons, exit, columns, page, System.currentTimeMillis() + MenuView.LIFETIME.toMillis());
    }
    public MenuView(Component title, List<Body> body, List<Input> inputs, List<Button> buttons, Button exit, int columns) {
        this(title, body, inputs, buttons, exit, columns, "actions");
    }
    public MenuView {
        Objects.requireNonNull(title);
        body = List.copyOf(body);
        inputs = List.copyOf(inputs);
        buttons = List.copyOf(buttons);
        Objects.requireNonNull(exit);
        if (columns < 1 || columns > 10) throw new IllegalArgumentException("Invalid columns");
        if (inputs.stream().map(Input::id).distinct().count() != inputs.size())
            throw new IllegalArgumentException("Duplicate input ID");
    }

    public record Body(Component text, int width) {
        public Body { Objects.requireNonNull(text); size(width); }
    }

    public record Input(String id, Component label, String value, int maxLength) {
        public Input {
            if (id == null || !id.matches("[a-z][a-z0-9_-]*")) throw new IllegalArgumentException("Invalid input ID");
            Objects.requireNonNull(label);
            Objects.requireNonNull(value);
            size(maxLength);
            if (value.length() > maxLength) throw new IllegalArgumentException("Input value is too long");
        }
    }

    public record Button(Component text, Component tooltip, int width, String action, String key) {
        public Button(Component text, Component tooltip, int width, String action) { this(text, tooltip, width, action, action); }
        public Button { Objects.requireNonNull(text); size(width); if (key == null) key = ""; }
    }

    private static void size(int value) {
        if (value < 1 || value > 1024) throw new IllegalArgumentException("Size must be 1-1024");
    }
}
