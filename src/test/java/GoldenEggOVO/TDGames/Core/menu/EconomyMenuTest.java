package GoldenEggOVO.TDGames.Core.menu;

import GoldenEggOVO.TDGames.Multiplayer.menu.RoomEconomyText;

import GoldenEggOVO.TDGames.SessionTestSupport;

import GoldenEggOVO.TDGames.Multiplayer.integration.OptionalIntegrations;
import GoldenEggOVO.TDGames.Core.economy.EconomyAccess;
import GoldenEggOVO.TDGames.Multiplayer.economy.RoomPaymentLedger;
import GoldenEggOVO.TDGames.Shared.session.GameSession;
import GoldenEggOVO.TDGames.Core.text.Language;
import GoldenEggOVO.TDGames.Core.text.MessageText;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EconomyMenuTest {
    @TempDir Path directory;

    MenuFlowTest.Fixture fixture() throws Exception {
        var fixture = new MenuFlowTest.Fixture();
        var server = mock(Server.class);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        when(fixture.plugin.getServer()).thenReturn(server);
        when(fixture.plugin.getDataFolder()).thenReturn(directory.toFile());
        fixture.plugin.getConfig().set("economy.enabled", true);
        fixture.plugin.getConfig().set("economy.entry-fee", 10.0);
        fixture.plugin.getConfig().set("economy.winner-reward", 25.0);
        SessionTestSupport.module(fixture.plugin, "integrations", new OptionalIntegrations(fixture.plugin));
        EconomyAccess bank = mock(EconomyAccess.class);
        when(bank.available()).thenReturn(true);
        when(bank.format(anyDouble())).thenAnswer(call -> Double.toString(call.getArgument(0)));
        GoldenEggOVO.TDGames.GameRulesTest.set(fixture.plugin.integrations(), "economy", bank);
        return fixture;
    }

    @ParameterizedTest
    @EnumSource(value = GameSession.Phase.class, names = {"LOBBY", "FINISHED"})
    void preparationAndRematchMenusShowTheNextGameFees(GameSession.Phase phase) throws Exception {
        var fixture = fixture();
        var room = fixture.addRoom(0);
        room.join(fixture.player.getUniqueId(), "Host");
        room.join(UUID.randomUUID(), "Guest");
        SessionTestSupport.set(room, "phase", phase);
        fixture.menus.room(fixture.player, room);
        String fees = MessageText.plain(Language.component("room.economy.fees", "fee", "10.0", "reward", "25.0"));
        assertTrue(MessageText.plain(fixture.description).contains(fees));
        assertTrue(fixture.buttons.stream().anyMatch(button -> button.id().equals(phase == GameSession.Phase.LOBBY ? "ready" : "rematch")));
    }

    @Test
    void stakeButtonsCaptureDisplayedPricesAndHideBotsAndIncompleteRematches() throws Exception {
        var fixture = fixture();
        var room = fixture.addRoom(0);
        room.join(fixture.player.getUniqueId(), "Host");
        SessionTestSupport.set(room, "stake", 10.0);
        fixture.menus.room(fixture.player, room);
        var ready = fixture.buttons.stream().filter(button -> button.id().equals("ready")).findFirst().orElseThrow();
        assertTrue(fixture.buttons.stream().anyMatch(button -> button.id().equals("stake")));
        assertFalse(fixture.buttons.stream().anyMatch(button -> button.id().equals("bots")));
        SessionTestSupport.set(room, "stake", 20.0);
        ready.action().run();
        verify(fixture.plugin.games()).ready(fixture.player, room, 10.0);
        SessionTestSupport.set(room, "phase", GameSession.Phase.FINISHED);
        SessionTestSupport.mutableDeparted(room).add(UUID.randomUUID());
        fixture.menus.room(fixture.player, room);
        assertFalse(fixture.buttons.stream().anyMatch(button -> button.id().equals("rematch") || button.id().equals("stake")));
    }

    @Test
    void botMenuShowsZeroAmount() throws Exception {
        var fixture = fixture();
        var room = fixture.addRoom(0);
        room.join(fixture.player.getUniqueId(), "Host");
        SessionTestSupport.mutableSeats(room).add(new GameSession.Seat(UUID.randomUUID(), "Bot", true));
        fixture.menus.room(fixture.player, room);
        assertTrue(MessageText.plain(fixture.description).contains(MessageText.plain(Language.component("room.economy.bots"))));
    }

    @Test
    void disabledEconomyLeavesTheRoomMenuUnchanged() throws Exception {
        var fixture = fixture();
        var room = fixture.addRoom(0);
        room.join(fixture.player.getUniqueId(), "Host");
        fixture.menus.room(fixture.player, room);
        String paid = MessageText.plain(fixture.description);
        fixture.plugin.getConfig().set("economy.enabled", false);
        fixture.menus.room(fixture.player, room);
        String free = MessageText.plain(fixture.description);
        assertNotEquals(paid, free);
        assertTrue(new RoomEconomyText(fixture.plugin).fees(room).equals(net.kyori.adventure.text.Component.empty()));
    }

    @ParameterizedTest
    @EnumSource(value = GameSession.Phase.class, names = {"LOBBY", "FINISHED"})
    void readyButtonsShowEntryFeesBeforeCharging(GameSession.Phase phase) throws Exception {
        var fixture = fixture();
        var room = fixture.addRoom(0);
        room.join(fixture.player.getUniqueId(), "Host");
        SessionTestSupport.set(room, "phase", phase);
        fixture.menus.room(fixture.player, room);
        var ready = fixture.buttons.stream().filter(button -> button.id().equals(phase == GameSession.Phase.LOBBY ? "ready" : "rematch")).findFirst().orElseThrow();
        assertTrue(ready.label().contains("Entry 10.0"));
        assertTrue(MessageText.plain(fixture.description).contains("only when the game starts"));
        assertFalse(java.nio.file.Files.exists(directory.resolve("economy.json")));
    }

    @Test
    void roomBrowserShowsPricesAndBotModeWithoutChangingJoinabilityOrder() throws Exception {
        var fixture = fixture();
        var finished = fixture.addRoom(0);
        SessionTestSupport.set(finished, "phase", GameSession.Phase.FINISHED);
        var full = fixture.addRoom(1);
        full.join(UUID.randomUUID(), "One");
        full.join(UUID.randomUUID(), "Two");
        var paid = fixture.addRoom(2);
        var practice = fixture.addRoom(3);
        SessionTestSupport.mutableSeats(practice).add(new GameSession.Seat(UUID.randomUUID(), "Bot", true));
        fixture.menus.rooms(fixture.player);
        var rows = fixture.buttons.stream().filter(button -> button.id().startsWith("room-")).toList();
        assertEquals("room-" + paid.id, rows.get(0).id());
        assertEquals("room-" + practice.id, rows.get(1).id());
        assertTrue(rows.get(0).label().contains("Entry 10.0 / Win 25.0"));
        assertTrue(rows.get(1).label().contains("Bots · 0"));
        assertTrue(rows.get(0).label().contains("0/2"));
        assertTrue(MessageText.plain(rows.get(0).tooltip()).contains("only when the game starts"));
    }

    @Test
    void roomBrowserHidesAmountsWhenEconomyIsDisabled() throws Exception {
        var fixture = fixture();
        fixture.plugin.getConfig().set("economy.enabled", false);
        var normal = fixture.addRoom(0);
        var practice = fixture.addRoom(1);
        SessionTestSupport.mutableSeats(practice).add(new GameSession.Seat(UUID.randomUUID(), "Bot", true));
        fixture.menus.rooms(fixture.player);
        var rows = fixture.buttons.stream().filter(button -> button.id().startsWith("room-")).toList();
        assertEquals(2, rows.size());
        for (var row : rows) {
            assertFalse(row.label().contains("Amount 0"), row.label());
            assertFalse(row.label().contains("Bots · 0"), row.label());
            assertFalse(row.label().endsWith(" · "), row.label());
            assertFalse(MessageText.plain(row.tooltip()).endsWith("\n"));
        }
        assertEquals(Component.empty(), new RoomEconomyText(fixture.plugin).priceBadge(normal));
    }

    @Test
    void roomBrowserStillShowsZeroWhenEconomyIsEnabled() throws Exception {
        var fixture = fixture();
        fixture.plugin.getConfig().set("economy.entry-fee", 0);
        fixture.plugin.getConfig().set("economy.winner-reward", 0);
        var room = fixture.addRoom(0);
        assertEquals(MessageText.plain(Language.component("room.economy.zero")),
                MessageText.plain(new RoomEconomyText(fixture.plugin).priceBadge(room)));
    }

    @Test
    void roomBrowserRetainsExistingPaidTermsAndStorageWarningsWhenEconomyIsDisabled() throws Exception {
        var fixture = fixture();
        var room = paid(fixture, EconomyAccess.Transfer.SUCCESS, false);
        fixture.plugin.getConfig().set("economy.enabled", false);
        var text = new RoomEconomyText(fixture.plugin);
        String active = MessageText.plain(text.priceBadge(room));
        assertTrue(active.contains("10") && active.contains("25"), active);
        SessionTestSupport.set(room, "phase", GameSession.Phase.PAUSED);
        SessionTestSupport.set(room, "result", "error.economy.storage");
        assertEquals(MessageText.plain(Language.component("error.economy.storage")), MessageText.plain(text.priceBadge(room)));
    }

    GameSession paid(MenuFlowTest.Fixture fixture, EconomyAccess.Transfer rewardResult, boolean finished) throws Exception {
        var room = fixture.addRoom(0);
        UUID host = fixture.player.getUniqueId();
        room.join(host, "Host");
        room.join(UUID.randomUUID(), "Guest");
        EconomyAccess bank = new EconomyAccess() {
            public boolean available() { return true; }
            public java.util.OptionalDouble balance(UUID player) { return java.util.OptionalDouble.of(100); }
            public String format(double amount) { return Double.toString(amount); }
            public Transfer withdraw(UUID player, double amount) { return Transfer.SUCCESS; }
            public Transfer deposit(UUID player, double amount) { return rewardResult; }
        };
        var ledger = new RoomPaymentLedger(directory.resolve("economy.json"), bank);
        ledger.begin(room, true, 10, 25);
        SessionTestSupport.set(room, "phase", GameSession.Phase.PLAYING);
        if (finished) {
            ledger.finish(room, "winner:0");
            SessionTestSupport.set(room, "phase", GameSession.Phase.FINISHED);
            SessionTestSupport.set(room, "result", "winner:0");
        }
        SessionTestSupport.module(fixture.plugin, "integrations", new OptionalIntegrations(fixture.plugin));
        fixture.plugin.getConfig().set("economy.entry-fee", 99.0);
        fixture.plugin.getConfig().set("economy.winner-reward", 200.0);
        return room;
    }

    @Test
    void finishedMenuSeparatesCapturedSettlementFromNextGamePrices() throws Exception {
        var fixture = fixture();
        var room = paid(fixture, EconomyAccess.Transfer.SUCCESS, true);
        fixture.menus.room(fixture.player, room);
        String text = MessageText.plain(fixture.description);
        assertTrue(text.contains("Entry paid: -10.0"));
        assertTrue(text.contains("Reward received: +25.0"));
        assertTrue(text.contains("Confirmed net: +15.0"));
        assertTrue(text.contains("Next game"));
        assertTrue(text.contains("99.0"));
        assertTrue(text.contains("200.0"));
        fixture.menus.confirmLeave(fixture.player);
        assertTrue(MessageText.plain(fixture.description).contains("does not reverse settled payments"));
    }

    @Test
    void economyTextReadsCurrentSettingsWithoutChangingPayments() throws Exception {
        var fixture = fixture();
        var room = paid(fixture, EconomyAccess.Transfer.REJECTED, true);
        var text = new RoomEconomyText(fixture.plugin);
        var file = directory.resolve("economy.json");
        byte[] before = java.nio.file.Files.readAllBytes(file);
        String settlement = MessageText.plain(text.settlement(room, fixture.player.getUniqueId()));
        text.priceBadge(room);
        text.readyLabel(room, Component.text("Ready"));
        text.balanceHint(room, fixture.player.getUniqueId());
        text.leaveDetails(room);
        fixture.plugin.getConfig().set("economy.entry-fee", 12.0);
        fixture.plugin.getConfig().set("economy.winner-reward", 30.0);
        String fees = MessageText.plain(text.fees(room));
        assertTrue(fees.contains("12.0") && fees.contains("30.0"));
        assertEquals(settlement, MessageText.plain(text.settlement(room, fixture.player.getUniqueId())));
        assertArrayEquals(before, java.nio.file.Files.readAllBytes(file));
    }

    @Test
    void pendingRewardsAreNotAdvertisedAsReceivedAndCanBeRefreshed() throws Exception {
        var fixture = fixture();
        var room = paid(fixture, EconomyAccess.Transfer.REJECTED, true);
        fixture.menus.room(fixture.player, room);
        String text = MessageText.plain(fixture.description);
        assertTrue(text.contains("Reward received: +0.0"));
        assertTrue(text.contains("Reward pending: 25.0"));
        assertTrue(text.contains("Confirmed net: -10.0"));
        fixture.buttons.stream().filter(button -> button.id().equals("refresh")).findFirst().orElseThrow().action().run();
        assertTrue(MessageText.plain(fixture.description).contains("Reward pending: 25.0"));
    }

    @Test
    void activePaidLeaveExplainsWholeTableCancellationAndRefunds() throws Exception {
        var fixture = fixture();
        var room = paid(fixture, EconomyAccess.Transfer.SUCCESS, false);
        fixture.menus.confirmLeave(fixture.player);
        String text = MessageText.plain(fixture.description);
        assertTrue(text.contains("ends the game for everyone"));
        assertTrue(text.contains("Confirmed entry charges are refunded to every charged player"));
        assertFalse(text.contains("free game"));
        SessionTestSupport.set(room, "phase", GameSession.Phase.LOBBY);
        fixture.menus.confirmLeave(fixture.player);
        assertTrue(MessageText.plain(fixture.description).contains("No entry fee is charged in the lobby"));
    }

    @Test
    void uncertainRewardMenuRequiresReviewAndDoesNotPromiseAutomaticRetries() throws Exception {
        var fixture = fixture();
        var room = paid(fixture, EconomyAccess.Transfer.UNCERTAIN, true);
        fixture.menus.room(fixture.player, room);
        String text = MessageText.plain(fixture.description);
        assertTrue(text.contains("Reward received: +0.0"));
        assertTrue(text.contains("will not be automatically retried"));
        assertFalse(text.contains("Reward pending:"));
    }

    @Test
    void balancePreviewShowsShortfallWithoutCharging() throws Exception {
        var fixture = fixture();
        var room = fixture.addRoom(0);
        room.join(fixture.player.getUniqueId(), "Host");
        var server = fixture.plugin.getServer();
        var manager = server.getPluginManager();
        when(manager.isPluginEnabled("Vault")).thenReturn(true);
        var services = mock(org.bukkit.plugin.ServicesManager.class);
        when(server.getServicesManager()).thenReturn(services);
        var provider = mock(net.milkbowl.vault.economy.Economy.class);
        when(provider.isEnabled()).thenReturn(true);
        when(provider.format(anyDouble())).thenAnswer(call -> Double.toString(call.getArgument(0, Double.class)));
        var registration = new org.bukkit.plugin.RegisteredServiceProvider<>(net.milkbowl.vault.economy.Economy.class,
                provider, org.bukkit.plugin.ServicePriority.Normal, fixture.plugin);
        when(services.getRegistration(net.milkbowl.vault.economy.Economy.class)).thenReturn(registration);
        var offline = mock(org.bukkit.OfflinePlayer.class);
        when(provider.getBalance(offline)).thenReturn(5.0);
        when(fixture.plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        SessionTestSupport.module(fixture.plugin, "integrations", new OptionalIntegrations(fixture.plugin));
        try (var bukkit = mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(() -> org.bukkit.Bukkit.getOfflinePlayer(fixture.player.getUniqueId())).thenReturn(offline);
            fixture.menus.room(fixture.player, room);
        }
        assertTrue(MessageText.plain(fixture.description).contains("Entry 10.0, balance 5.0; short by 5.0"));
        verify(provider, never()).withdrawPlayer(any(org.bukkit.OfflinePlayer.class), anyDouble());
        assertFalse(java.nio.file.Files.exists(directory.resolve("economy.json")));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"en_US", "zh_CN"})
    void activePaidLeaveWarningExplainsRefundsInBothLanguages(String locale) throws Exception {
        var fixture = fixture();
        paid(fixture, EconomyAccess.Transfer.SUCCESS, false);
        Path languages = directory.resolve("paid-languages");
        try {
            assertTrue(Language.reload(languages, locale, warning -> fail(warning)));
            fixture.menus.confirmLeave(fixture.player);
            String text = MessageText.plain(fixture.description);
            assertTrue(text.contains(locale.equals("en_US") ? "ends the game for everyone" : "\u7ed3\u675f\u6240\u6709\u73a9\u5bb6\u7684\u8fd9\u573a\u5bf9\u5c40"));
            assertTrue(text.contains(locale.equals("en_US") ? "Confirmed entry charges are refunded" : "\u9000\u8fd8\u7ed9\u6bcf\u4f4d"));
        } finally {
            Language.reload(directory.resolve("default-languages"), "en_US", warning -> fail(warning));
        }
    }
}
