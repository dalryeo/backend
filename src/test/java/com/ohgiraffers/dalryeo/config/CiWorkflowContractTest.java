package com.ohgiraffers.dalryeo.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CiWorkflowContractTest {

    @Test
    void backendVerifyRunsSameChecksForPullRequestsToAnyBaseBranch() throws IOException {
        String workflow = Files.readString(Path.of(".github/workflows/ci.yml"));

        assertThat(workflow).contains("pull_request:");
        assertThat(workflow).doesNotContain("pull_request:\n    branches:");
        assertThat(workflow).contains(
                "bash scripts/test-harness-checks.sh",
                "bash scripts/docs-lint.sh",
                "bash scripts/check-migration-files.sh",
                "bash scripts/check-sensitive-paths.sh --tracked",
                "bash scripts/check-pr-contract.sh --changed-files changed-files.txt --base-ref \"$GITHUB_BASE_REF\"",
                "run: ./scripts/test-local.sh"
        );
    }

    @Test
    void ciCollectsNameStatusSoMigrationChangesCanBlockExistingFileEdits() throws IOException {
        String workflow = Files.readString(Path.of(".github/workflows/ci.yml"));

        assertThat(workflow).contains("git diff --name-status");
        assertThat(workflow).doesNotContain("git diff --name-only");
    }

    @Test
    void deployDevWorkflowUsesOnlyDevBranchAndDevRuntimeProfile() throws IOException {
        String workflow = Files.readString(Path.of(".github/workflows/deploy-dev.yml"));

        assertThat(workflow).contains("branches: [\"dev\"]");
        assertThat(workflow).contains("if: github.ref == 'refs/heads/dev'");
        assertThat(workflow).doesNotContain("branches: [\"main\"]");
        assertThat(workflow).contains(
                "\"SENTRY_ENVIRONMENT=dev\"",
                "\"SPRING_PROFILES_ACTIVE=dev\""
        );
        assertThat(workflow).doesNotContain(
                "\"SENTRY_ENVIRONMENT=prod\"",
                "\"SPRING_PROFILES_ACTIVE=prod\""
        );
    }

    @Test
    void testingPolicyDocumentsPrVerificationForDevAndMain() throws IOException {
        String policy = Files.readString(Path.of("docs/standards/testing-policy.md"));

        assertThat(policy).contains(
                "- Source of Truth: Yes",
                "`dev`",
                "`main`",
                "`pull_request`",
                "`scripts/docs-lint.sh`",
                "`scripts/check-sensitive-paths.sh --tracked`",
                "`./scripts/test-local.sh`"
        );
    }
}
