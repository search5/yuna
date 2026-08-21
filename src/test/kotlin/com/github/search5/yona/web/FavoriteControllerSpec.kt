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
            it("성공적으로 프로젝트 즐겨찾기 상태를 토글해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { favoriteService.toggleFavoriteProject(1L, 10L) } returns true

                mockMvc.perform(post("/-_-api/v1/favoriteProjects/10").principal(auth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.projectId").value("10"))
                    .andExpect(jsonPath("$.favored").value(true))
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
        }

        describe("POST /-_-api/v1/favoriteIssues/{issueId}") {
            it("성공적으로 이슈 즐겨찾기 상태를 토글해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { favoriteService.toggleFavoriteIssue(1L, 30L) } returns true

                mockMvc.perform(post("/-_-api/v1/favoriteIssues/30").principal(auth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.issueId").value("30"))
                    .andExpect(jsonPath("$.favored").value(true))
                    .andExpect(jsonPath("$.message").exists())
            }
        }

        describe("GET /-_-api/v1/favoriteIssues") {
            it("즐겨찾는 이슈 목록을 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                val favIssue = FavoriteIssue(id = 6L, user = user, issue = issue)
                every { favoriteService.getFavoriteIssues(1L) } returns listOf(favIssue)

                mockMvc.perform(get("/-_-api/v1/favoriteIssues").principal(auth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.projectIds[0]").value(30))
                    .andExpect(jsonPath("$.projects[0].issueTitle").value("testissue"))
            }
        }
    }
})
