package GoldenEggOVO.TDGames.Shared.render.classic;

import GoldenEggOVO.TDGames.Core.game.GameFactory;
import GoldenEggOVO.TDGames.Core.text.Language;
import GoldenEggOVO.TDGames.Core.text.MessageText;
import GoldenEggOVO.TDGames.Shared.game.ClassicGame;
import GoldenEggOVO.TDGames.Shared.render.TableViewTest;
import GoldenEggOVO.TDGames.Shared.support.WorkspaceTempFactory;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ClassicLanguageRefreshTest {
    @TempDir(factory = WorkspaceTempFactory.class)
    Path languageFolder;

    @BeforeEach
    void setup() {
        MockBukkit.mock();
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void reloadUpdatesAnUnchangedMonopolyPropertyWithoutRebuildingTableGeometry() throws Exception {
        assertTrue(Language.reload(languageFolder, "en_US", message -> fail(message)));
        Files.writeString(languageFolder.resolve("audit.yml"), "\"classic.cell.monopoly.oak_grove\": \"AAA\"\n");
        assertTrue(Language.reload(languageFolder, "audit", message -> fail(message)));
        var f = new ClassicTableTest.Fixture((ClassicGame) GameFactory.create("monopoly", 2, 7L), "monopoly");
        try {
            long revision = f.room.revision();
            assertTrue(propertyLabels(f, "p1").stream().map(this::latestText).anyMatch("AAA"::equals));
            List<Entity> tableGeometry = List.copyOf((List<Entity>) TableViewTest.field(f.table, "nativeFurniture"));
            assertFalse(tableGeometry.isEmpty());
            assertTrue(tableGeometry.stream().allMatch(BlockDisplay.class::isInstance));

            int beforeIdle = f.entities.size();
            for (int pulse = 0; pulse < 10; pulse++) f.table.tick();
            assertEquals(beforeIdle, f.entities.size(), "Idle geometry must already be reused before reload");

            Files.writeString(languageFolder.resolve("audit.yml"), "\"classic.cell.monopoly.oak_grove\": \"BBB\"\n");
            assertTrue(Language.reload(languageFolder, "audit", message -> fail(message)));
            f.table.tick();
            assertEquals(revision, f.room.revision());
            assertTrue(propertyLabels(f, "p1").stream().map(this::latestText).anyMatch("BBB"::equals),
                    "The untouched property's latest TextDisplay must use the reloaded catalogue");
            assertFalse(propertyLabels(f, "p1").stream().map(this::latestText).anyMatch("AAA"::equals));
            assertEquals(tableGeometry, TableViewTest.field(f.table, "nativeFurniture"));
            for (Entity part : tableGeometry) verify(part, never()).remove();

            int afterReload = f.entities.size();
            f.entities.forEach(org.mockito.Mockito::clearInvocations);
            for (int pulse = 0; pulse < 10; pulse++) f.table.tick();
            assertEquals(afterReload, f.entities.size(), "A completed reload must not cause recurring Visual rebuilds");
            for (Entity part : f.entities) verify(part, never()).remove();
            for (TextDisplay label : propertyLabels(f, "p1")) verify(label, never()).text(any(Component.class));
        } finally {
            f.table.close();
            assertTrue(Language.reload(languageFolder, "en_US", message -> fail(message)));
        }
    }

    @SuppressWarnings("unchecked")
    private List<TextDisplay> propertyLabels(ClassicTableTest.Fixture f, String id) throws Exception {
        var views = (Map<?, ?>) TableViewTest.field(f.table, "views");
        Object view = views.get(f.owner.getUniqueId());
        var visuals = (Map<?, ?>) TableViewTest.field(view, "visuals");
        Object visual = visuals.get(id);
        assertNotNull(visual);
        return ((List<Entity>) TableViewTest.field(visual, "parts")).stream()
                .filter(TextDisplay.class::isInstance).map(TextDisplay.class::cast).toList();
    }

    private String latestText(TextDisplay display) {
        var text = ArgumentCaptor.forClass(Component.class);
        verify(display, atLeastOnce()).text(text.capture());
        return MessageText.plain(text.getValue());
    }
}
