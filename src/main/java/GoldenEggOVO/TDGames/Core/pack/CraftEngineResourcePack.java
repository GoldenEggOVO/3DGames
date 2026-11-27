package GoldenEggOVO.TDGames.Core.pack;

import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.event.*;
import org.bukkit.plugin.Plugin;

import javax.imageio.ImageIO;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

final class CraftEngineResourcePack implements AutoCloseable {
    record Download(String url, String sha1) {}

    private final Object engine;
    private final Object manager;
    private final PackBuilder builder;
    private final Path output;
    private final Method fullyLoaded, isReloading, host, upload, links, onlineUser, url, sha1;
    private final Listener listener = new Listener() {};
    private volatile String hash = "";
    private final Path hashFile;
    private volatile boolean closed;

    CraftEngineResourcePack(Plugin owner, Path input) throws ReflectiveOperationException {
        Plugin dependency = Bukkit.getPluginManager().getPlugin("CraftEngine");
        if (dependency == null || !dependency.isEnabled())
            throw new IllegalStateException("CraftEngine is not enabled");
        ClassLoader loader = dependency.getClass().getClassLoader();
        Class<?> api = Class.forName("net.momirealms.craftengine.core.plugin.CraftEngine", true, loader);
        engine = api.getMethod("instance").invoke(null);
        fullyLoaded = api.getMethod("isFullyLoaded");
        isReloading = api.getMethod("isReloading");
        manager = api.getMethod("packManager").invoke(engine);
        Class<?> managerType = Class.forName("net.momirealms.craftengine.core.pack.AbstractPackManager", true, loader);
        Class<?> packs = Class.forName("net.momirealms.craftengine.core.pack.PackManager", true, loader);
        Method legacyGenerate = null;
        try {
            legacyGenerate = packs.getMethod("generateResourcePack");
        } catch (NoSuchMethodException workflowApi) {
            packs.getMethod("runWorkflow", String.class);
        }
        if (legacyGenerate == null) {
            output = dependency.getDataFolder().toPath().resolve("generated/resource_pack.zip");
            builder = workflowBuilder(api, managerType);
        } else {
            output = (Path) packs.getMethod("resourcePackPath").invoke(manager);
            Field hosting = managerType.getDeclaredField("resourcePackHost");
            hosting.setAccessible(true);
            Method generatePack = legacyGenerate;
            builder = source -> generate(manager, generatePack, hosting, output, source);
        }
        hashFile = output.resolveSibling(output.getFileName() + ".plugins.sha1");
        host = packs.getMethod("resourcePackHost");
        Class<?> hosting = Class.forName("net.momirealms.craftengine.core.pack.host.ResourcePackHost", true, loader);
        upload = hosting.getMethod("upload", Path.class);
        links = hosting.getMethod("requestResourcePackDownloadLink",
                Class.forName("net.momirealms.craftengine.core.plugin.network.NetWorkUser", true, loader));
        onlineUser = Class.forName("net.momirealms.craftengine.core.plugin.network.NetworkManager", true, loader)
                .getMethod("getOnlineUser", UUID.class);
        Class<?> download = Class.forName("net.momirealms.craftengine.core.pack.host.ResourcePackDownloadData", true, loader);
        url = download.getMethod("url");
        sha1 = download.getMethod("sha1");
        Class<? extends Event> cache = Class.forName(
                "net.momirealms.craftengine.bukkit.api.event.AsyncResourcePackCacheEvent", true, loader).asSubclass(Event.class);
        Method register = cache.getMethod("registerExternalResourcePack", Path.class);
        Bukkit.getPluginManager().registerEvent(cache, listener, EventPriority.NORMAL, (ignored, event) -> {
            if (closed) return;
            try {
                register.invoke(event, input);
            } catch (ReflectiveOperationException exception) {
                throw new EventException(exception);
            }
        }, owner);
    }

