package dev.javuk.session;

import dev.javuk.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Stores sessions as JSON files under {@code ~/.config/javuk/sessions}. Each
 * session is {@code <id>.json}; {@link #list()} returns ids newest-first.
 */
public final class SessionStore {

    private final Path dir;

    public SessionStore() {
        this(defaultDir());
    }

    public SessionStore(Path dir) {
        this.dir = dir;
    }

    public static Path defaultDir() {
        return Path.of(System.getProperty("user.home"), ".config", "javuk", "sessions");
    }

    public void save(Session session) throws IOException {
        Files.createDirectories(dir);
        Path file = dir.resolve(session.id() + ".json");
        Files.writeString(file, Json.write(session));
    }

    public Session load(String id) throws IOException {
        Path file = dir.resolve(id + ".json");
        if (!Files.exists(file)) {
            throw new IOException("No session named '" + id + "'");
        }
        return Json.MAPPER.readValue(Files.readString(file), Session.class);
    }

    /** Session ids, most recently modified first. */
    public List<String> list() {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> sorted = new ArrayList<>(files
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .toList());
            sorted.sort(Comparator.comparingLong(SessionStore::lastModified).reversed());
            return sorted.stream()
                    .map(p -> p.getFileName().toString().replaceFirst("\\.json$", ""))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** The most recently modified session id, or null if there are none. */
    public String mostRecent() {
        List<String> all = list();
        return all.isEmpty() ? null : all.get(0);
    }

    private static long lastModified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }
}
