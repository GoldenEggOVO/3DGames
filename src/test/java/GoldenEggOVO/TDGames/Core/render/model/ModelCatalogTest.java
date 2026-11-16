package GoldenEggOVO.TDGames.Core.render.model;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ModelCatalogTest {
    @Test void everyGeometryFileIsRegisteredAndEveryRegisteredFileIsShipped() throws Exception {
        Path root=Path.of("src/main/resources/vanilla-models");
        var index=JsonParser.parseString(Files.readString(root.resolve("index.json"))).getAsJsonObject();
        Set<String> expected=new HashSet<>(Set.of("index.json", "shared/cards/head_textures.json", "tabletop/color_eight/head_textures.json"));
        for (var entry : index.entrySet()) {
            expected.add(entry.getValue().getAsString());
            assertNotNull(ModelRepository.require(entry.getKey()),entry.getKey());
        }
        try (var files=Files.walk(root)) {
            var actual=files.filter(Files::isRegularFile).map(root::relativize)
                    .map(path->path.toString().replace('\\','/')).collect(java.util.stream.Collectors.toSet());
            assertEquals(expected,actual,"Native model files must have a live catalog or texture reference");
        }
    }
}
