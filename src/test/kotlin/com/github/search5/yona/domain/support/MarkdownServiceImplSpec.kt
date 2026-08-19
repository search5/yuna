package com.github.search5.yona.domain.support

import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.issue.Issue
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.organization.Organization
import com.github.search5.yona.domain.organization.OrganizationRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.vcs.Commit
import com.github.search5.yona.domain.vcs.PlayRepository
import com.github.search5.yona.domain.vcs.RepositoryService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import org.springframework.context.MessageSource
import java.util.Optional
import java.util.Locale

class MarkdownServiceImplSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>(relaxed = true)
    val issueRepository = mockk<IssueRepository>(relaxed = true)
    val userRepository = mockk<UserRepository>(relaxed = true)
    val organizationRepository = mockk<OrganizationRepository>(relaxed = true)
    val repositoryService = mockk<RepositoryService>(relaxed = true)
    val messageSource = mockk<MessageSource>(relaxed = true)

    val autoLinkRenderer = AutoLinkRenderer(
        projectRepository,
        issueRepository,
        userRepository,
        organizationRepository,
        repositoryService,
        messageSource
    )

    val markdownService = MarkdownServiceImpl(autoLinkRenderer)

    describe("MarkdownServiceImpl & AutoLinkRenderer TDD 단축 링크 변환 검증") {
        val project = Project(id = 1L, name = "yobi", owner = "yobi", vcs = "GIT")

        it("멘션 @yobi 링크 변환 테스트") {
            val user = User(id = 2L, loginId = "yobi", name = "요비")
            every { userRepository.findByLoginId("yobi") } returns Optional.of(user)
            every { organizationRepository.findByName("yobi") } returns Optional.empty()

            val input = "Mention: @yobi"
            val output = markdownService.render(input, true, project)
            println("=== MENTION OUTPUT: $output ===")

            output.shouldContain("href=\"/user/yobi\"")
            output.shouldContain("class=\"no-text-decoration user-link\"")
            output.shouldContain("@요비")
        }

        it("이슈 #2 링크 변환 테스트") {
            val issue = Issue(id = 10L, title = "버그수정", project = project, number = 2L, state = State.OPEN)
            every { issueRepository.findByProjectAndNumber(any(), 2L) } returns issue
            every { messageSource.getMessage("issue.state.open", null, "open", any<Locale>()) } returns "열림"

            val input = "Issue no: #2"
            val output = markdownService.render(input, true, project)
            println("=== ISSUE OUTPUT: $output ===")

            output.shouldContain("href=\"/yobi/yobi/issue/2\"")
            output.shouldContain("class=\"issueLink\"")
            output.shouldContain("#2.버그수정")
            output.shouldContain("class=\"issue-state open\"")
            output.shouldContain("열림")
        }

        it("커밋 @763575 링크 변환 테스트") {
            val commit = object : Commit() {
                override fun getShortId(): String = "763575f"
                override fun getId(): String = "763575f177a4ce8b9370954de3ea1a1410205593"
                override fun getShortMessage(): String = ""
                override fun getMessage(): String? = null
                override fun getAuthor(): User? = null
                override fun getAuthorName(): String? = null
                override fun getAuthorEmail(): String? = null
                override fun getAuthorDate(): java.util.Date? = null
                override fun getAuthorTimezone(): java.util.TimeZone? = null
                override fun getCommitterName(): String? = null
                override fun getCommitterEmail(): String? = null
                override fun getCommitterDate(): java.util.Date? = null
                override fun getCommitterTimezone(): java.util.TimeZone? = null
                override fun getParentCount(): Int = 0
            }

            val playRepo = object : PlayRepository {
                override fun create() {}
                override fun isIntermediateFolder(path: String): Boolean = false
                override fun getMetaDataFromPath(path: String): tools.jackson.databind.node.ObjectNode? = null
                override fun getMetaDataFromPath(branch: String, path: String): tools.jackson.databind.node.ObjectNode? = null
                override fun getRawFile(revision: String, path: String): ByteArray = ByteArray(0)
                override fun delete() {}
                override fun getPatch(commitId: String): String = ""
                override fun getPatch(revA: String, revB: String): String = ""
                override fun getDiff(commitId: String): List<Any> = emptyList()
                override fun getDiff(revA: String, revB: String): List<Any> = emptyList()
                override fun getHistory(pageNum: Int, pageSize: Int, untilRev: String?, path: String?): List<Commit> = emptyList()
                override fun getCommit(rev: String): Commit? {
                    return if (rev == "763575f") commit else null
                }
                override fun getRefNames(): List<String> = emptyList()
                override fun isFile(path: String): Boolean = false
                override fun isFile(path: String, revStr: String): Boolean = false
                override fun renameTo(projectName: String): Boolean = true
                override fun getDefaultBranch(): String = "main"
                override fun setDefaultBranch(target: String) {}
                override fun getBranches(): List<com.github.search5.yona.domain.vcs.GitBranch> = emptyList()
                override fun getHeadBranch(): com.github.search5.yona.domain.vcs.GitBranch? = null
                override fun deleteBranch(branchName: String) {}
                override fun createBranch(branchName: String, startPoint: String) {}
                override fun getParentCommitOf(commitId: String): Commit? = null
                override fun isEmpty(): Boolean = false
                override fun move(srcProjectOwner: String, srcProjectName: String, destProjectOwner: String, destProjectName: String): Boolean = true
                override fun getDirectory(): java.io.File = java.io.File("/tmp")
                override fun getArchive(os: java.io.OutputStream, branchName: String) {}
                override fun getBlobId(revision: String, path: String): String? = null
            }

            every { repositoryService.getRepository(any()) } returns playRepo

            val input = "commit: @763575f"
            val output = markdownService.render(input, true, project)
            println("=== COMMIT OUTPUT: $output ===")

            output.shouldContain("href=\"/yobi/yobi/commit/763575f177a4ce8b9370954de3ea1a1410205593\"")
            output.shouldContain("763575f")
        }
    }

    describe("MarkdownServiceImpl 새니타이저 XSS 방지 검증 (allowlist 기반)") {
        it("<script> 태그는 완전히 제거되어야 한다") {
            val output = markdownService.render("본문 <script>alert('xss')</script> 끝")
            output.shouldNotContain("<script")
            output.shouldNotContain("alert(")
        }

        it("onclick 같은 이벤트 핸들러 속성은 허용목록에 없으므로 제거되어야 한다") {
            val output = markdownService.render("<div onclick=\"alert(1)\">내용</div>")
            output.shouldNotContain("onclick")
        }

        it("onerror 이벤트 핸들러가 있는 img 태그는 속성만 제거되고 태그는 허용되어야 한다") {
            val output = markdownService.render("<img src=\"x.png\" onerror=\"alert(1)\">")
            output.shouldNotContain("onerror")
        }

        it("javascript: 프로토콜 링크는 무해화되어야 한다") {
            val output = markdownService.render("[클릭](javascript:alert(1))")
            output.shouldNotContain("javascript:")
        }

        it("허용되지 않은 svg/onload 벡터는 태그 자체가 제거되어야 한다") {
            val output = markdownService.render("<svg onload=\"alert(1)\"></svg>")
            output.shouldNotContain("onload")
            output.shouldNotContain("<svg")
        }

        it("data: URI 기반 스크립트 삽입은 무해화되어야 한다") {
            val output = markdownService.render("<a href=\"data:text/html,<script>alert(1)</script>\">링크</a>")
            output.shouldNotContain("<script")
        }

        it("정상적인 GFM 표(table)는 그대로 렌더링되어야 한다") {
            val input = """
                |a|b|
                |---|---|
                |1|2|
            """.trimIndent()
            val output = markdownService.render(input)
            output.shouldContain("<table")
            output.shouldContain("<td>1</td>")
        }

        it("펜스 코드블록은 그대로 렌더링되어야 한다") {
            val output = markdownService.render("```\nval x = 1\n```")
            output.shouldContain("<pre")
            output.shouldContain("val x = 1")
        }
    }
})
