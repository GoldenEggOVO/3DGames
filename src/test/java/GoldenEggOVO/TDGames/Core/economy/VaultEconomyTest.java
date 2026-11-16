package GoldenEggOVO.TDGames.Core.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VaultEconomyTest {
    @Test
    void providerRegistrationChangesAreDetectedWithoutRestart() {
        var current = new AtomicReference<Economy>();
        var vault = new VaultEconomy(current::get, ignored -> mock(OfflinePlayer.class));
        assertFalse(vault.available());
        var provider = mock(Economy.class);
        when(provider.isEnabled()).thenReturn(true);
        current.set(provider);
        assertTrue(vault.available());
        current.set(null);
        assertFalse(vault.available());
    }

    @Test
    void operationsUseOfflinePlayerApiAndKeepFailedResponsesDistinct() {
        UUID id = UUID.randomUUID();
        var player = mock(OfflinePlayer.class);
        var provider = mock(Economy.class);
        when(provider.isEnabled()).thenReturn(true);
        when(provider.fractionalDigits()).thenReturn(2);
        when(provider.getBalance(player)).thenReturn(50.0);
        when(provider.withdrawPlayer(player, 10)).thenReturn(new EconomyResponse(10, 40, EconomyResponse.ResponseType.SUCCESS, null));
        when(provider.depositPlayer(player, 25)).thenReturn(new EconomyResponse(0, 40, EconomyResponse.ResponseType.FAILURE, "rejected"));
        var vault = new VaultEconomy(() -> provider, requested -> {
            assertEquals(id, requested);
            return player;
        });
        assertEquals(50, vault.balance(id).orElseThrow());
        assertEquals(EconomyAccess.Transfer.SUCCESS, vault.withdraw(id, 10));
        assertEquals(EconomyAccess.Transfer.REJECTED, vault.deposit(id, 25));
        when(provider.depositPlayer(player, 25)).thenThrow(new IllegalStateException("unknown transaction outcome"));
        assertEquals(EconomyAccess.Transfer.UNCERTAIN, vault.deposit(id, 25));
        verify(provider, never()).withdrawPlayer(anyString(), anyDouble());
    }

    @Test
    void invalidAmountsAndBalancesCannotReachTheProvider() {
        var provider = mock(Economy.class);
        var player = mock(OfflinePlayer.class);
        when(provider.isEnabled()).thenReturn(true);
        when(provider.fractionalDigits()).thenReturn(2);
        when(provider.getBalance(player)).thenReturn(Double.NaN);
        var vault = new VaultEconomy(() -> provider, ignored -> player);
        assertTrue(vault.balance(UUID.randomUUID()).isEmpty());
        for (double amount : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY, .001}) {
            assertEquals(EconomyAccess.Transfer.REJECTED, vault.withdraw(UUID.randomUUID(), amount));
            assertEquals(EconomyAccess.Transfer.REJECTED, vault.deposit(UUID.randomUUID(), amount));
        }
        verify(provider, never()).withdrawPlayer(any(OfflinePlayer.class), anyDouble());
        verify(provider, never()).depositPlayer(any(OfflinePlayer.class), anyDouble());
    }
}