    boolean ready() throws ReflectiveOperationException {
        return !closed && (boolean) fullyLoaded.invoke(engine) && !(boolean) isReloading.invoke(engine);
    }

    String hash() {
        if (closed || hash.isEmpty()) return "";
        try (var reader = Files.newBufferedReader(hashFile, StandardCharsets.US_ASCII)) {
            char[] value = new char[41];
            int length = reader.read(value);
            String current = length < 0 ? "" : new String(value, 0, length).trim();
            return current.matches("[a-f0-9]{40}") ? current : "";
        } catch (IOException exception) {
            return "";
        }
    }

    synchronized String rebuild(LocalResourcePack.Archive source) throws IOException, ReflectiveOperationException {
        synchronized (manager) {
            if (!ready()) throw new IOException("CraftEngine is not ready to rebuild its resource pack");
            builder.build(source);
            if (!ready()) throw new IOException("CraftEngine stopped or started reloading during pack generation");
            byte[] bytes = Files.readAllBytes(output);
            String next;
            try {
                next = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException(exception);
            }
            try {
                ((CompletableFuture<?>) upload.invoke(host.invoke(manager), output)).get(120, TimeUnit.SECONDS);
            } catch (ExecutionException | TimeoutException exception) {
                throw new IOException("Cannot publish the unified CraftEngine resource pack", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Unified resource-pack rebuild interrupted", exception);
            }
            if (closed) throw new IOException("Resource-pack integration is closed");
            Path temporary = Files.createTempFile(hashFile.getParent(), "published-hash-", ".tmp");
            try {
                Files.writeString(temporary, next, StandardCharsets.US_ASCII);
                try {
                    Files.move(temporary, hashFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(temporary, hashFile, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
            hash = next;
            return hash;
        }
    }

    CompletableFuture<Download> download(UUID player) {
        try {
            Object network = engine.getClass().getMethod("networkManager").invoke(engine);
            Object user = onlineUser.invoke(network, player);
            String currentHash = hash();
            if (closed || currentHash.isEmpty() || user == null)
                return CompletableFuture.failedFuture(new IOException("Unified resource pack is unavailable"));
            return ((CompletableFuture<?>) links.invoke(host.invoke(manager), user)).orTimeout(30, TimeUnit.SECONDS).thenApply(value -> {
                try {
                    var downloads = (List<?>) value;
                    Object data = downloads.size() == 1 ? downloads.getFirst() : null;
                    for (Object candidate : downloads)
                        if (currentHash.equals(sha1.invoke(candidate))) { data = candidate; break; }
                    if (data == null) throw new IOException("Unified CraftEngine resource pack is unavailable");
                    return new Download((String) url.invoke(data), (String) sha1.invoke(data));
                } catch (ReflectiveOperationException | IOException exception) {
                    throw new CompletionException(exception);
                }
            });
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    @FunctionalInterface
    private interface PackBuilder {
        void build(LocalResourcePack.Archive source) throws IOException, ReflectiveOperationException;
    }

    private PackBuilder workflowBuilder(Class<?> api, Class<?> managerType) throws ReflectiveOperationException {
        String pack = "net.momirealms.craftengine.core.pack.";
        ClassLoader loader = api.getClassLoader();
        Class<?> options = loader.loadClass(pack + "PackGenerationOptions");
        Class<?> sequence = loader.loadClass(pack + "workflow.PackWorkflowSequence");
        String steps = pack + "workflow.impl.";
        String candidateName = "plugins-verified-candidate.zip";
        Object workflow = sequence.getConstructor(String.class, Set.class, List.class, boolean.class)
                .newInstance("verified-plugin-pack", Set.of(), List.of(
                        loader.loadClass(steps + "GeneratePackWorkflow").getConstructor(options)
                                .newInstance(options.getMethod("defaults").invoke(null)),
                        loader.loadClass(steps + "ValidatePackWorkflow").getConstructor().newInstance(),
                        loader.loadClass(steps + "ZipPackWorkflow").getConstructor(String.class, boolean.class)
                                .newInstance("./generated/" + candidateName, false)), false);
        Method execute = managerType.getDeclaredMethod("executeWorkflow", sequence);
        execute.setAccessible(true);
        Object coordinator = api.getMethod("resourceOperations").invoke(engine);
        Method acquire = coordinator.getClass().getMethod("acquire");
        Method release = acquire.getReturnType().getMethod("close");
        Path candidate = output.resolveSibling(candidateName);
        return source -> {
            Object lease = acquire.invoke(coordinator);
            try {
                Files.createDirectories(output.getParent());
                Files.deleteIfExists(candidate);
                execute.invoke(manager, workflow);
                verifyMerged(source, candidate);
                Files.move(candidate, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                try {
                    Files.deleteIfExists(candidate);
                } finally {
                    release.invoke(lease);
                }
            }
        };
    }

    static void generate(Object manager, Method generate, Field hosting, Path output,
            LocalResourcePack.Archive source) throws IOException, ReflectiveOperationException {
        synchronized (manager) {
            byte[] original = Files.exists(output) ? Files.readAllBytes(output) : null;
            Object host = hosting.get(manager);
            Object guarded = Proxy.newProxyInstance(hosting.getType().getClassLoader(), new Class<?>[]{hosting.getType()},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("canUpload")) return false;
                        try { return method.invoke(host, arguments); }
                        catch (InvocationTargetException exception) { throw exception.getCause(); }
                    });
            hosting.set(manager, guarded);
            boolean valid = false;
            try {
                generate.invoke(manager);
                verifyMerged(source, output);
                if (hosting.get(manager) != guarded)
                    throw new IOException("CraftEngine hosting changed during resource-pack generation");
                valid = true;
            } finally {
                boolean owned = hosting.get(manager) == guarded;
                if (owned) hosting.set(manager, host);
                if (!valid && owned) {
                    if (original == null) Files.deleteIfExists(output);
                    else Files.write(output, original);
                }
            }
        }
    }

    private static void verifyMerged(LocalResourcePack.Archive source, Path output) throws IOException {
        try (var merged = new ZipFile(output.toFile());
                var input = new ZipInputStream(new ByteArrayInputStream(source.bytes()))) {
            for (ZipEntry entry; (entry = input.getNextEntry()) != null;) {
                String name = entry.getName();
                if (!name.startsWith("assets/3dgames/")) continue;
                var target = merged.getEntry(name);
                if (target == null) throw new IOException("Unified pack is missing tabletop resource: " + name);
                if (name.endsWith(".json") || name.endsWith(".mcmeta")) {
                    try (var resource = merged.getInputStream(target)) {
                        if (!JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                                .equals(JsonParser.parseString(new String(resource.readAllBytes(), StandardCharsets.UTF_8))))
                            throw new IOException("Unified pack contains an outdated tabletop definition: " + name);
                    }
                    continue;
                }
                if (!name.endsWith(".png")) {
                    try (var resource = merged.getInputStream(target)) {
                        if (!Arrays.equals(input.readAllBytes(), resource.readAllBytes()))
                            throw new IOException("Unified pack contains an outdated tabletop resource: " + name);
                    }
                    continue;
                }
                var expected = ImageIO.read(input);
                try (var texture = merged.getInputStream(target)) {
                    var actual = ImageIO.read(texture);
                    if (actual == null || expected.getWidth() != actual.getWidth() || expected.getHeight() != actual.getHeight()
                            || !Arrays.equals(expected.getRGB(0, 0, expected.getWidth(), expected.getHeight(), null, 0, expected.getWidth()),
                                    actual.getRGB(0, 0, actual.getWidth(), actual.getHeight(), null, 0, actual.getWidth())))
                        throw new IOException("Unified pack contains an outdated tabletop texture: " + name);
                }
            }
        }
    }

    @Override
    public void close() {
        closed = true;
        HandlerList.unregisterAll(listener);
    }
}
