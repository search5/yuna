package com.github.search5.yona.domain.support

import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.organization.OrganizationRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.vcs.Commit
import com.github.search5.yona.domain.vcs.PlayRepository
import com.github.search5.yona.domain.vcs.RepositoryService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.MessageSource

// yona utils/AutoLinkRenderer.java:268-296 toValidSHALink() 대응 (P2-35). SHA 커밋 자동 링크화가
// project.isGit() 뿐 아니라 project.isCodeAvailable()(코드브라우저 메뉴 활성 여부)도 검사해야 하는데,
// yuna는 vcs=="GIT" 검사만 있고 project.isCodeEnabled 검사가 빠져 있었다 — 코드브라우저를 끈
// 프로젝트에서도 커밋 SHA가 링크로 렌더링되는(존재하지 않는 코드브라우저 URL을 가리키는) 문제.
class AutoLinkRendererSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val issueRepository = mockk<IssueRepository>()
    val userRepository = mockk<UserRepository>()
    val organizationRepository = mockk<OrganizationRepository>()
    val repositoryService = mockk<RepositoryService>()
    val messageSource = mockk<MessageSource>()

    val autoLinkRenderer = AutoLinkRenderer(
        projectRepository, issueRepository, userRepository, organizationRepository,
        repositoryService, messageSource
    )

    val sha = "abc1234"

    beforeTest {
        clearMocks(projectRepository, issueRepository, userRepository, organizationRepository, repositoryService, messageSource)
    }

    describe("render() - SHA 자동 링크화 (P2-35)") {
        it("코드브라우저가 켜진(isCodeEnabled=true) GIT 프로젝트에서는 SHA가 커밋 링크로 변환된다") {
            val project = Project(id = 1L, owner = "owner", name = "proj", vcs = "GIT", isCodeEnabled = true)
            val repo = mockk<PlayRepository>()
            val commit = mockk<Commit>()
            every { repositoryService.getRepository(project) } returns repo
            every { repo.getCommit(sha) } returns commit
            every { commit.getId() } returns sha
            every { commit.getShortId() } returns sha

            val result = autoLinkRenderer.render(sha, project)

            result shouldContain "<a"
            result shouldContain "/owner/proj/commit/$sha"
        }

        it("코드브라우저가 꺼진(isCodeEnabled=false) 프로젝트에서는 GIT이어도 SHA를 링크로 바꾸지 않는다") {
            val project = Project(id = 2L, owner = "owner", name = "proj2", vcs = "GIT", isCodeEnabled = false)

            val result = autoLinkRenderer.render(sha, project)

            result shouldNotContain "<a"
            verify(exactly = 0) { repositoryService.getRepository(any()) }
        }
    }
})
