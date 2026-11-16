package GoldenEggOVO.TDGames.Core.menu;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public final class DialogMenus {
    private static final java.time.Duration CALLBACK_LIFETIME = java.time.Duration.ofHours(1);
    private DialogMenus() {}

    public static void open(Plugin plugin, Player player, MenuView view,
                              BiConsumer<String, Map<String, String>> handler) {
        open(plugin, player, view, handler, action -> {});
    }

    public static void open(Plugin plugin, Player player, MenuView view,
                              BiConsumer<String, Map<String, String>> handler, java.util.function.Consumer<String> expired) {
        if (!plugin.getConfig().getBoolean("menu-enabled", true)) {
            player.sendMessage(GoldenEggOVO.TDGames.Core.text.Language.component("menu.disabled"));
            return;
        }
        var world = player.getWorld().getUID();
        var used = new java.util.concurrent.atomic.AtomicBoolean();
        BiConsumer<String, Map<String, String>> dispatch = (action, values) -> {
            if (!plugin.isEnabled() || !used.compareAndSet(false, true)) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (plugin.isEnabled() && player.isOnline() && world.equals(player.getWorld().getUID())
                        && plugin.getConfig().getBoolean("menu-enabled", true)) {
                    if (System.currentTimeMillis() >= view.expiresAt()) {
                        if (action != null && !(view.exit().key().equals("close") && action.equals(view.exit().action()))) {
                            player.sendMessage(GoldenEggOVO.TDGames.Core.text.Language.component("menu.expired"));
                            expired.accept(action);
                        }
                    } else if (action != null) handler.accept(action, values);
                }
            });
        };
        var body = new java.util.ArrayList<DialogBody>();
        for (var header : view.buttons().stream().filter(b -> isHeader(view, b)).toList()) {
            var label = MenuLabels.caption(header)
                    .hoverEvent(header.tooltip() == null ? header.text() : header.tooltip())
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.callback(audience -> {
                if (audience instanceof Player actor && actor.getUniqueId().equals(player.getUniqueId()))
                    dispatch.accept(header.action(), Map.of());
            }, ClickCallback.Options.builder().uses(1).lifetime(CALLBACK_LIFETIME).build()));
            body.add(DialogBody.plainMessage(label, 300));
        }
        body.addAll(view.body().stream()
                .filter(b -> !GoldenEggOVO.TDGames.Core.text.MessageText.plain(b.text()).isBlank())
                .map(b -> DialogBody.plainMessage(b.text(), Math.min(300, b.width()))).toList());
        var inputs = view.inputs().stream().map(i -> DialogInput.text(i.id(), 300,
                i.label(), true, i.value(), i.maxLength(), null)).toList();
        var choices = view.buttons().stream().filter(b -> !isHeader(view, b)).toList();
        var buttons = choices.stream().map(b -> actionButton(player, b, view, dispatch,
                Math.min(view.columns() > 1 ? 144 : 300, b.width()))).toList();
        var base = DialogBase.create(view.title(), null, true, false,
                DialogBase.DialogAfterAction.CLOSE, body, inputs);
        DialogType type = buttons.isEmpty()
                ? DialogType.notice(actionButton(player, view.exit(), view, dispatch, Math.min(300, view.exit().width())))
                : DialogType.multiAction(buttons, actionButton(player, view.exit(), view, dispatch, Math.min(300, view.exit().width())), view.columns());
        player.showDialog(Dialog.create(factory -> factory.empty().base(base).type(type)));
    }

    private static boolean isHeader(MenuView view, MenuView.Button button) {
        return view.page().equals("catalog") && java.util.Set.of("settings", "rooms", "resume").contains(button.key())
                || view.page().equals("tabletop") && button.key().equals("solo-resume");
    }

    private static ActionButton actionButton(Player player, MenuView.Button button, MenuView view,
                                        BiConsumer<String, Map<String, String>> dispatch, int width) {
        DialogAction action = button.action() == null ? null : DialogAction.customClick((response, audience) -> {
            if (!(audience instanceof Player actor) || !actor.getUniqueId().equals(player.getUniqueId())) return;
            Map<String, String> values = new HashMap<>();
            for (var input : view.inputs()) {
                String value = response.getText(input.id());
                if (value != null) {
                    if (value.length() > input.maxLength()) return;
                    values.put(input.id(), value);
                }
            }
            dispatch.accept(button.action(), Map.copyOf(values));
        }, ClickCallback.Options.builder().uses(1).lifetime(CALLBACK_LIFETIME).build());
        return ActionButton.create(MenuLabels.caption(button), button.tooltip(), width, action);
    }
}
