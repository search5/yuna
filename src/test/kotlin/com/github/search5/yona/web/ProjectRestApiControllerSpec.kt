package com.github.search5.yona.web

import com.github.search5.yona.config.ApiTokenAuthenticationFilter
import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.apitoken.ApiToken
import com.github.search5.yona.domain.organization.OrganizationRepository
import com.github.search5.yona.domain.organization.OrganizationUserRepository
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.pullrequest.ReviewCommentRepository
import com.github.search5.yona.domain.pullrequest.CommitCommentRepository
import com.github.search5.yona.domain.milestone.MilestoneRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

// yona-wiki P3-02 Step6 — ProjectRestApiController(/api/v1/projects/{owner}[/{project}])는 새
// 서비스 로직 없이 ProjectRepository + 실제 AccessControl(웹 UI와 동일한 가시성 규칙)만으로
// 구성된다. 이 스펙은 공개/비공개 프로젝트 가시성 규칙과 404/403 분기를 검증한다.
//
// AccessControl 기반 익명/비멤버 403·404 케이스로 "권한 없는 요청 거부"를 검증한다(개별 조회는
// ApiTokenAuthenticationFilter가 이미 스코프 밖 요청을 403으로 막으므로 이 컨트롤러 자체는 별도
// 스코프 검증이 필요 없다 — 필터 레벨 검증은 config/ApiTokenScopedMetadataAndListAuthorizationIntegrationSpec
// 참고).
//
// yona-wiki P3-02 Step6.5 — 목록(list())은 request attribute(SCOPED_API_TOKEN_ATTRIBUTE)로 넘어온
// ApiToken에 따라 AccessControl 통과 목록을 한 번 더 좁힌다. 아래 "GET /api/v1/projects/{owner} -
// 스코프 토큰 기반 필터링(Step6.5)" describe가 이 분기(속성 없음/전체스코프/선택스코프)를 컨트롤러
// 단위로 검증한다.
class ProjectRestApiControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val userRepository = mockk<UserRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    every { organizationUserRepository.findByOrganizationIdAndUserId(any(), any()) } returns Optional.empty()
    val organizationRepository = mockk<OrganizationRepository>()
    val issueRepository = mockk<IssueRepository>()
    val postingRepository = mockk<PostingRepository>()
    val reviewCommentRepository = mockk<ReviewCommentRepository>()
    val commitCommentRepository = mockk<CommitCommentRepository>()
    val milestoneRepository = mockk<MilestoneRepository>()
    val accessControl = AccessControl(
        projectUserRepository, organizationUserRepository,
        userRepository, organizationRepository,
        issueRepository, postingRepository,
        reviewCommentRepository, commitCommentRepository,
        milestoneRepository
    )

    val controller = ProjectRestApiController(projectRepository, userRepository, accessControl)
    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    beforeTest {
        clearMocks(projectRepository, userRepository, projectUserRepository)
    }

    val publicProject = Project(id = 1L, owner = "yona", name = "public-repo", overview = "공개 저장소", projectScope = ProjectScope.PUBLIC)
    val privateProject = Project(id = 2L, owner = "yona", name = "private-repo", overview = "비공개 저장소", projectScope = ProjectScope.PRIVATE)
    val outsider = User(id = 10L, loginId = "outsider", name = "외부인")
    val auth = UsernamePasswordAuthenticationToken("outsider", "password")

    describe("GET /api/v1/projects/{owner}") {
        it("비로그인 상태에서도 공개 프로젝트만 목록에 포함한다") {
            every { projectRepository.findByOwner("yona") } returns listOf(publicProject, privateProject)

            mockMvc.perform(get("/api/v1/projects/yona"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("public-repo"))
        }
    }

    describe("GET /api/v1/projects/{owner}/{project}") {
        it("프로젝트가 없으면 404를 반환한다") {
            every { projectRepository.findByOwnerAndName("yona", "unknown") } returns Optional.empty()

            mockMvc.perform(get("/api/v1/projects/yona/unknown"))
                .andExpect(status().isNotFound)
        }

        it("공개 프로젝트는 비로그인 사용자도 조회할 수 있다") {
            every { projectRepository.findByOwnerAndName("yona", "public-repo") } returns Optional.of(publicProject)

            mockMvc.perform(get("/api/v1/projects/yona/public-repo"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("public-repo"))
        }

        it("비공개 프로젝트는 멤버가 아닌 로그인 사용자에게 403을 반환한다") {
            every { projectRepository.findByOwnerAndName("yona", "private-repo") } returns Optional.of(privateProject)
            every { userRepository.findByLoginId("outsider") } returns Optional.of(outsider)

            mockMvc.perform(get("/api/v1/projects/yona/private-repo").principal(auth))
                .andExpect(status().isForbidden)
        }
    }

    // yona-wiki P3-02 Step6.5 — "프로젝트 목록 API 스코프 필터링 설계" 검증. request attribute에
    // ApiTokenAuthenticationFilter.SCOPED_API_TOKEN_ATTRIBUTE로 넘어온 ApiToken 유무/종류에 따라
    // AccessControl 통과 목록을 한 번 더 좁혀야 한다.
    describe("GET /api/v1/projects/{owner} - 스코프 토큰 기반 필터링(Step6.5)") {
        val otherPublicProject = Project(id = 3L, owner = "yona", name = "public-repo-2", projectScope = ProjectScope.PUBLIC)

        it("SCOPED_API_TOKEN 속성이 없으면(세션/레거시 인증) 기존 AccessControl 통과 목록을 그대로 반환한다") {
            every { projectRepository.findByOwner("yona") } returns listOf(publicProject, otherPublicProject)

            mockMvc.perform(get("/api/v1/projects/yona"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(2))
        }

        it("전체 저장소 스코프(allRepositories=true) 토큰이면 AccessControl 통과 목록을 모두 반환한다") {
            every { projectRepository.findByOwner("yona") } returns listOf(publicProject, otherPublicProject)
            val allRepoToken = ApiToken(owner = null, tokenHash = "irrelevant", allRepositories = true)

            mockMvc.perform(
                get("/api/v1/projects/yona")
                    .requestAttr(ApiTokenAuthenticationFilter.SCOPED_API_TOKEN_ATTRIBUTE, allRepoToken)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(2))
        }

        it("선택 저장소 스코프 토큰이면 scopedProjects에 포함된 프로젝트만 반환한다") {
            every { projectRepository.findByOwner("yona") } returns listOf(publicProject, otherPublicProject)
            val selectiveToken = ApiToken(
                owner = null,
                tokenHash = "irrelevant",
                allRepositories = false,
                scopedProjects = mutableSetOf(otherPublicProject)
            )

            mockMvc.perform(
                get("/api/v1/projects/yona")
                    .requestAttr(ApiTokenAuthenticationFilter.SCOPED_API_TOKEN_ATTRIBUTE, selectiveToken)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("public-repo-2"))
        }
    }
})
