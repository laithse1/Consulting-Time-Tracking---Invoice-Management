package io.consulting.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Architecture contracts: adapters and persistence cannot absorb each other's responsibilities. */
class PackageBoundariesTest {

    @Test
    void controllersDoNotDependOnPersistenceOrProviderImplementations() throws IOException {
        assertImportsExclude(
            "controller",
            List.of(
                "io.consulting.repository",
                "org.springframework.jdbc",
                "java.sql",
                "io.consulting.integration"
            )
        );
    }

    @Test
    void repositoriesDoNotDependOnServicesOrControllers() throws IOException {
        assertImportsExclude(
            "repository",
            List.of("io.consulting.service", "io.consulting.controller")
        );
    }

    @Test
    void domainDoesNotDependOnSpringOrApplicationAdapters() throws IOException {
        assertImportsExclude(
            "domain",
            List.of(
                "org.springframework",
                "io.consulting.controller",
                "io.consulting.repository",
                "io.consulting.service",
                "io.consulting.integration"
            )
        );
    }

    private void assertImportsExclude(String packageName, List<String> forbidden)
        throws IOException {
        Path directory = Path.of("src/main/java/io/consulting", packageName);
        try (Stream<Path> paths = Files.walk(directory)) {
            List<Path> sources = paths.filter(path -> path.toString().endsWith(".java")).toList();
            assertFalse(sources.isEmpty(), "Architecture package is missing: " + packageName);
            for (Path source : sources) {
                for (String line : Files.readAllLines(source)) {
                    String statement = line.trim();
                    if (!statement.startsWith("import ")) continue;
                    assertTrue(
                        forbidden.stream().noneMatch(statement::contains),
                        source +
                            " violates the " +
                            packageName +
                            " dependency boundary: " +
                            statement
                    );
                }
            }
        }
    }
}
