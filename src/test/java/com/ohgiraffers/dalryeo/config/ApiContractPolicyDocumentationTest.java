package com.ohgiraffers.dalryeo.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ApiContractPolicyDocumentationTest {

    @Test
    void apiContractPolicyIsCurrentSourceOfTruth() throws IOException {
        String policy = Files.readString(Path.of("docs/standards/api-contract-policy.md"));
        String index = Files.readString(Path.of("docs/indexes/standards-index.md"));

        assertThat(policy).contains(
                "# API Contract Policy",
                "- Status: Active",
                "- Source of Truth: Yes",
                "## API 계약 범위",
                "## 하위 호환 기준",
                "## PR 작성 기준",
                "`dev`",
                "`main`",
                "`ApiContractIntegrationTest`"
        );
        assertThat(index).contains("[API Contract Policy](../standards/api-contract-policy.md)");
    }

    @Test
    void pullRequestTemplateRequiresApiCompatibilityAndClientCoordinationDecisions() throws IOException {
        String template = Files.readString(Path.of(".github/PULL_REQUEST_TEMPLATE.md"));

        assertThat(template).contains(
                "## 계약 영향",
                "API 계약 영향 범위를 확인함",
                "하위 호환 영향 없음",
                "하위 호환 영향 있음 - 운영 영향 또는 리뷰 포인트에 앱 클라이언트 영향과 대응 계획을 적음",
                "앱 클라이언트 조율 필요 없음",
                "앱 클라이언트 조율 필요 - main 승격 전 조율 계획을 적음",
                "운영 배포 승격 전 API 계약 테스트 영향 확인함"
        );
    }
}
