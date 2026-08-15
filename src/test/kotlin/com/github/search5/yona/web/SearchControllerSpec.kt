package com.github.search5.yona.web

import com.github.search5.yona.domain.enumeration.SearchType
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.organization.Organization
import com.github.search5.yona.domain.organization.OrganizationRepository
import com.github.search5.yona.domain.support.SearchResult
import com.github.search5.yona.domain.support.SearchService
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

class SearchControllerSpec : DescribeSpec({
    val searchService = mockk<SearchService>()
    val userRepository = mockk<UserRepository>()
    val projectRepository = mockk<ProjectRepository>()
    val organizationRepository = mockk<OrganizationRepository>()

    val searchController = SearchController(
        searchService,
        userRepository,
        projectRepository,
        organizationRepository
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(searchController).build()

    beforeTest {
        io.mockk.clearMocks(searchService, userRepository, projectRepository, organizationRepository)
    }

    describe("SearchController 웹 진입점 테스트") {
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")
        val searchResult = SearchResult(keyword = "yona", searchType = SearchType.ISSUE)

        it("GET /search - 전역 검색 시 200 OK와 search/list 뷰를 반환해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { searchService.searchInAll("yona", SearchType.ISSUE, user, any()) } returns searchResult

            mockMvc.perform(
                get("/search")
                    .param("keyword", "yona")
                    .param("searchType", "issue")
                    .principal(userAuth)
            )
                .andExpect(status().isOk)
                .andExpect(view().name("search/list"))
                .andExpect(model().attributeExists("keyword", "searchResult", "currentUser"))
        }

        it("GET /org/{organizationName}/search - 조직 검색 시 200 OK와 search/list 뷰를 반환해야 한다") {
            val org = Organization(id = 5L, name = "testorg")
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { organizationRepository.findByName("testorg") } returns Optional.of(org)
            every { searchService.searchInAGroup("yona", SearchType.ISSUE, user, org, any()) } returns searchResult

            mockMvc.perform(
                get("/org/testorg/search")
                    .param("keyword", "yona")
                    .param("searchType", "issue")
                    .principal(userAuth)
            )
                .andExpect(status().isOk)
                .andExpect(view().name("search/list"))
                .andExpect(model().attributeExists("keyword", "searchResult", "currentUser", "org"))
        }

        it("GET /{owner}/{projectName}/search - 프로젝트 검색 시 200 OK와 search/list 뷰를 반환해야 한다") {
            val project = Project(id = 1L, name = "TestProj", owner = "owner")
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { projectRepository.findByOwnerAndName("owner", "TestProj") } returns Optional.of(project)
            every { searchService.searchInAProject("yona", SearchType.ISSUE, user, project, any()) } returns searchResult

            mockMvc.perform(
                get("/owner/TestProj/search")
                    .param("keyword", "yona")
                    .param("searchType", "issue")
                    .principal(userAuth)
            )
                .andExpect(status().isOk)
                .andExpect(view().name("search/list"))
                .andExpect(model().attributeExists("keyword", "searchResult", "currentUser", "project"))
        }
    }
})
