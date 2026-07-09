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
    void deployWorkflowsUseSplitDevAndProdTriggersAndContainerManagedRuntimeEnv() throws IOException {
        String devWorkflow = Files.readString(Path.of(".github/workflows/deploy-dev.yml"));
        String prodWorkflow = Files.readString(Path.of(".github/workflows/deploy-prod.yml"));
        String policy = Files.readString(Path.of("docs/standards/testing-policy.md"));

        assertThat(devWorkflow).contains(
                "branches: [\"dev\"]",
                "environment: dev"
        );
        assertThat(prodWorkflow).contains(
                "branches: [\"main\"]",
                "environment: prod"
        );
        assertThat(devWorkflow).doesNotContain(
                "branches: [\"main\"]",
                "environment: prod",
                "\"SENTRY_ENVIRONMENT=dev\"",
                "\"SPRING_PROFILES_ACTIVE=dev\"",
                "SENTRY_ENVIRONMENT",
                "SPRING_PROFILES_ACTIVE"
        );
        assertThat(prodWorkflow).doesNotContain(
                "branches: [\"dev\"]",
                "environment: dev",
                "\"SENTRY_ENVIRONMENT=prod\"",
                "\"SPRING_PROFILES_ACTIVE=${{ vars.SPRING_ENV }}\"",
                "SENTRY_ENVIRONMENT",
                "SPRING_PROFILES_ACTIVE"
        );
        assertThat(policy).contains(
                "`.github/workflows/deploy-dev.yml`은 `dev` push와 GitHub `dev` Environment 기준으로 실행한다",
                "`.github/workflows/deploy-prod.yml`은 `main` push와 GitHub `prod` Environment 기준으로 실행한다",
                "배포 workflow는 `SENTRY_ENVIRONMENT`와 `SPRING_PROFILES_ACTIVE`를 주입하지 않는다"
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
