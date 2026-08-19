package com.github.search5.yona.web

import com.github.search5.yona.domain.issue.Issue
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.organization.Organization
import com.github.search5.yona.domain.organization.OrganizationUser
import com.github.search5.yona.domain.organization.OrganizationUserRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUser
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.role.Role
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

class MentionControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    val issueRepository = mockk<IssueRepository>()
    val userRepository = mockk<UserRepository>()

    val mentionController = MentionController(
        projectRepository,
        projectUserRepository,
        organizationUserRepository,
        issueRepository,
        userRepository
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(mentionController).build()

    beforeTest {
        io.mockk.clearMocks(projectRepository, projectUserRepository, organizationUserRepository, issueRepository, userRepository)
    }

    describe("GET /api/{owner}/{projectName}/mentionList (P1-14)") {
        val me = User(id = 1L, loginId = "me", name = "나")
        val meAuth = UsernamePasswordAuthenticationToken("me", "password")
        val memberRole = Role(id = RoleType.MEMBER.roleType)

        it("비공개 프로젝트는 멤버가 아니면 403을 반환해야 한다") {
            val project = Project(id = 10L, name = "priv", owner = "owner", projectScope = ProjectScope.PRIVATE)
            every { projectRepository.findByOwnerAndName("owner", "priv") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            every { projectUserRepository.existsByProjectIdAndUserId(10L, 1L) } returns false

            mockMvc.perform(
                get("/api/owner/priv/mentionList")
                    .param("mentionType", "user")
                    .principal(meAuth)
            ).andExpect(status().isForbidden)
        }

        it("mentionType=user: query가 없으면 프로젝트 멤버+조직 그룹멤버를 후보로 삼고 나를 맨 뒤에 배치해야 한다") {
            val org = Organization(id = 100L, name = "org1")
            val project = Project(id = 11L, name = "p", owner = "owner", projectScope = ProjectScope.PRIVATE, organization = org)
            val other = User(id = 2L, loginId = "other", name = "다른사람")
            val groupMember = User(id = 3L, loginId = "groupie", name = "그룹멤버")

            every { projectRepository.findByOwnerAndName("owner", "p") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            every { projectUserRepository.existsByProjectIdAndUserId(11L, 1L) } returns true
            every { projectUserRepository.findByProjectId(11L) } returns listOf(
                ProjectUser(id = 900L, user = me, project = project, role = memberRole),
                ProjectUser(id = 901L, user = other, project = project, role = memberRole)
            )
            every { organizationUserRepository.findByOrganizationId(100L) } returns listOf(
                OrganizationUser(id = 950L, user = groupMember, organization = org, role = memberRole)
            )

            mockMvc.perform(
                get("/api/owner/p/mentionList")
                    .param("mentionType", "user")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.result.length()").value(3))
                .andExpect(jsonPath("$.result[0].loginid").value("other"))
                .andExpect(jsonPath("$.result[1].loginid").value("groupie"))
                .andExpect(jsonPath("$.result[2].loginid").value("me"))
        }

        it("mentionType=user: 공개 프로젝트에서 query가 있으면 전역 사용자 검색 결과를 써야 한다") {
            val project = Project(id = 12L, name = "pub", owner = "owner", projectScope = ProjectScope.PUBLIC)
            val searched = User(id = 4L, loginId = "found", name = "검색됨")

            every { projectRepository.findByOwnerAndName("owner", "pub") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            every { userRepository.searchUsers("sea", PageRequest.of(0, 20)) } returns PageImpl(listOf(searched))

            mockMvc.perform(
                get("/api/owner/pub/mentionList")
                    .param("mentionType", "user")
                    .param("query", "sea")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.result.length()").value(2))
                .andExpect(jsonPath("$.result[0].loginid").value("found"))
                .andExpect(jsonPath("$.result[1].loginid").value("me"))
        }

        it("admin 로그인ID는 후보에서 제외해야 한다") {
            val project = Project(id = 13L, name = "p2", owner = "owner", projectScope = ProjectScope.PRIVATE)
            val admin = User(id = 5L, loginId = "admin", name = "관리자")

            every { projectRepository.findByOwnerAndName("owner", "p2") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            every { projectUserRepository.existsByProjectIdAndUserId(13L, 1L) } returns true
            every { projectUserRepository.findByProjectId(13L) } returns listOf(
                ProjectUser(id = 902L, user = admin, project = project, role = memberRole)
            )

            mockMvc.perform(
                get("/api/owner/p2/mentionList")
                    .param("mentionType", "user")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.result.length()").value(1))
                .andExpect(jsonPath("$.result[0].loginid").value("me"))
        }

        it("mentionType=issue: 최근 이슈 목록을 name/issueNo/title로 반환해야 한다") {
            val project = Project(id = 14L, name = "p3", owner = "owner", projectScope = ProjectScope.PRIVATE)
            val issue = Issue(id = 500L, title = "버그 수정", project = project, number = 7L)

            every { projectRepository.findByOwnerAndName("owner", "p3") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            every { projectUserRepository.existsByProjectIdAndUserId(14L, 1L) } returns true
            every { issueRepository.findForMention(project, "", PageRequest.of(0, 20)) } returns listOf(issue)

            mockMvc.perform(
                get("/api/owner/p3/mentionList")
                    .param("mentionType", "issue")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.result[0].issueNo").value("7"))
                .andExpect(jsonPath("$.result[0].title").value("버그 수정"))
        }

        it("mentionType이 user/issue가 아니면 빈 결과를 반환해야 한다") {
            val project = Project(id = 15L, name = "p4", owner = "owner", projectScope = ProjectScope.PRIVATE)

            every { projectRepository.findByOwnerAndName("owner", "p4") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            every { projectUserRepository.existsByProjectIdAndUserId(15L, 1L) } returns true

            mockMvc.perform(
                get("/api/owner/p4/mentionList")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                .andExpect(content().json("{}"))
        }
    }
})
