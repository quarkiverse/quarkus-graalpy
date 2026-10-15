package io.quarkiverse.graalpy.runtime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;

/**
 * Fluent entry point for calling Python from Java.
 *
 * <pre>
 * graalPy.module("classifier").function("predict").invoke(features).as(Prediction.class);
 * </pre>
 */
@ApplicationScoped
public class GraalPy {

    public static final String RESOURCE_ROOT = "python-sources";
    public static final String TEST_RESOURCE_ROOT = "python-test-sources";
    public static final String MANIFEST_RESOURCE = RESOURCE_ROOT + "/_manifest.txt";
    public static final String TEST_MANIFEST_RESOURCE = TEST_RESOURCE_ROOT + "/_manifest.txt";

    @Inject
    GraalPyConfig config;

    private Context context;
    private final List<Path> extractedRoots = new ArrayList<>();

    private synchronized Context context() {
        if (context == null) {
            HostAccess hostAccess = HostAccess.newBuilder(HostAccess.ALL)
                    .allowMapAccess(true)
                    .allowListAccess(true)
                    .allowBufferAccess(true)
                    .allowArrayAccess(true)
                    .allowIterableAccess(true)
                    .allowIteratorAccess(true)
                    .build();
            context = Context.newBuilder("python")
                    .allowHostAccess(hostAccess)
                    .allowIO(IOAccess.ALL)
                    .allowExperimentalOptions(true)
                    .build();
            for (String path : resolveSysPaths()) {
                String literal = path.replace("\\", "\\\\").replace("'", "\\'");
                context.eval("python", "import sys; sys.path.insert(0, '" + literal + "')");
            }
        }
        return context;
    }

    private List<String> resolveSysPaths() {
        List<String> paths = new ArrayList<>();
        config.sourcePath().ifPresent(p -> {
            Path abs = Path.of(p).toAbsolutePath();
            if (Files.isDirectory(abs)) {
                paths.add(abs.toString());
            }
        });
        Path main = extractClasspathSources(MANIFEST_RESOURCE, RESOURCE_ROOT);
        if (main != null) {
            paths.add(main.toString());
        }
        Path test = extractClasspathSources(TEST_MANIFEST_RESOURCE, TEST_RESOURCE_ROOT);
        if (test != null) {
            paths.add(test.toString());
        }
        return paths;
    }

    private Path extractClasspathSources(String manifestResource, String resourceRoot) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = GraalPy.class.getClassLoader();
        }
        URL manifestUrl = cl.getResource(manifestResource);
        if (manifestUrl == null) {
            return null;
        }
        List<String> entries;
        try (InputStream in = manifestUrl.openStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            entries = reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + manifestResource, e);
        }
        if (entries.isEmpty()) {
            return null;
        }
        try {
            Path root = Files.createTempDirectory("quarkus-graalpy-");
            for (String rel : entries) {
                URL resource = cl.getResource(resourceRoot + "/" + rel);
                if (resource == null) {
                    continue;
                }
                Path target = root.resolve(rel).normalize();
                if (!target.startsWith(root)) {
                    throw new IllegalStateException("Illegal manifest entry escapes root: " + rel);
                }
                Files.createDirectories(target.getParent());
                try (InputStream in = resource.openStream()) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            extractedRoots.add(root);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteRecursively(root)));
            return root;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to extract packaged Python sources", e);
        }
    }

    private static void deleteRecursively(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try {
            try (var walk = Files.walk(root)) {
                walk.sorted(Collections.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                    }
                });
            }
        } catch (IOException ignored) {
        }
    }

    public Module module(String name) {
        return new Module(name);
    }

    @PreDestroy
    synchronized void close() {
        if (context != null) {
            context.close();
            context = null;
        }
    }

    public final class Module {
        private final String name;

        private Module(String name) {
            this.name = name;
        }

        public Function function(String functionName) {
            return new Function(this, functionName);
        }

        private synchronized Value resolve() {
            Context ctx = context();
            Value importer = ctx.eval("python", "__import__");
            return importer.execute(name, ctx.eval("python", "None"),
                    ctx.eval("python", "None"), ctx.eval("python", "['*']"));
        }
    }

    public final class Function {
        private final Module module;
        private final String name;

        private Function(Module module, String name) {
            this.module = module;
            this.name = name;
        }

        public synchronized Result invoke(Object... args) {
            Value callable = module.resolve().getMember(name);
            if (callable == null || !callable.canExecute()) {
                throw new IllegalArgumentException(
                        "Python function not found: " + module.name + "." + name);
            }
            return new Result(callable.execute(args));
        }
    }

    public static final class Result {
        private final Value value;

        private Result(Value value) {
            this.value = value;
        }

        public <T> T as(Class<T> type) {
            return value.as(type);
        }

        public Value raw() {
            return value;
        }
    }
}
