package com.github.search5.yona.web

import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.attachment.AttachmentRepository
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.pullrequest.PullRequestRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.user.UserService
import com.github.search5.yona.domain.watch.WatchRepository
import com.github.search5.yona.domain.notification.UserProjectNotificationRepository
import com.github.search5.yona.domain.user.FavoriteProjectRepository
import com.github.search5.yona.domain.user.FavoriteOrganizationRepository
import com.github.search5.yona.domain.organization.OrganizationUserRepository
import com.github.search5.yona.domain.organization.OrganizationRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.data.web.PageableHandlerMethodArgumentResolver
import java.util.Optional

class UserViewControllerSpec : DescribeSpec({
    val userRepository = mockk<UserRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val issueRepository = mockk<IssueRepository>()
    val pullRequestRepository = mockk<PullRequestRepository>()
    val watchRepository = mockk<WatchRepository>()
    val projectRepository = mockk<ProjectRepository>()
    val userProjectNotificationRepository = mockk<UserProjectNotificationRepository>()
    val attachmentRepository = mockk<AttachmentRepository>()
    val postingRepository = mockk<PostingRepository>()
    val favoriteProjectRepository = mockk<FavoriteProjectRepository>()
    val favoriteOrganizationRepository = mockk<FavoriteOrganizationRepository>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    val organizationRepository = mockk<OrganizationRepository>()
    val userService = mockk<UserService>()

    val userViewController = UserViewController(
        userRepository,
        projectUserRepository,
        issueRepository,
        pullRequestRepository,
        watchRepository,
        projectRepository,
        userProjectNotificationRepository,
        attachmentRepository,
        postingRepository,
        favoriteProjectRepository,
        favoriteOrganizationRepository,
        organizationUserRepository,
        organizationRepository,
        userService
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(userViewController)
        .setCustomArgumentResolvers(PageableHandlerMethodArgumentResolver())
        .build()

    beforeTest {
        io.mockk.clearMocks(
            userRepository,
            projectUserRepository,
            issueRepository,
            userService,
            pullRequestRepository,
            watchRepository,
            projectRepository,
            userProjectNotificationRepository,
            attachmentRepository,
            postingRepository,
            favoriteProjectRepository,
            favoriteOrganizationRepository,
            organizationUserRepository,
            organizationRepository
        )
        every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()
    }

    describe("UserViewController 템플릿 연동 테스트") {
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")

        describe("GET /user/{loginId}") {
            it("200 OK와 user/view 뷰를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.findByUserId(10L) } returns emptyList()
                every { issueRepository.findByAuthorId(10L) } returns emptyList()
                every { pullRequestRepository.findByContributor(user) } returns emptyList()

                mockMvc.perform(get("/user/testuser").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("user/view"))
                    .andExpect(model().attributeExists("user", "projects", "issues", "pullRequests"))
            }
        }

        describe("GET /user/editform") {
            it("로그인된 사용자라면 200 OK와 user/edit 뷰를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)

                mockMvc.perform(get("/user/editform").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("user/edit"))
                    .andExpect(model().attributeExists("user"))
            }
        }

        describe("GET /user/editform/emails") {
            it("로그인된 사용자라면 200 OK와 user/edit_emails 뷰를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)

                mockMvc.perform(get("/user/editform/emails").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("user/edit_emails"))
                    .andExpect(model().attributeExists("user", "emails"))
            }
        }

        describe("GET /user/editform/notifications") {
            it("로그인된 사용자라면 200 OK와 user/edit_notifications 뷰를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { watchRepository.findByUserAndResourceType(user, any()) } returns emptyList()

                mockMvc.perform(get("/user/editform/notifications").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("user/edit_notifications"))
                    .andExpect(model().attributeExists("user", "projects", "notiTypes", "notiMap", "notiTypeDescriptions"))
            }
        }
    }
})
