package GoldenEggOVO.TDGames.Shared.session;

import GoldenEggOVO.TDGames.SessionTestSupport;

import GoldenEggOVO.TDGames.Core.player.PlayerSessions;

import GoldenEggOVO.TDGames.TDGamesPlugin;
import GoldenEggOVO.TDGames.Shared.interaction.TableWorldController;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OfflineRestoreTest {
    @TempDir Path directory;

    @Test
    void persistedOfflineSeatsUseOwnerAuthenticatedRestoreInsteadOfAdmission() throws Exception {
        TDGamesPlugin plugin = SessionTestSupport.plugin();
        SessionTestSupport.module(plugin, "playerSessions", mock(PlayerSessions.class));
        SessionTestSupport.module(plugin, "tableWorld", mock(TableWorldController.class));
        SessionTestSupport.install(plugin, new LinkedHashMap<UUID, GameSession>());
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        doCallRealMethod().when(SessionTestSupport.service(plugin)).restore();
        UUID player = UUID.randomUUID();
        String source =
                "{\"returns\":{},\"rooms\":[{\"id\":\""
                        + UUID.randomUUID()
                        + "\",\"kind\":\"chess\",\"capacity\":2,\"seed\":1,\"table\":0,\"phase\":\"LOBBY\",\"revision\":1,\"history\":[],\"seats\":[{\"id\":\""
                        + player
                        + "\",\"name\":\"offline\",\"bot\":false}]}]}";
        Files.writeString(directory.resolve("rooms.json"), source);
        when(plugin.sessions().restoreReservation(player, "chess")).thenReturn(true);
        assertDoesNotThrow(plugin.games()::restore);
        verify(plugin.sessions()).restoreReservation(player, "chess");
        verify(plugin.sessions(), never()).reserve(any(), any());
        assertEquals(1, plugin.games().activeSessions().size());
        assertTrue(plugin.games().activeSessions().values().iterator().next().offline.containsKey(player));
        assertEquals(source, Files.readString(directory.resolve("rooms.json")));
    }

    @Test
    void removedMahjongProfilesRejectRestoreWithoutOverwritingOriginalRecords() throws Exception {
        for (String profile : List.of("fuzhou", "qinhuangdao")) {
            Path data = Files.createDirectory(directory.resolve(profile));
            TDGamesPlugin plugin = SessionTestSupport.plugin();
            SessionTestSupport.install(plugin, new LinkedHashMap<UUID, GameSession>());
            when(plugin.getDataFolder()).thenReturn(data.toFile());
            doCallRealMethod().when(SessionTestSupport.service(plugin)).restore();
            doCallRealMethod().when(SessionTestSupport.service(plugin)).save();
            String source =
                    "{\"returns\":{},\"rooms\":[{\"id\":\""
                            + UUID.randomUUID()
                            + "\",\"kind\":\"mahjong\",\"capacity\":4,\"seed\":1,\"table\":0,\"rulesVersion\":1,\"options\":{\"profile\":\""
                            + profile
                            + "\"}}]}";
            Path file = data.resolve("rooms.json");
            Files.writeString(file, source);
            var failure = assertThrows(IllegalStateException.class, plugin.games()::restore);
            assertInstanceOf(IllegalArgumentException.class, failure.getCause());
            assertEquals("Unknown mahjong profile: " + profile, failure.getCause().getMessage());
            assertTrue(plugin.games().activeSessions().isEmpty());
            plugin.games().save();
            assertEquals(source, Files.readString(file));
            try (var files = Files.list(data)) {
                var copies =
                        files.filter(
                                        p ->
                                                p.getFileName()
                                                        .toString()
                                                        .startsWith("rooms.unreadable-"))
                                .toList();
                assertEquals(1, copies.size());
                assertEquals(source, Files.readString(copies.getFirst()));
            }
        }
    }

}
