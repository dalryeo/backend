package com.ohgiraffers.dalryeo.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class EnvironmentVariableDocumentationTest {

    private static final Pattern PLACEHOLDER_ENV =
            Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*)(?=[:}])");

    @Test
    void readmeDocumentsRuntimeEnvironmentVariables() throws IOException {
        Set<String> variables = new TreeSet<>();
        variables.addAll(extractEnvironmentVariables(Path.of("src/main/resources/application.yml")));
        variables.addAll(extractEnvironmentVariables(Path.of("src/main/resources/application-dev.yml")));
        variables.addAll(extractEnvironmentVariables(Path.of("src/main/resources/application-prod.yml")));

        assertReadmeDocuments(variables);
    }

    @Test
    void readmeDocumentsTestOnlyEnvironmentVariables() throws IOException {
        Set<String> variables = extractEnvironmentVariables(Path.of("src/test/resources/application-test.yml"));

        assertReadmeDocuments(variables);
    }

    private Set<String> extractEnvironmentVariables(Path path) throws IOException {
        String content = Files.readString(path);
        Matcher matcher = PLACEHOLDER_ENV.matcher(content);
        Set<String> variables = new TreeSet<>();
        while (matcher.find()) {
            variables.add(matcher.group(1));
        }
        return variables;
    }

    private void assertReadmeDocuments(Set<String> variables) throws IOException {
        String readme = Files.readString(Path.of("README.md"));

        assertThat(variables)
                .allSatisfy(variable -> assertThat(readme)
                        .as("README should document `%s`", variable)
                        .contains("`" + variable + "`"));
    }
}
