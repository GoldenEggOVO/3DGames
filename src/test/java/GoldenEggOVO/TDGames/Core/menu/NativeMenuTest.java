package GoldenEggOVO.TDGames.Core.menu;

import GoldenEggOVO.TDGames.SessionTestSupport;


import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NativeMenuTest {
    @Test void languageReloadClosesOnlyTheOriginalMenuOnThePlayerScheduler() {
        var plugin = SessionTestSupport.plugin();
        var player = mock(Player.class);
        var id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        var world = mock(World.class);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(world);
        when(plugin.getConfig()).thenReturn(new org.bukkit.configuration.file.YamlConfiguration());
        when(plugin.allowed(player)).thenReturn(true);
        var menus = new GameMenus(plugin);
        var queue = new ArrayList<Runnable>();
        var scheduler = plugin.scheduler();
        doAnswer(call -> { queue.add(call.getArgument(1)); return null; })
                .when(scheduler).execute(eq(player), any(Runnable.class));
        try (var bukkit = mockStatic(org.bukkit.Bukkit.class);
             var dialogs = mockStatic(GoldenEggOVO.TDGames.Core.platform.NativeDialogs.class)) {
            bukkit.when(() -> org.bukkit.Bukkit.getPlayer(id)).thenReturn(player);
            menus.show(player, "Old", "", List.of(), null);
            menus.languageChanged();
            dialogs.verifyNoInteractions();
            menus.show(player, "New", "", List.of(), null);
            queue.removeFirst().run();
            dialogs.verifyNoInteractions();
            assertTrue(menus.active(player));
            menus.languageChanged();
            queue.removeFirst().run();
            dialogs.verify(() -> GoldenEggOVO.TDGames.Core.platform.NativeDialogs.clear(player));
            assertFalse(menus.active(player));
        }
    }

    @Test void standaloneCatalogDoesNotOfferMissingServerMenu() throws Exception {
        var plugin=SessionTestSupport.plugin();when(plugin.getConfig()).thenReturn(new org.bukkit.configuration.file.YamlConfiguration());when(plugin.allowed(any())).thenReturn(true);
        var player=mock(Player.class);when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        var world=mock(World.class);when(world.getUID()).thenReturn(UUID.randomUUID());when(player.getWorld()).thenReturn(world);
        var menus=new GameMenus(plugin);menus.show(player,"Games","",List.of(),null);
        var field=GameMenus.class.getDeclaredField("sessions");field.setAccessible(true);
        @SuppressWarnings("unchecked") var sessions=(Map<UUID,GameMenus.Session>)field.get(menus);
        assertFalse(sessions.get(player.getUniqueId()).buttons().stream().anyMatch(b->b.id().equals("main")));
    }
    @SuppressWarnings("unchecked") private String action(GameMenus menus,Player p)throws Exception{var field=GameMenus.class.getDeclaredField("sessions");field.setAccessible(true);var sessions=(Map<UUID,GameMenus.Session>)field.get(menus);return "3dgames:"+sessions.get(p.getUniqueId()).token()+" 0";}
    @Test void callbackIsSingleUseAndBoundToPlayerAndWorld()throws Exception{
        var plugin=SessionTestSupport.plugin();when(plugin.getConfig()).thenReturn(new org.bukkit.configuration.file.YamlConfiguration());when(plugin.allowed(any())).thenReturn(true);var p=mock(Player.class);when(p.getUniqueId()).thenReturn(UUID.randomUUID());var world=mock(World.class);when(world.getUID()).thenReturn(UUID.randomUUID());when(p.getWorld()).thenReturn(world);
        var menus=new GameMenus(plugin);var invoked=new AtomicInteger();menus.show(p,"Menu","",List.of(new GameMenus.Button("Play",invoked::incrementAndGet)),null);String action=action(menus,p);
        var other=mock(Player.class);when(other.getUniqueId()).thenReturn(UUID.randomUUID());menus.handle(other,action);assertEquals(0,invoked.get());
        menus.handle(p,"3dgames:"+UUID.randomUUID()+" 0");assertEquals(0,invoked.get());
        var elsewhere=mock(World.class);when(elsewhere.getUID()).thenReturn(UUID.randomUUID());when(p.getWorld()).thenReturn(elsewhere);menus.handle(p,action);assertEquals(0,invoked.get());
        when(p.getWorld()).thenReturn(world);menus.handle(p,action);menus.handle(p,action);assertEquals(1,invoked.get());
    }
    @Test void authorizationIsRecheckedBeforeCallback()throws Exception{
        var plugin=SessionTestSupport.plugin();when(plugin.getConfig()).thenReturn(new org.bukkit.configuration.file.YamlConfiguration());when(plugin.allowed(any())).thenReturn(true);var p=mock(Player.class);when(p.getUniqueId()).thenReturn(UUID.randomUUID());var world=mock(World.class);when(world.getUID()).thenReturn(UUID.randomUUID());when(p.getWorld()).thenReturn(world);var menus=new GameMenus(plugin);var invoked=new AtomicInteger();menus.show(p,"Menu","",List.of(new GameMenus.Button("Play",invoked::incrementAndGet)),null);String action=action(menus,p);when(plugin.allowed(p)).thenReturn(false);menus.handle(p,action);assertEquals(0,invoked.get());
    }
}
