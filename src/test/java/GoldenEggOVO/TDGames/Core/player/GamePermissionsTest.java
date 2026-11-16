package GoldenEggOVO.TDGames.Core.player;

import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.permissions.PermissibleBase;
import org.bukkit.permissions.ServerOperator;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class GamePermissionsTest {
    private Player player;
    private ServerOperator operator;
    private PermissibleBase permissions;
    private Plugin plugin;

    @BeforeEach void setup() {
        var server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        var manager = server.getPluginManager();
        manager.addPermission(new Permission("3dgames.use", PermissionDefault.TRUE));
        manager.addPermission(new Permission("3dgames.admin", PermissionDefault.OP));
        operator = mock(ServerOperator.class);
        permissions = new PermissibleBase(operator);
        player = mock(Player.class);
        when(player.hasPermission(anyString())).thenAnswer(call -> permissions.hasPermission(call.getArgument(0, String.class)));
    }

    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @Test void usePermissionTracksAttachmentChanges() {
        assertTrue(GamePermissions.use(player));
        var attachment = permissions.addAttachment(plugin, "3dgames.use", false);
        assertFalse(GamePermissions.use(player));
        attachment.setPermission("3dgames.use", true);
        assertTrue(GamePermissions.use(player));
    }

    @Test void explicitAdminDenialOverridesOperatorDefault() {
        when(operator.isOp()).thenReturn(true);
        permissions.recalculatePermissions();
        assertTrue(GamePermissions.admin(player));
        permissions.addAttachment(plugin, "3dgames.admin", false);
        assertFalse(GamePermissions.admin(player));
    }

    @Test void nonOperatorCanReceiveExplicitAdminPermission() {
        assertFalse(GamePermissions.admin(player));
        permissions.addAttachment(plugin, "3dgames.admin", true);
        assertTrue(GamePermissions.admin(player));
    }

    @Test void defaultsStillApplyWithoutAttachments() {
        assertTrue(GamePermissions.use(player));
        assertFalse(GamePermissions.admin(player));
        when(operator.isOp()).thenReturn(true);
        permissions.recalculatePermissions();
        assertTrue(GamePermissions.admin(player));
    }
}
