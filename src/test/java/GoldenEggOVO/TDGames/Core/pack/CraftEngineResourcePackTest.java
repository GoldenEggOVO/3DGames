package GoldenEggOVO.TDGames.Core.pack;

import GoldenEggOVO.TDGames.Shared.support.WorkspaceTempFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

import static org.junit.jupiter.api.Assertions.*;

class CraftEngineResourcePackTest {
    @TempDir(factory = WorkspaceTempFactory.class) Path folder;

    public interface Host {
        boolean canUpload();
        void upload();
        String identity();
    }

    public static final class OriginalHost implements Host {
        int uploads;
        public boolean canUpload() { return true; }
        public void upload() { uploads++; }
        public String identity() { return "original"; }
    }

    public static final class Manager {
        public Host resourcePackHost = new OriginalHost();
        final Path output;
        byte[] candidate;
        Manager(Path output) { this.output = output; }
        public void generateResourcePack() throws IOException {
            assertEquals("original", resourcePackHost.identity());
            if (candidate == null) Files.writeString(output, "Broken candidate ZIP");
            else Files.write(output, candidate);
            if (resourcePackHost.canUpload()) resourcePackHost.upload();
        }
    }

    private byte[] archive(String value) throws Exception {
        return archive("assets/3dgames/items/test.json", value);
    }

    private byte[] archive(String name, String value) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    @Test
    void aStaleSoundIsRejectedBeforePublishing() throws Exception {
        Path output = folder.resolve("unified.zip");
        String name = "assets/3dgames/sounds/cards/play.ogg";
        byte[] original = archive(name, "Old sound");
        Files.write(output, original);
        var manager = new Manager(output);
        manager.candidate = original;
        var host = (OriginalHost) manager.resourcePackHost;
        assertThrows(IOException.class, () -> CraftEngineResourcePack.generate(manager,
                Manager.class.getMethod("generateResourcePack"), Manager.class.getField("resourcePackHost"),
                output, new LocalResourcePack.Archive("11".repeat(20), archive(name, "New sound"))));
        assertArrayEquals(original, Files.readAllBytes(output));
        assertSame(host, manager.resourcePackHost);
        assertEquals(0, host.uploads);
    }

    @Test
    void aStaleGeneratedDefinitionIsRejectedBeforePublishing() throws Exception {
        Path output = folder.resolve("unified.zip");
        byte[] original = archive("{\"model\":\"old\"}");
        Files.write(output, original);
        var manager = new Manager(output);
        manager.candidate = original;
        var host = (OriginalHost) manager.resourcePackHost;
        assertThrows(IOException.class, () -> CraftEngineResourcePack.generate(manager,
                Manager.class.getMethod("generateResourcePack"), Manager.class.getField("resourcePackHost"),
                output, new LocalResourcePack.Archive("11".repeat(20), archive("{\"model\":\"new\"}"))));
        assertArrayEquals(original, Files.readAllBytes(output));
        assertSame(host, manager.resourcePackHost);
        assertEquals(0, host.uploads);
    }

    @Test
    void rejectedGenerationCannotAutoPublishAndRestoresTheOriginalZipAndHost() throws Exception {
        Path output = folder.resolve("unified.zip");
        byte[] original = "Previously published archive".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(output, original);
        var source = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(source)) {
            zip.putNextEntry(new ZipEntry("assets/3dgames/items/test.json"));
            zip.write("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        var manager = new Manager(output);
        var host = (OriginalHost) manager.resourcePackHost;
        var generate = assertDoesNotThrow(() -> CraftEngineResourcePack.class.getDeclaredMethod("generate",
                Object.class, java.lang.reflect.Method.class, java.lang.reflect.Field.class, Path.class,
                LocalResourcePack.Archive.class));
        generate.setAccessible(true);
        var failure = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> generate.invoke(null,
                manager, Manager.class.getMethod("generateResourcePack"), Manager.class.getField("resourcePackHost"),
                output, new LocalResourcePack.Archive("11".repeat(20), source.toByteArray())));
        assertInstanceOf(IOException.class, failure.getCause());
        assertArrayEquals(original, Files.readAllBytes(output));
        assertSame(host, manager.resourcePackHost);
        assertEquals(0, host.uploads);
    }
    @Test
    void currentPublishedHashDoesNotWaitForGenerationAndTracksCompanionPublication() throws Exception {
        Path sidecar = folder.resolve("resource-pack.zip.plugins.sha1");
        Files.writeString(sidecar, "11".repeat(20));
        var integration = org.mockito.Mockito.mock(CraftEngineResourcePack.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        Object manager = new Object();
        for (var entry : java.util.Map.<String, Object>of("manager", manager, "hashFile", sidecar, "hash", "22".repeat(20)).entrySet()) {
            var field = CraftEngineResourcePack.class.getDeclaredField(entry.getKey());
            field.setAccessible(true);
            field.set(integration, entry.getValue());
        }
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            synchronized (manager) {
                assertEquals("11".repeat(20), executor.submit(integration::hash).get(2, java.util.concurrent.TimeUnit.SECONDS),
                        "Main-thread requests must not wait for the CE generation/upload lock");
            }
        }
        Files.writeString(sidecar, "33".repeat(20));
        assertEquals("33".repeat(20), integration.hash(), "Companion publications invalidate the local hash");
        Files.writeString(sidecar, "partial");
        assertEquals("", integration.hash(), "An incomplete sidecar must never become a pack hash");
    }

}
