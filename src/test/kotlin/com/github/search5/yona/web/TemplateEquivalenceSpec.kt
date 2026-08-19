package com.github.search5.yona.web

import com.github.search5.yona.AbstractIntegrationTest
import com.github.search5.yona.domain.board.Posting
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.issue.Issue
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUser
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.role.Role
import com.github.search5.yona.domain.role.RoleRepository
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.user.YonaUserDetails
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.jsoup.Jsoup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

class TemplateEquivalenceSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val roleRepository: RoleRepository,
    private val postingRepository: PostingRepository,
    private val issueRepository: IssueRepository,
    private val issueLabelRepository: com.github.search5.yona.domain.issue.IssueLabelRepository,
    private val issueLabelCategoryRepository: com.github.search5.yona.domain.issue.IssueLabelCategoryRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        describe("Thymeleaf 템플릿 동치성 회귀 검증") {
            // Given: 테스트용 기본 데이터 준비
            val owner = userRepository.findByLoginId("owner").orElseGet {
                userRepository.save(User(loginId = "owner", name = "소유자", email = "owner@yona.io"))
            }
            val member = userRepository.findByLoginId("member").orElseGet {
                userRepository.save(User(loginId = "member", name = "멤버", email = "member@yona.io"))
            }
            val nonmember = userRepository.findByLoginId("nonmember").orElseGet {
                userRepository.save(User(loginId = "nonmember", name = "외부인", email = "nonmember@yona.io"))
            }

            val roleMember = roleRepository.findById(RoleType.MEMBER.roleType).orElseGet {
                roleRepository.save(Role(id = RoleType.MEMBER.roleType, name = "MEMBER"))
            }

            // 프로젝트 1: 일반 공개 프로젝트 (코드 비공개 해제)
            val publicProj = projectRepository.findAll().find { it.name == "public-proj" } ?: projectRepository.save(
                Project(
                    name = "public-proj",
                    owner = "owner",
                    projectScope = ProjectScope.PUBLIC,
                    isCodeAccessibleMemberOnly = false
                )
            )
            val category = issueLabelCategoryRepository.findAll().find { it.project.id == publicProj.id }
                ?: issueLabelCategoryRepository.save(
                    com.github.search5.yona.domain.issue.IssueLabelCategory(
                        name = "테스트카테고리",
                        project = publicProj
                    )
                )

            val label = issueLabelRepository.findAll().find { it.category.id == category.id }
                ?: issueLabelRepository.save(
                    com.github.search5.yona.domain.issue.IssueLabel(
                        name = "테스트라벨",
                        color = "#FF0000",
                        category = category,
                        project = publicProj
                    )
                )

            // 프로젝트 2: 공개 프로젝트이지만 코드는 멤버전용인 프로젝트
            val codeMemberOnlyProj = projectRepository.findAll().find { it.name == "memberonly-proj" } ?: projectRepository.save(
                Project(
                    name = "memberonly-proj",
                    owner = "owner",
                    projectScope = ProjectScope.PUBLIC,
                    isCodeAccessibleMemberOnly = true
                )
            )

            // 멤버 관계 형성
            if (!projectUserRepository.existsByProjectIdAndUserId(codeMemberOnlyProj.id!!, member.id!!)) {
                projectUserRepository.save(
                    ProjectUser(
                        project = codeMemberOnlyProj,
                        user = member,
                        role = roleMember
                    )
                )
            }

            val memberDetails = YonaUserDetails(
                id = member.id!!,
                loginId = member.loginId,
                passwordVal = "hashed",
                passwordSalt = "salt",
                authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
            )

            val nonMemberDetails = YonaUserDetails(
                id = nonmember.id!!,
                loginId = nonmember.loginId,
                passwordVal = "hashed",
                passwordSalt = "salt",
                authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
            )

            describe("[Test-19-1] GNB 프로젝트 메뉴(projectMenu) 탭 동치성 렌더링 검증") {
                it("공개 프로젝트에 비로그인 접근 시, CODE/PULL_REQUEST/REVIEW 탭이 정상적으로 렌더링되어야 한다") {
                    val result = mockMvc.perform(get("/owner/public-proj"))
                        .andExpect(status().isOk)
                        .andReturn()

                    val html = result.response.contentAsString
                    val doc = Jsoup.parse(html)

                    // GNB 탭 체크
                    val menu = doc.select(".project-menu-nav")
                    menu.size shouldNotBe 0
                    menu.select("a[href*='/code']").size shouldNotBe 0
                    menu.select("a[href*='/pulls']").size shouldNotBe 0
                    menu.select("a[href*='/reviews']").size shouldNotBe 0
                }

                it("코드 멤버전용 프로젝트에 비로그인 접근 시, CODE/PULL_REQUEST/REVIEW 탭이 렌더링에서 제외되어야 한다") {
                    val result = mockMvc.perform(get("/owner/memberonly-proj"))
                        .andExpect(status().isOk)
                        .andReturn()

                    val html = result.response.contentAsString
                    val doc = Jsoup.parse(html)

                    val menu = doc.select(".project-menu-nav")
                    menu.select("a[href*='/code']").size shouldBe 0
                    menu.select("a[href*='/pulls']").size shouldBe 0
                    menu.select("a[href*='/reviews']").size shouldBe 0
                }

                it("코드 멤버전용 프로젝트에 비멤버로 로그인 접근 시, CODE/PULL_REQUEST/REVIEW 탭이 숨겨져야 한다") {
                    val result = mockMvc.perform(
                        get("/owner/memberonly-proj")
                            .with(SecurityMockMvcRequestPostProcessors.user(nonMemberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val html = result.response.contentAsString
                    val doc = Jsoup.parse(html)

                    val menu = doc.select(".project-menu-nav")
                    menu.select("a[href*='/code']").size shouldBe 0
                    menu.select("a[href*='/pulls']").size shouldBe 0
                    menu.select("a[href*='/reviews']").size shouldBe 0
                }

                it("코드 멤버전용 프로젝트에 프로젝트 멤버로 로그인 접근 시, CODE/PULL_REQUEST/REVIEW 탭이 정상 노출되어야 한다") {
                    val result = mockMvc.perform(
                        get("/owner/memberonly-proj")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val html = result.response.contentAsString
                    val doc = Jsoup.parse(html)

                    val menu = doc.select(".project-menu-nav")
                    menu.select("a[href*='/code']").size shouldNotBe 0
                    menu.select("a[href*='/pulls']").size shouldNotBe 0
                    menu.select("a[href*='/reviews']").size shouldNotBe 0
                }
            }

            describe("[Test-19-2] 게시판 상세 뷰(board/view.html) 렌더링 동치성 검증") {
                val post = postingRepository.findAll().find { it.project.id == publicProj.id } ?: postingRepository.save(
                    Posting(
                        title = "테스트포스트",
                        body = "포스트내용",
                        project = publicProj,
                        authorId = owner.id!!,
                        authorLoginId = owner.loginId,
                        authorName = owner.name,
                        number = 1L
                    )
                )

                it("로그인 사용자가 게시판 상세 조회 시, 댓글 입력 폼 및 파일 업로더 드롭존 마크업이 포함되어야 한다") {
                    val result = mockMvc.perform(
                        get("/owner/public-proj/post/${post.number}")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val html = result.response.contentAsString
                    val doc = Jsoup.parse(html)

                    // 댓글 작성 폼과 업로드 드롭존 검증
                    doc.select("#comment-form").size shouldBe 1
                    doc.select("#upload-drop-zone").size shouldBe 1
                    doc.select("input[name='file']").size shouldBe 1
                }

                it("비로그인 사용자가 상세 조회 시, 비활성화된 알림 메시지와 함께 댓글 등록 창이 제한되어야 한다") {
                    val result = mockMvc.perform(get("/owner/public-proj/post/${post.number}"))
                        .andExpect(status().isOk)
                        .andReturn()

                    val html = result.response.contentAsString
                    val doc = Jsoup.parse(html)

                    // 폼 자체는 배제되고 비로그인 알림 상자가 노출되는지 체크
                    doc.select("#comment-form").size shouldBe 0
                    val disabledBox = doc.select(".write-comment-box[data-login='required']")
                    disabledBox.size shouldBe 1
                    disabledBox.select("textarea.comment.disabled").size shouldBe 1
                }
            }

            describe("[Test-19-3] 이슈 상세 뷰(issue/view.html) 렌더링 동치성 검증") {
                val issue = issueRepository.findAll().find { it.project.id == publicProj.id } ?: issueRepository.save(
                    Issue(
                        title = "테스트이슈",
                        body = "이슈내용",
                        project = publicProj,
                        authorId = owner.id!!,
                        authorLoginId = owner.loginId,
                        authorName = owner.name,
                        number = 1L
                    )
                )

                it("로그인 사용자가 이슈 상세 조회 시, 댓글 입력 폼 및 파일 업로더 드롭존 마크업이 노출되어야 한다") {
                    val result = mockMvc.perform(
                        get("/owner/public-proj/issue/${issue.number}")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val html = result.response.contentAsString
                    val doc = Jsoup.parse(html)

                    doc.select("#comment-form").size shouldBe 1
                    doc.select("#upload-drop-zone").size shouldBe 1
                    doc.select("input[name='file']").size shouldBe 1
                }

                it("비로그인 사용자가 이슈 조회 시, 댓글 폼이 차단되고 비로그인 대체 마크업이 표시되어야 한다") {
                    val result = mockMvc.perform(get("/owner/public-proj/issue/${issue.number}"))
                        .andExpect(status().isOk)
                        .andReturn()

                    val html = result.response.contentAsString
                    val doc = Jsoup.parse(html)

                    doc.select("#comment-form").size shouldBe 0
                    val disabledBox = doc.select(".write-comment-box[data-login='required']")
                    disabledBox.size shouldBe 1
                    disabledBox.select("textarea.comment.disabled").size shouldBe 1
                }
            }

            describe("[Test-19-4] 게시판 목록 뷰(board/list.html) 렌더링 동치성 검증") {
                it("게시판 목록 조회 시 라벨 드롭다운 필터 및 단축키 도움말 조각이 렌더링되어야 한다") {
                    val result = mockMvc.perform(
                        get("/owner/public-proj/posts")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val html = result.response.contentAsString
                    val doc = Jsoup.parse(html)

                    // 1. 라벨 드롭다운 필터 존재 여부 검증
                    doc.select(".board-labels").size shouldNotBe 0
                    doc.select(".board-labels select").size shouldNotBe 0

                    // 2. 단축키 도움말 조각 존재 여부 검증
                    doc.select("#helpKeys").size shouldNotBe 0
                    doc.select(".board-footer").size shouldNotBe 0
                }
            }
        }
    }
}
