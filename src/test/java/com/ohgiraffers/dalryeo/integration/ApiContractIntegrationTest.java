package com.ohgiraffers.dalryeo.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ohgiraffers.dalryeo.auth.entity.User;
import com.ohgiraffers.dalryeo.auth.entity.UserStatus;
import com.ohgiraffers.dalryeo.auth.jwt.JwtTokenProvider;
import com.ohgiraffers.dalryeo.auth.oauth.AppleOAuthValidator;
import com.ohgiraffers.dalryeo.auth.repository.AuthTokenRepository;
import com.ohgiraffers.dalryeo.auth.repository.OAuthClientRepository;
import com.ohgiraffers.dalryeo.auth.repository.UserRepository;
import com.ohgiraffers.dalryeo.record.entity.RunningRecord;
import com.ohgiraffers.dalryeo.record.dto.RecordIdResponse;
import com.ohgiraffers.dalryeo.record.dto.RunningRecordRequest;
import com.ohgiraffers.dalryeo.record.outbox.RecordOutboxEventProcessor;
import com.ohgiraffers.dalryeo.record.outbox.RecordOutboxEventRepository;
import com.ohgiraffers.dalryeo.record.repository.RunningRecordRepository;
import com.ohgiraffers.dalryeo.record.repository.WeeklyUserStatsRepository;
import com.ohgiraffers.dalryeo.record.service.RecordService;
import com.ohgiraffers.dalryeo.tier.entity.Tier;
import com.ohgiraffers.dalryeo.tier.entity.TierGrade;
import com.ohgiraffers.dalryeo.tier.repository.TierGradeRepository;
import com.ohgiraffers.dalryeo.tier.repository.TierRepository;
import com.ohgiraffers.dalryeo.weeklytier.entity.WeeklyTier;
import com.ohgiraffers.dalryeo.weeklytier.repository.WeeklyTierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiContractIntegrationTest {

    private static final Path TEST_PROFILE_IMAGE_UPLOAD_DIR = Path.of("build/test-uploads/profile-images");
    private static final ZoneOffset TEST_ZONE_OFFSET = ZoneOffset.ofHours(9);
    private static final String INVALID_REQUEST_BODY_MESSAGE = "요청 본문 형식이 올바르지 않습니다.";
    private static final String INVALID_OFFSET_DATE_TIME_MESSAGE =
            "시간 값은 timezone offset을 포함해야 합니다. 예: 2026-04-14T12:13:09+09:00";
    private static final String INVALID_LOCAL_DATE_MESSAGE =
            "날짜 값은 yyyy-MM-dd 형식이어야 합니다. 예: 2001-01-01";
    private static final String INVALID_NUMBER_MESSAGE = "숫자 형식으로 입력해야 합니다.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RunningRecordRepository runningRecordRepository;

    @Autowired
    private WeeklyUserStatsRepository weeklyUserStatsRepository;

    @Autowired
    private RecordOutboxEventRepository recordOutboxEventRepository;

    @Autowired
    private RecordOutboxEventProcessor recordOutboxEventProcessor;

    @Autowired
    private RecordService recordService;

    @Autowired
    private WeeklyTierRepository weeklyTierRepository;

    @Autowired
    private OAuthClientRepository oAuthClientRepository;

    @Autowired
    private AuthTokenRepository authTokenRepository;

    @Autowired
    private TierRepository tierRepository;

    @Autowired
    private TierGradeRepository tierGradeRepository;

    @MockBean
    private AppleOAuthValidator appleOAuthValidator;

    @BeforeEach
    void cleanDatabase() {
        authTokenRepository.deleteAll();
        oAuthClientRepository.deleteAll();
        weeklyTierRepository.deleteAll();
        recordOutboxEventRepository.deleteAll();
        weeklyUserStatsRepository.deleteAll();
        runningRecordRepository.deleteAll();
        userRepository.deleteAll();
        tierGradeRepository.deleteAll();
        tierRepository.deleteAll();
        cleanProfileImageDirectory();
        seedTierMetadata();
    }

    @Test
    void loginWithApple_keepsTokenResponseContract() throws Exception {
        when(appleOAuthValidator.validateAndExtractAppleId("identity-token"))
                .thenReturn("apple-sub-1");

        mockMvc.perform(post("/auth/oauth/apple")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identityToken": "identity-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andExpect(jsonPath("$.data.isNewUser").value(true));
    }

    @Test
    void loginWithApple_returnsUnauthorizedWhenIdentityTokenVerificationFails() throws Exception {
        when(appleOAuthValidator.validateAndExtractAppleId("invalid-identity-token"))
                .thenThrow(new RuntimeException("Apple identityToken validation failed"));

        mockMvc.perform(post("/auth/oauth/apple")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identityToken": "invalid-identity-token"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value("AC-003"))
                .andExpect(jsonPath("$.data.message").value("OAuth 토큰 검증 실패"));
    }

    @Test
    void refreshToken_keepsTokenResponseContract() throws Exception {
        JsonNode loginResponse = login("apple-sub-refresh", "identity-refresh");
        String refreshToken = loginResponse.path("data").path("refreshToken").asText();

        mockMvc.perform(post("/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString());
    }

    @Test
    void refreshToken_returnsUnauthorizedWhenAccessTokenIsSubmitted() throws Exception {
        JsonNode loginResponse = login("apple-sub-refresh-access-token", "identity-refresh-access-token");
        String accessToken = loginResponse.path("data").path("accessToken").asText();

        mockMvc.perform(post("/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(accessToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value("AC-006"))
                .andExpect(jsonPath("$.data.message").value("refreshToken 만료"));
    }

    @Test
    void refreshToken_returnsForbiddenWhenUserIsWithdrawn() throws Exception {
        String appleSub = "apple-sub-refresh-withdrawn";
        JsonNode loginResponse = login(appleSub, "identity-refresh-withdrawn");
        String accessToken = loginResponse.path("data").path("accessToken").asText();
        String refreshToken = loginResponse.path("data").path("refreshToken").asText();
        User user = findUserByAppleSub(appleSub);

        mockMvc.perform(delete("/auth/withdraw")
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk());

        assertThat(authTokenRepository.findByUserId(user.getId())).isEmpty();

        mockMvc.perform(post("/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value("USER-002"))
                .andExpect(jsonPath("$.data.message").value("탈퇴한 사용자입니다."));
    }

    @Test
    void refreshToken_returnsNotFoundWhenTokenUserDoesNotExist() throws Exception {
        String refreshToken = jwtTokenProvider.generateRefreshToken(999_999L);

        mockMvc.perform(post("/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value("USER-001"))
                .andExpect(jsonPath("$.data.message").value("사용자를 찾을 수 없습니다."));
    }

    @Test
    void logout_keepsSuccessResponseContract() throws Exception {
        String appleSub = "apple-sub-logout";
        JsonNode loginResponse = login(appleSub, "identity-logout");
        Long userId = findUserByAppleSub(appleSub).getId();
        String accessToken = loginResponse.path("data").path("accessToken").asText();

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());

        assertThat(authTokenRepository.findByUserId(userId)).isEmpty();
    }

    @Test
    void protectedApi_returnsUnauthorizedWhenRefreshTokenIsUsedAsBearerToken() throws Exception {
        JsonNode loginResponse = login("apple-sub-refresh-as-bearer", "identity-refresh-as-bearer");
        String refreshToken = loginResponse.path("data").path("refreshToken").asText();

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", bearer(refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value("AC-006"))
                .andExpect(jsonPath("$.data.message").value("refreshToken 만료"));
    }

    @Test
    void withdraw_keepsSuccessResponseContract() throws Exception {
        String appleSub = "apple-sub-withdraw";
        JsonNode loginResponse = login(appleSub, "identity-withdraw");
        User user = findUserByAppleSub(appleSub);
        String accessToken = loginResponse.path("data").path("accessToken").asText();

        mockMvc.perform(delete("/auth/withdraw")
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());

        User withdrawnUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(withdrawnUser.isWithdrawn()).isTrue();
        assertThat(withdrawnUser.getDeletedAt()).isNotNull();
    }

    @Test
    void checkNickname_keepsResponseContract() throws Exception {
        saveUser("runner-existing");

        mockMvc.perform(get("/onboarding/nickname/check")
                        .param("nickname", "runner-existing"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.available").value(false));
    }

    @Test
    void onboardingSaveAndGet_keepResponseContract() throws Exception {
        User user = saveUser(null);
        String accessToken = accessToken(user.getId());

        mockMvc.perform(post("/onboarding")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "runner1",
                                  "gender": "F",
                                  "birth": "1998-05-12",
                                  "height": 165,
                                  "weight": 52,
                                  "profileImage": "profile.png"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(get("/onboarding")
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nickname").value("runner1"))
                .andExpect(jsonPath("$.data.gender").value("F"))
                .andExpect(jsonPath("$.data.birth").value("1998-05-12"))
                .andExpect(jsonPath("$.data.height").value(165))
                .andExpect(jsonPath("$.data.weight").value(52))
                .andExpect(jsonPath("$.data.displayProfileImage").value("profile.png"))
                .andExpect(jsonPath("$.data.customProfileImage").value("profile.png"));
    }

    @Test
    void onboardingUpdate_keepsResponseContract() throws Exception {
        User user = saveUser("runner-before");
        ReflectionTestUtils.setField(user, "gender", "F");
        ReflectionTestUtils.setField(user, "birth", LocalDate.of(1998, 5, 12));
        ReflectionTestUtils.setField(user, "height", 165);
        ReflectionTestUtils.setField(user, "weight", 52);
        ReflectionTestUtils.setField(user, "profileImage", "https://cdn.example.com/original.png");
        userRepository.save(user);
        String accessToken = accessToken(user.getId());

        mockMvc.perform(put("/onboarding")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "runner-after",
                                  "gender": "M",
                                  "birth": "1997-01-03",
                                  "height": 178,
                                  "weight": 70,
                                  "profileImage": "https://cdn.example.com/updated.png"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(get("/onboarding")
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("runner-after"))
                .andExpect(jsonPath("$.data.gender").value("M"))
                .andExpect(jsonPath("$.data.birth").value("1997-01-03"))
                .andExpect(jsonPath("$.data.height").value(178))
                .andExpect(jsonPath("$.data.weight").value(70))
                .andExpect(jsonPath("$.data.displayProfileImage").value("https://cdn.example.com/updated.png"))
                .andExpect(jsonPath("$.data.customProfileImage").value("https://cdn.example.com/updated.png"));
    }

    @Test
    void onboardingUpdate_returnsConflictWhenNicknameAlreadyExists() throws Exception {
        saveUser("runner-taken");
        User user = saveUser("runner-before-conflict");
        String accessToken = accessToken(user.getId());

        mockMvc.perform(put("/onboarding")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "runner-taken",
                                  "gender": "M",
                                  "birth": "1997-01-03",
                                  "height": 178,
                                  "weight": 70,
                                  "profileImage": null
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value("USER-003"))
                .andExpect(jsonPath("$.data.message").value("이미 사용 중인 닉네임입니다."));
    }

    @Test
    void onboardingSave_returnsBadRequestWhenBirthFormatIsInvalid() throws Exception {
        User user = saveUser(null);
        String accessToken = accessToken(user.getId());

        mockMvc.perform(post("/onboarding")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "runner-invalid-birth",
                                  "gender": "F",
                                  "birth": "1998/05/12",
                                  "height": 165,
                                  "weight": 52,
                                  "profileImage": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.data.message").value(INVALID_REQUEST_BODY_MESSAGE))
                .andExpect(jsonPath("$.data.errors.birth").value(INVALID_LOCAL_DATE_MESSAGE));
    }

    @Test
    void getOnboarding_returnsNotFoundWhenTokenUserDoesNotExist() throws Exception {
        String accessToken = accessToken(999_999L);

        mockMvc.perform(get("/onboarding")
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value("USER-001"))
                .andExpect(jsonPath("$.data.message").value("사용자를 찾을 수 없습니다."));
    }

    @Test
    void getMyRanking_returnsForbiddenWhenUserIsWithdrawn() throws Exception {
        User user = saveUser("runner-withdrawn");
        user.withdraw();
        userRepository.save(user);

        mockMvc.perform(get("/ranking/me")
                        .header("Authorization", bearer(accessToken(user.getId()))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value("USER-002"))
                .andExpect(jsonPath("$.data.message").value("탈퇴한 사용자입니다."));
    }

    @Test
    void uploadProfileImage_savesFileAndServesItByReturnedUrl() throws Exception {
        User user = saveUser("runner-image");
        String accessToken = accessToken(user.getId());
        byte[] imageBytes = "fake-png-content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile profileImage = new MockMultipartFile(
                "profileImage",
                "avatar.png",
                "image/png",
                imageBytes
        );

        MvcResult result = mockMvc.perform(multipart("/onboarding/profile-image")
                        .file(profileImage)
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.imageUrl", startsWith("/profiles/custom/")))
                .andReturn();

        String imageUrl = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("imageUrl")
                .asText();

        mockMvc.perform(get("/onboarding")
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayProfileImage").value(imageUrl))
                .andExpect(jsonPath("$.data.customProfileImage").value(imageUrl));

        mockMvc.perform(get(imageUrl))
                .andExpect(status().isOk())
                .andExpect(content().bytes(imageBytes));

        Path storedFile = TEST_PROFILE_IMAGE_UPLOAD_DIR.resolve(imageUrl.substring("/profiles/custom/".length()));
        assertThat(Files.exists(storedFile)).isTrue();
    }

    @Test
    void estimateTier_keepsResponseContract() throws Exception {
        User user = saveUser("runner-tier");
        String accessToken = accessToken(user.getId());

        mockMvc.perform(post("/onboarding/estimate-tier")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "distanceKm": 5.0,
                                  "paceSecPerKm": 300
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tierCode").value("DEER"))
                .andExpect(jsonPath("$.data.displayName").value("사슴"))
                .andExpect(jsonPath("$.data.tierGrade").value("B"))
                .andExpect(jsonPath("$.data.score").value(1.24));
        assertThat(weeklyTierRepository.findAll()).isEmpty();
    }

    @Test
    void estimateTier_returnsForbiddenWhenUserIsWithdrawn() throws Exception {
        User user = saveUser("runner-tier-withdrawn");
        user.withdraw();
        userRepository.save(user);

        mockMvc.perform(post("/onboarding/estimate-tier")
                        .header("Authorization", bearer(accessToken(user.getId())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "distanceKm": 5.0,
                                  "paceSecPerKm": 300
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value("USER-002"))
                .andExpect(jsonPath("$.data.message").value("탈퇴한 사용자입니다."));
    }

    @Test
    void getOnboarding_returnsTurtleProfileImageWhenOnboardingCompletedWithoutCurrentTier() throws Exception {
        User user = saveUser(null);
        String accessToken = accessToken(user.getId());

        mockMvc.perform(post("/onboarding")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "runner-turtle",
                                  "gender": "F",
                                  "birth": "1998-05-12",
                                  "height": 165,
                                  "weight": 52,
                                  "profileImage": null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(get("/onboarding")
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.displayProfileImage").value("https://api.dalryeo.store/profiles/tiers/turtle.png"))
                .andExpect(jsonPath("$.data.customProfileImage").isEmpty());
    }

    @Test
    void getOnboarding_returnsTierDefaultProfileImageWhenCustomImageDoesNotExist() throws Exception {
        User user = saveUser("runner-tier-image");
        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        weeklyTierRepository.save(WeeklyTier.builder()
                .userId(user.getId())
                .weekStartDate(weekStart)
                .tierCode("DEER")
                .tierScore(124)
                .build());

        mockMvc.perform(get("/onboarding")
                .header("Authorization", bearer(accessToken(user.getId()))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.displayProfileImage").value("https://api.dalryeo.store/profiles/tiers/deer.png"))
                .andExpect(jsonPath("$.data.customProfileImage").isEmpty());
    }

    @Test
    void getOnboarding_prefersCurrentWeekRecordTierImageOverWeeklyTierSnapshot() throws Exception {
        User user = saveUser("runner-tier-priority");
        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        weeklyTierRepository.save(WeeklyTier.builder()
                .userId(user.getId())
                .weekStartDate(weekStart)
                .tierCode("CHEETAH")
                .tierScore(157)
                .build());
        saveRecord(user.getId(), 5.0, 300, LocalDateTime.now().minusHours(1));

        mockMvc.perform(get("/onboarding")
                .header("Authorization", bearer(accessToken(user.getId()))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.displayProfileImage").value("https://api.dalryeo.store/profiles/tiers/deer.png"))
                .andExpect(jsonPath("$.data.customProfileImage").isEmpty());
    }

    @Test
    void saveRecord_keepsResponseContract() throws Exception {
        User user = saveUser("runner-record");
        String accessToken = accessToken(user.getId());

        mockMvc.perform(post("/records")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "platform": "IOS",
                                  "distanceKm": 5.0,
                                  "durationSec": 1500,
                                  "avgPaceSecPerKm": 300,
                                  "avgHeartRate": 150,
                                  "caloriesKcal": 300,
                                  "startAt": "2026-03-31T07:00:00+09:00",
                                  "endAt": "2026-03-31T07:25:00+09:00"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.recordId").isNumber());

        recordOutboxEventProcessor.processDueEvents(10, 300);

        LocalDate weekStart = LocalDate.of(2026, 3, 30);
        var weeklyStats = weeklyUserStatsRepository.findByUserIdAndWeekStartDate(user.getId(), weekStart)
                .orElseThrow();
        assertThat(weeklyStats.getRunCount()).isEqualTo(1);
        assertThat(weeklyStats.totalDistanceAsDouble()).isEqualTo(5.0);
        assertThat(weeklyStats.getAvgPaceSecPerKm()).isEqualTo(300);
        assertThat(weeklyStats.tierScoreAsDouble()).isEqualTo(1.24);
    }

    @Test
    void saveRecord_returnsBadRequestWhenDtoValidationFails() throws Exception {
        User user = saveUser("runner-record-invalid-dto");
        String accessToken = accessToken(user.getId());
        long beforeCount = runningRecordRepository.count();
        long beforeStatsCount = weeklyUserStatsRepository.count();

        mockMvc.perform(post("/records")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "platform": "IOS",
                                  "distanceKm": 0.05,
                                  "durationSec": 30,
                                  "avgPaceSecPerKm": 100,
                                  "avgHeartRate": 10,
                                  "caloriesKcal": 0,
                                  "startAt": "2026-03-31T07:00:00+09:00",
                                  "endAt": "2026-03-31T07:00:30+09:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.data.message").isString())
                .andExpect(jsonPath("$.data.errors.distanceKm").value("거리는 0.1km 이상이어야 합니다."))
                .andExpect(jsonPath("$.data.errors.durationSec").value("시간은 60초 이상이어야 합니다."))
                .andExpect(jsonPath("$.data.errors.avgPaceSecPerKm").value("평균 페이스는 120초/km 이상이어야 합니다."));

        assertThat(runningRecordRepository.count()).isEqualTo(beforeCount);
        assertThat(weeklyUserStatsRepository.count()).isEqualTo(beforeStatsCount);
    }

    @Test
    void saveRecord_returnsBadRequestWhenDomainValidationFails() throws Exception {
        User user = saveUser("runner-record-invalid-domain");
        String accessToken = accessToken(user.getId());
        long beforeCount = runningRecordRepository.count();
        long beforeStatsCount = weeklyUserStatsRepository.count();

        mockMvc.perform(post("/records")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "platform": "IOS",
                                  "distanceKm": 5.0,
                                  "durationSec": 1500,
                                  "avgPaceSecPerKm": 300,
                                  "avgHeartRate": 150,
                                  "caloriesKcal": 300,
                                  "startAt": "2026-03-31T07:00:00+09:00",
                                  "endAt": "2026-03-31T07:00:00+09:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value("RC-001"))
                .andExpect(jsonPath("$.data.message").value("종료 시간은 시작 시간보다 뒤여야 합니다."));

        assertThat(runningRecordRepository.count()).isEqualTo(beforeCount);
        assertThat(weeklyUserStatsRepository.count()).isEqualTo(beforeStatsCount);
    }

    @Test
    void saveRecord_returnsBadRequestWhenDateTimeOffsetIsMissing() throws Exception {
        User user = saveUser("runner-record-missing-offset");
        String accessToken = accessToken(user.getId());
        long beforeCount = runningRecordRepository.count();
        long beforeStatsCount = weeklyUserStatsRepository.count();

        mockMvc.perform(post("/records")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "platform": "IOS",
                                  "distanceKm": 5.0,
                                  "durationSec": 1500,
                                  "avgPaceSecPerKm": 300,
                                  "avgHeartRate": 150,
                                  "caloriesKcal": 300,
                                  "startAt": "2026-03-31T07:00:00",
                                  "endAt": "2026-03-31T07:25:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.data.message").value(INVALID_REQUEST_BODY_MESSAGE))
                .andExpect(jsonPath("$.data.errors.startAt").value(INVALID_OFFSET_DATE_TIME_MESSAGE));

        assertThat(runningRecordRepository.count()).isEqualTo(beforeCount);
        assertThat(weeklyUserStatsRepository.count()).isEqualTo(beforeStatsCount);
    }

    @Test
    void saveRecord_returnsBadRequestWhenNumberFieldTypeIsInvalid() throws Exception {
        User user = saveUser("runner-record-invalid-number");
        String accessToken = accessToken(user.getId());
        long beforeCount = runningRecordRepository.count();
        long beforeStatsCount = weeklyUserStatsRepository.count();

        mockMvc.perform(post("/records")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "platform": "IOS",
                                  "distanceKm": "fast",
                                  "durationSec": 1500,
                                  "avgPaceSecPerKm": 300,
                                  "avgHeartRate": 150,
                                  "caloriesKcal": 300,
                                  "startAt": "2026-03-31T07:00:00+09:00",
                                  "endAt": "2026-03-31T07:25:00+09:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.data.message").value(INVALID_REQUEST_BODY_MESSAGE))
                .andExpect(jsonPath("$.data.errors.distanceKm").value(INVALID_NUMBER_MESSAGE));

        assertThat(runningRecordRepository.count()).isEqualTo(beforeCount);
        assertThat(weeklyUserStatsRepository.count()).isEqualTo(beforeStatsCount);
    }

    @Test
    void saveRecord_returnsBadRequestWhenJsonSyntaxIsInvalid() throws Exception {
        User user = saveUser("runner-record-invalid-json");
        String accessToken = accessToken(user.getId());
        long beforeCount = runningRecordRepository.count();
        long beforeStatsCount = weeklyUserStatsRepository.count();

        mockMvc.perform(post("/records")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "platform": "IOS",
                                  "distanceKm": 5.0,
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.data.message").value(INVALID_REQUEST_BODY_MESSAGE))
                .andExpect(jsonPath("$.data.errors").doesNotExist());

        assertThat(runningRecordRepository.count()).isEqualTo(beforeCount);
        assertThat(weeklyUserStatsRepository.count()).isEqualTo(beforeStatsCount);
    }

    @Test
    void getRecordSummary_keepsSummaryResponseContract() throws Exception {
        User user = saveUser(null);
        saveRecord(user.getId(), 5.0, 300, LocalDateTime.now().minusHours(2));

        mockMvc.perform(get("/records/summary")
                        .header("Authorization", bearer(accessToken(user.getId()))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.currentTier").value("DEER"))
                .andExpect(jsonPath("$.data.currentTierGrade").value("B"))
                .andExpect(jsonPath("$.data.weeklyCount").value(1))
                .andExpect(jsonPath("$.data.weeklyAvgPace").value(300))
                .andExpect(jsonPath("$.data.weeklyDistance").value(5.0));
    }

    @Test
    void getWeeklyRecords_keepsWeeklyRecordsResponseContract() throws Exception {
        User user = saveUser("runner-weekly");
        RunningRecord record = saveRecord(user.getId(), 5.0, 300, LocalDateTime.now().minusHours(3));

        mockMvc.perform(get("/records/weekly")
                        .header("Authorization", bearer(accessToken(user.getId()))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.weeklyCount").value(1))
                .andExpect(jsonPath("$.data.records[0].recordId").value(record.getId()))
                .andExpect(jsonPath("$.data.records[0].platform").value("IOS"))
                .andExpect(jsonPath("$.data.records[0].distanceKm").value(5.0))
                .andExpect(jsonPath("$.data.records[0].tierCode").value("DEER"));
    }

    @Test
    void getCurrentWeeklySummary_keepsResponseContract() throws Exception {
        User user = saveUser("runner-summary");
        saveRecord(user.getId(), 5.0, 300, LocalDateTime.now().minusHours(4));

        mockMvc.perform(get("/weekly/summary/current")
                        .header("Authorization", bearer(accessToken(user.getId()))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.currentTier").value("DEER"))
                .andExpect(jsonPath("$.data.currentTierGrade").value("B"))
                .andExpect(jsonPath("$.data.weeklyCount").value(1))
                .andExpect(jsonPath("$.data.weeklyAvgPace").value(300))
                .andExpect(jsonPath("$.data.weeklyDistance").value(5.0));
    }

    @Test
    void getWeeklySummaryList_keepsResponseContract() throws Exception {
        User user = saveUser("runner-summary-list");
        saveRecord(user.getId(), 5.0, 300, LocalDateTime.now().minusHours(5));
        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        mockMvc.perform(get("/weekly/summary/list")
                        .header("Authorization", bearer(accessToken(user.getId()))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].weekStart").value(weekStart.toString()))
                .andExpect(jsonPath("$.data[0].tierCode").value("DEER"))
                .andExpect(jsonPath("$.data[0].tierGrade").value("B"))
                .andExpect(jsonPath("$.data[0].runCount").value(1))
                .andExpect(jsonPath("$.data[0].averagePace").value(300))
                .andExpect(jsonPath("$.data[0].weeklyDistance").value(5.0));
    }

    @Test
    void getCurrentWeeklyTier_keepsWeeklyTierResponseContract() throws Exception {
        User user = saveUser("runner-weekly-tier");
        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        weeklyTierRepository.save(WeeklyTier.builder()
                .userId(user.getId())
                .weekStartDate(weekStart)
                .tierCode("CHEETAH")
                .tierScore(157)
                .build());

        mockMvc.perform(get("/weekly/tiers/current")
                        .header("Authorization", bearer(accessToken(user.getId()))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.weekStartDate").value(weekStart.toString()))
                .andExpect(jsonPath("$.data.tierCode").value("CHEETAH"))
                .andExpect(jsonPath("$.data.tierGrade").value("S"))
                .andExpect(jsonPath("$.data.tierScore").value(1.57));
    }

    @Test
    void getWeeklyScoreRanking_keepsResponseContract() throws Exception {
        User alpha = saveUser("alpha");
        User beta = saveUser("beta");
        saveRecord(alpha.getId(), 5.0, 300, LocalDateTime.now().minusHours(2));
        saveRecord(beta.getId(), 10.0, 300, LocalDateTime.now().minusHours(1));

        mockMvc.perform(get("/ranking/weekly/score"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].rank").value(1))
                .andExpect(jsonPath("$.data[0].nickname").value("beta"))
                .andExpect(jsonPath("$.data[0].tierCode").value("DEER"))
                .andExpect(jsonPath("$.data[0].tierGrade").value("B"))
                .andExpect(jsonPath("$.data[0].tierScore").value(1.27))
                .andExpect(jsonPath("$.data[1].rank").value(2))
                .andExpect(jsonPath("$.data[1].nickname").value("alpha"));
    }

    @Test
    void getWeeklyDistanceRanking_keepsResponseContract() throws Exception {
        User alpha = saveUser("alpha");
        User beta = saveUser("beta");
        saveRecord(alpha.getId(), 5.0, 300, LocalDateTime.now().minusHours(2));
        saveRecord(beta.getId(), 10.0, 300, LocalDateTime.now().minusHours(1));

        mockMvc.perform(get("/ranking/weekly/distance"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].rank").value(1))
                .andExpect(jsonPath("$.data[0].nickname").value("beta"))
                .andExpect(jsonPath("$.data[0].weeklyDistance").value(10.0))
                .andExpect(jsonPath("$.data[1].rank").value(2))
                .andExpect(jsonPath("$.data[1].nickname").value("alpha"));
    }

    @Test
    void getMyRanking_keepsResponseContract() throws Exception {
        User alpha = saveUser("alpha");
        User beta = saveUser("beta");
        saveRecord(alpha.getId(), 5.0, 300, LocalDateTime.now().minusHours(2));
        saveRecord(beta.getId(), 10.0, 300, LocalDateTime.now().minusHours(1));

        mockMvc.perform(get("/ranking/me")
                        .header("Authorization", bearer(accessToken(alpha.getId()))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nickname").value("alpha"))
                .andExpect(jsonPath("$.data.scoreRank").value(2))
                .andExpect(jsonPath("$.data.distanceRank").value(2))
                .andExpect(jsonPath("$.data.tierCode").value("DEER"))
                .andExpect(jsonPath("$.data.tierGrade").value("B"))
                .andExpect(jsonPath("$.data.tierScore").value(1.24))
                .andExpect(jsonPath("$.data.weeklyAvgPace").value(300))
                .andExpect(jsonPath("$.data.weeklyDistance").value(5.0));
    }

    @Test
    void getAnalysisRecords_keepsResponseContract() throws Exception {
        User user = saveUser("runner-analysis");
        RunningRecord record = saveRecord(user.getId(), 5.0, 300, LocalDateTime.now().minusDays(1));

        mockMvc.perform(get("/analysis/records")
                        .header("Authorization", bearer(accessToken(user.getId())))
                        .param("page", "1")
                        .param("sort", "latest"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].recordId").value(record.getId()))
                .andExpect(jsonPath("$.data.records[0].distanceKm").value(5.0))
                .andExpect(jsonPath("$.data.records[0].durationSec").value(1500))
                .andExpect(jsonPath("$.data.records[0].avgPaceSecPerKm").value(300))
                .andExpect(jsonPath("$.data.records[0].bpm").value(150));
    }

    @Test
    void getAnalysisRecordDetail_keepsResponseContract() throws Exception {
        User user = saveUser("runner-analysis-detail");
        RunningRecord record = saveRecord(user.getId(), 5.0, 300, LocalDateTime.of(2026, 3, 31, 7, 0));

        mockMvc.perform(get("/analysis/records/{recordId}", record.getId())
                        .header("Authorization", bearer(accessToken(user.getId()))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.recordId").value(record.getId()))
                .andExpect(jsonPath("$.data.platform").value("IOS"))
                .andExpect(jsonPath("$.data.distanceKm").value(5.0))
                .andExpect(jsonPath("$.data.durationSec").value(1500))
                .andExpect(jsonPath("$.data.avgPaceSecPerKm").value(300))
                .andExpect(jsonPath("$.data.avgHeartRate").value(150))
                .andExpect(jsonPath("$.data.caloriesKcal").value(300))
                .andExpect(jsonPath("$.data.startAt").value("2026-03-31T07:00:00"))
                .andExpect(jsonPath("$.data.endAt").value("2026-03-31T07:25:00"));
    }

    private JsonNode login(String appleSub, String identityToken) throws Exception {
        when(appleOAuthValidator.validateAndExtractAppleId(identityToken)).thenReturn(appleSub);

        MvcResult result = mockMvc.perform(post("/auth/oauth/apple")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identityToken": "%s"
                                }
                                """.formatted(identityToken)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private User findUserByAppleSub(String appleSub) {
        Long userId = oAuthClientRepository.findByProviderAndProviderId("APPLE", appleSub)
                .orElseThrow()
                .getUserId();
        return userRepository.findById(userId).orElseThrow();
    }

    private User saveUser(String nickname) {
        User user = User.builder()
                .status(UserStatus.NORMAL)
                .build();
        if (nickname != null) {
            ReflectionTestUtils.setField(user, "nickname", nickname);
        }
        return userRepository.save(user);
    }

    private RunningRecord saveRecord(Long userId, double distanceKm, int avgPaceSecPerKm, LocalDateTime startAt) {
        RunningRecordRequest request = new RunningRecordRequest();
        ReflectionTestUtils.setField(request, "platform", "IOS");
        ReflectionTestUtils.setField(request, "distanceKm", distanceKm);
        ReflectionTestUtils.setField(request, "durationSec", (int) Math.round(distanceKm * avgPaceSecPerKm));
        ReflectionTestUtils.setField(request, "avgPaceSecPerKm", avgPaceSecPerKm);
        ReflectionTestUtils.setField(request, "avgHeartRate", 150);
        ReflectionTestUtils.setField(request, "caloriesKcal", 300);
        ReflectionTestUtils.setField(request, "startAt", startAt.atOffset(TEST_ZONE_OFFSET));
        ReflectionTestUtils.setField(request, "endAt", startAt.plusSeconds((long) Math.round(distanceKm * avgPaceSecPerKm)).atOffset(TEST_ZONE_OFFSET));

        RecordIdResponse response = recordService.saveRecord(userId, request);
        recordOutboxEventProcessor.processDueEvents(10, 300);
        return runningRecordRepository.findById(response.getRecordId()).orElseThrow();
    }

    private String accessToken(Long userId) {
        return jwtTokenProvider.generateAccessToken(userId);
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private void cleanProfileImageDirectory() {
        if (!Files.exists(TEST_PROFILE_IMAGE_UPLOAD_DIR)) {
            return;
        }

        try (var walk = Files.walk(TEST_PROFILE_IMAGE_UPLOAD_DIR)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new IllegalStateException("테스트 프로필 이미지 디렉터리를 정리할 수 없습니다.", e);
                        }
                    });
        } catch (IOException e) {
            throw new IllegalStateException("테스트 프로필 이미지 디렉터리를 조회할 수 없습니다.", e);
        }
    }

    private void seedTierMetadata() {
        tierRepository.saveAll(java.util.List.of(
                tier("CHEETAH", "치타", 1.50, 999.99),
                tier("DEER", "사슴", 1.20, 1.49),
                tier("HUSKY", "허스키", 1.00, 1.19),
                tier("FOX", "여우", 0.86, 0.99),
                tier("WATERDEER", "고라니", 0.75, 0.85),
                tier("SHEEP", "양", 0.67, 0.74),
                tier("RABBIT", "토끼", 0.60, 0.66),
                tier("PANDA", "판다", 0.55, 0.59),
                tier("DUCK", "오리", 0.46, 0.54),
                tier("TURTLE", "거북이", 0.00, 0.45)
        ));
        tierGradeRepository.saveAll(java.util.List.of(
                tierGrade("CHEETAH", "G", 1.64, 999.99),
                tierGrade("CHEETAH", "S", 1.57, 1.63),
                tierGrade("CHEETAH", "B", 1.50, 1.56),
                tierGrade("DEER", "G", 1.39, 1.49),
                tierGrade("DEER", "S", 1.29, 1.38),
                tierGrade("DEER", "B", 1.20, 1.28),
                tierGrade("HUSKY", "G", 1.13, 1.19),
                tierGrade("HUSKY", "S", 1.06, 1.12),
                tierGrade("HUSKY", "B", 1.00, 1.05),
                tierGrade("FOX", "G", 0.95, 0.99),
                tierGrade("FOX", "S", 0.90, 0.94),
                tierGrade("FOX", "B", 0.86, 0.89),
                tierGrade("WATERDEER", "G", 0.82, 0.85),
                tierGrade("WATERDEER", "S", 0.78, 0.81),
                tierGrade("WATERDEER", "B", 0.75, 0.77),
                tierGrade("SHEEP", "G", 0.72, 0.74),
                tierGrade("SHEEP", "S", 0.69, 0.71),
                tierGrade("SHEEP", "B", 0.67, 0.68),
                tierGrade("RABBIT", "G", 0.64, 0.66),
                tierGrade("RABBIT", "S", 0.62, 0.63),
                tierGrade("RABBIT", "B", 0.60, 0.61),
                tierGrade("PANDA", "G", 0.58, 0.59),
                tierGrade("PANDA", "S", 0.56, 0.57),
                tierGrade("PANDA", "B", 0.55, 0.55),
                tierGrade("DUCK", "G", 0.52, 0.54),
                tierGrade("DUCK", "S", 0.49, 0.51),
                tierGrade("DUCK", "B", 0.46, 0.48)
        ));
    }

    private Tier tier(String tierCode, String displayName, double minScore, double maxScore) {
        return Tier.builder()
                .tierCode(tierCode)
                .displayName(displayName)
                .minScore(minScore)
                .maxScore(maxScore)
                .defaultProfileImage(defaultProfileImagePath(tierCode))
                .build();
    }

    private String defaultProfileImagePath(String tierCode) {
        return "/profiles/tiers/" + tierCode.toLowerCase() + ".png";
    }

    private TierGrade tierGrade(String tierCode, String grade, double minScore, double maxScore) {
        return TierGrade.builder()
                .tierCode(tierCode)
                .grade(grade)
                .minScore(minScore)
                .maxScore(maxScore)
                .build();
    }
}
