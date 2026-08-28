package com.github.search5.yona.web

import com.github.search5.yona.config.security.AccessControl
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
// [설계상 알려진 한계] 이 컨트롤러의 URL은 ApiTokenAuthenticationFilter의 scopedApiPattern(owner/
// project/resource 3단 세그먼트 필수)과 맞지 않아 Fine-grained 스코프 토큰으로는 아직 호출할 수
// 없다(컨트롤러 파일 상단 주석 및 계획 문서 "리스크/미결정 사항" 참고) — 그래서 이 스펙은
// ApiTokenScopedAuthorizationIntegrationSpec 패턴(스코프 토큰 403) 대신, 이 API가 실제로 적용하는
// 인가 메커니즘인 AccessControl 기반 익명/비멤버 403·404 케이스로 "권한 없는 요청 거부"를 검증한다.
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
})
