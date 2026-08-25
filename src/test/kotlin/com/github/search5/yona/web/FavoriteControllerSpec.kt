package com.github.search5.yona.web

import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.issue.Issue
import com.github.search5.yona.domain.organization.Organization
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.user.*
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional
import io.mockk.clearMocks

class FavoriteControllerSpec : DescribeSpec({
    val favoriteService = mockk<FavoriteService>()
    val userRepository = mockk<UserRepository>()

    val favoriteController = FavoriteController(favoriteService, userRepository)
    val mockMvc = MockMvcBuilders.standaloneSetup(favoriteController).build()

    beforeTest {
        clearMocks(favoriteService, userRepository)
    }

    describe("FavoriteController 단위 테스트") {
        val user = User(id = 1L, loginId = "testuser", name = "테스트유저", email = "test@example.com")
        val auth = UsernamePasswordAuthenticationToken("testuser", "password")

        val project = Project(id = 10L, name = "testproject", owner = "testowner")
        val org = Organization(id = 20L, name = "testorg")
        val issue = Issue(id = 30L, title = "testissue", project = project)

        describe("POST /-_-api/v1/favoriteProjects/{projectId}") {
            it("성공적으로 프로젝트 즐겨찾기 상태를 토글해야 한다(추가)") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { favoriteService.toggleFavoriteProject(1L, 10L) } returns true

                mockMvc.perform(post("/-_-api/v1/favoriteProjects/10").principal(auth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.projectId").value("10"))
                    .andExpect(jsonPath("$.favored").value(true))
            }

            it("즐겨찾기가 해제되면 favored는 false여야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { favoriteService.toggleFavoriteProject(1L, 10L) } returns false

                mockMvc.perform(post("/-_-api/v1/favoriteProjects/10").principal(auth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.favored").value(false))
            }

            it("인증되지 않은 요청은 401을 반환해야 한다") {
                mockMvc.perform(post("/-_-api/v1/favoriteProjects/10"))
                    .andExpect(status().isUnauthorized)
            }
        }

        describe("GET /-_-api/v1/favoriteProjects") {
            it("즐겨찾는 프로젝트 목록을 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                val favProject = FavoriteProject(user = user, project = project).apply { id = 5L }
                every { favoriteService.getFavoriteProjects(1L) } returns listOf(favProject)

                mockMvc.perform(get("/-_-api/v1/favoriteProjects").principal(auth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.projectIds[0]").value(10))
                    .andExpect(jsonPath("$.projects[0].projectName").value("testproject"))
            }

            it("인증되지 않은 요청은 401을 반환해야 한다") {
                mockMvc.perform(get("/-_-api/v1/favoriteProjects"))
                    .andExpect(status().isUnauthorized)
            }
        }

        describe("POST /-_-api/v1/favoriteIssues/{issueId}") {
            it("성공적으로 이슈 즐겨찾기 상태를 토글해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { favoriteService.toggleFavoriteIssue(1L, 30L) } returns true

                mockMvc.perform(post("/-_-api/v1/favoriteIssues/30").principal(auth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.issueId").value("30"))
                    .andExpect(jsonPath("$.favored").value(true))
                    .andExpect(jsonPath("$.message").value("이슈가 즐겨찾기에 추가되었습니다."))
            }

            it("즐겨찾기가 해제되면 해제 메시지를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { favoriteService.toggleFavoriteIssue(1L, 30L) } returns false

                mockMvc.perform(post("/-_-api/v1/favoriteIssues/30").principal(auth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.favored").value(false))
                    .andExpect(jsonPath("$.message").value("이슈가 즐겨찾기에서 삭제되었습니다."))
            }

            it("인증되지 않은 요청은 401을 반환해야 한다") {
                mockMvc.perform(post("/-_-api/v1/favoriteIssues/30"))
                    .andExpect(status().isUnauthorized)
            }
        }

        describe("GET /-_-api/v1/favoriteIssues") {
            it("즐겨찾는 이슈 목록을 반환해야 한다(작성자 있음)") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                val namedIssue = Issue(id = 31L, title = "namedissue", project = project, authorName = "작성자")
                val favIssue = FavoriteIssue(id = 6L, user = user, issue = namedIssue)
                every { favoriteService.getFavoriteIssues(1L) } returns listOf(favIssue)

                mockMvc.perform(get("/-_-api/v1/favoriteIssues").principal(auth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.projectIds[0]").value(31))
                    .andExpect(jsonPath("$.projects[0].issueTitle").value("namedissue"))
                    .andExpect(jsonPath("$.projects[0].issueAuthorName").value("작성자"))
            }

            it("작성자 정보가 없으면 알수없음으로 표시해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                val favIssue = FavoriteIssue(id = 6L, user = user, issue = issue)
                every { favoriteService.getFavoriteIssues(1L) } returns listOf(favIssue)

                mockMvc.perform(get("/-_-api/v1/favoriteIssues").principal(auth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.projects[0].issueAuthorName").value("알수없음"))
            }

            it("인증되지 않은 요청은 401을 반환해야 한다") {
                mockMvc.perform(get("/-_-api/v1/favoriteIssues"))
                    .andExpect(status().isUnauthorized)
            }
        }

        describe("POST /-_-api/v1/favoriteOrganizations/{organizationId}") {
            it("성공적으로 조직 즐겨찾기 상태를 토글해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { favoriteService.toggleFavoriteOrganization(1L, 20L) } returns true

                mockMvc.perform(post("/-_-api/v1/favoriteOrganizations/20").principal(auth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.organizationId").value("20"))
                    .andExpect(jsonPath("$.favored").value(true))
            }

            it("즐겨찾기가 해제되면 favored는 false여야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { favoriteService.toggleFavoriteOrganization(1L, 20L) } returns false

                mockMvc.perform(post("/-_-api/v1/favoriteOrganizations/20").principal(auth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.favored").value(false))
            }

            it("인증되지 않은 요청은 401을 반환해야 한다") {
                mockMvc.perform(post("/-_-api/v1/favoriteOrganizations/20"))
                    .andExpect(status().isUnauthorized)
            }
        }

        describe("GET /-_-api/v1/favoriteOrganizations") {
            it("즐겨찾는 조직 목록을 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                val favOrg = FavoriteOrganization(user = user, organization = org)
                every { favoriteService.getFavoriteOrganizations(1L) } returns listOf(favOrg)

                mockMvc.perform(get("/-_-api/v1/favoriteOrganizations").principal(auth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.organizationIds[0]").value(20))
                    .andExpect(jsonPath("$.organizations[0].organizationName").value("testorg"))
            }

            it("인증되지 않은 요청은 401을 반환해야 한다") {
                mockMvc.perform(get("/-_-api/v1/favoriteOrganizations"))
                    .andExpect(status().isUnauthorized)
            }
        }
    }
})
