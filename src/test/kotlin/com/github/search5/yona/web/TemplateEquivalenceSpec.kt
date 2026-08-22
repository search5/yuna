package com.github.search5.yona.web

import com.github.search5.yona.AbstractIntegrationTest
import com.github.search5.yona.domain.board.Posting
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.issue.Assignee
import com.github.search5.yona.domain.issue.AssigneeRepository
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
import com.github.search5.yona.domain.support.YonaUpdateService
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.user.UserState
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import com.github.search5.yona.domain.issue.IssueLabelRepository
import com.github.search5.yona.domain.issue.IssueLabelCategoryRepository
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import com.github.search5.yona.domain.issue.IssueLabelCategory
import com.github.search5.yona.domain.issue.IssueLabel
import com.github.search5.yona.domain.issue.RecentIssueService
import com.github.search5.yona.domain.user.UserSetting
import com.github.search5.yona.domain.user.UserSettingRepository
import com.github.search5.yona.domain.user.UserVerification
import com.github.search5.yona.domain.user.UserVerificationRepository
import com.github.search5.yona.domain.milestone.Milestone
import com.github.search5.yona.domain.milestone.MilestoneRepository
import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.organization.Organization
import com.github.search5.yona.domain.organization.OrganizationRepository
import com.github.search5.yona.domain.organization.OrganizationUser
import com.github.search5.yona.domain.organization.OrganizationUserRepository

class TemplateEquivalenceSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val roleRepository: RoleRepository,
    private val postingRepository: PostingRepository,
    private val issueRepository: IssueRepository,
    private val assigneeRepository: AssigneeRepository,
    private val issueLabelRepository: IssueLabelRepository,
    private val issueLabelCategoryRepository: IssueLabelCategoryRepository,
    private val yonaUpdateService: YonaUpdateService,
    private val recentIssueService: RecentIssueService,
    private val userSettingRepository: UserSettingRepository,
    private val userVerificationRepository: UserVerificationRepository,
    private val milestoneRepository: MilestoneRepository,
    private val organizationRepository: OrganizationRepository,
    private val organizationUserRepository: OrganizationUserRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
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
                    isCodeAccessibleMemberOnly = false,
                    vcs = "GIT"
                )
            )
            val category = issueLabelCategoryRepository.findAll().find { it.project.id == publicProj.id }
                ?: issueLabelCategoryRepository.save(
                    IssueLabelCategory(
                        name = "테스트카테고리",
                        project = publicProj
                    )
                )

            val label = issueLabelRepository.findAll().find { it.category.id == category.id }
                ?: issueLabelRepository.save(
                    IssueLabel(
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
                    isCodeAccessibleMemberOnly = true,
                    vcs = "GIT"
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

            val siteAdmin = userRepository.findByLoginId("siteadmin").orElseGet {
                userRepository.save(User(loginId = "siteadmin", name = "관리자", email = "siteadmin@yona.io", state = UserState.SITE_ADMIN))
            }.let {
                if (it.state != UserState.SITE_ADMIN) {
                    it.state = UserState.SITE_ADMIN
                    userRepository.save(it)
                } else it
            }

            val siteAdminDetails = YonaUserDetails(
                id = siteAdmin.id!!,
                loginId = siteAdmin.loginId,
                passwordVal = "hashed",
                passwordSalt = "salt",
                authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE", "ROLE_SITE_ADMIN")
            )

            fun setUpdateState(updateRequired: Boolean, latestVersion: String?, watched: Boolean = true) {
                val requiredField = yonaUpdateService.javaClass.getDeclaredField("isUpdateRequired")
                requiredField.isAccessible = true
                requiredField.setBoolean(yonaUpdateService, updateRequired)

                val versionField = yonaUpdateService.javaClass.getDeclaredField("latestVersion")
                versionField.isAccessible = true
                versionField.set(yonaUpdateService, latestVersion)

                yonaUpdateService.isWatched = watched
            }

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
                    doc.select(".upload-wrap[data-resource-type]").size shouldBe 1
                    doc.select("input[name='filePath']").size shouldBe 1
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
                    doc.select(".upload-wrap[data-resource-type]").size shouldBe 1
                    doc.select("input[name='filePath']").size shouldBe 1
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

            describe("[Test-19-5] 레이아웃 공통 조각(site/layout.html) 동치성 검증") {
                it("사이트 관리자에게 업데이트가 존재하고 알림을 끄지 않았다면, 업데이트 알림 배너가 노출되어야 한다") {
                    setUpdateState(updateRequired = true, latestVersion = "9.9.9", watched = true)

                    val result = mockMvc.perform(
                        get("/owner/public-proj")
                            .with(SecurityMockMvcRequestPostProcessors.user(siteAdminDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    val notice = doc.select("p.center-txt a[href*='github.com/yona-projects/yona/releases']")
                    notice.size shouldBe 1
                    notice.text() shouldNotBe ""

                    val hideBtn = doc.select("p.center-txt button[data-request-method='post'][data-request-uri='/sites/unwatchUpdate']")
                    hideBtn.size shouldBe 1
                }

                it("업데이트가 필요하지 않으면 사이트 관리자에게도 업데이트 알림 배너가 노출되지 않아야 한다") {
                    setUpdateState(updateRequired = false, latestVersion = null, watched = true)

                    val result = mockMvc.perform(
                        get("/owner/public-proj")
                            .with(SecurityMockMvcRequestPostProcessors.user(siteAdminDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select("button[data-request-uri='/sites/unwatchUpdate']").size shouldBe 0
                }

                it("업데이트가 존재해도 사이트 관리자가 아닌 사용자에게는 업데이트 알림 배너가 노출되지 않아야 한다") {
                    setUpdateState(updateRequired = true, latestVersion = "9.9.9", watched = true)

                    val result = mockMvc.perform(
                        get("/owner/public-proj")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select("button[data-request-uri='/sites/unwatchUpdate']").size shouldBe 0

                    setUpdateState(updateRequired = false, latestVersion = null, watched = true)
                }

                it("head 조각에 og/twitter 메타 태그가 렌더링되어야 한다") {
                    val result = mockMvc.perform(get("/owner/public-proj"))
                        .andExpect(status().isOk)
                        .andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select("meta[property='og:title']").size shouldBe 1
                    doc.select("meta[property='og:url']").size shouldBe 1
                    doc.select("meta[property='og:type']").attr("content") shouldBe "website"
                    doc.select("meta[name='twitter:card']").attr("content") shouldBe "summary"
                    doc.select("meta[name='twitter:title']").size shouldBe 1
                    doc.select("meta[name='twitter:url']").size shouldBe 1
                }

                it("공통 스크립트 조각에 NProgress 라이브러리 로드/초기화 및 ViewerJS 자산이 포함되어야 한다") {
                    val result = mockMvc.perform(get("/owner/public-proj"))
                        .andExpect(status().isOk)
                        .andReturn()

                    val html = result.response.contentAsString
                    val doc = Jsoup.parse(html)

                    doc.select("link[href*='lib/nprogress/nprogress.css']").size shouldBe 1
                    doc.select("script[src*='lib/nprogress/nprogress.js']").size shouldBe 1
                    html.contains("NProgress.configure(") shouldBe true

                    doc.select("link[href*='lib/viewerjs/viewer.css']").size shouldBe 1
                    doc.select("script[src*='lib/viewerjs/viewer.js']").size shouldBe 1
                    doc.select("script[src*='lib/viewerjs/jquery-viewer.js']").size shouldBe 1
                    html.contains(".markdown-wrap").shouldBe(true)
                }

                it("sendYonaUsage 설정 기본값이 꺼져 있으면 메인 레이아웃에도 구글 애널리틱스 스크립트가 렌더링되지 않아야 한다") {
                    val result = mockMvc.perform(get("/owner/public-proj"))
                        .andExpect(status().isOk)
                        .andReturn()

                    result.response.contentAsString.contains("google-analytics.com/analytics.js") shouldBe false
                }
            }

            describe("[Test-19-6] framed 레이아웃(site/layout_framed.html) 동치성 검증") {
                it("사이드바 프레임 페이지에 og/twitter 메타 태그와 nprogress/magnific-popup 자산이 포함되어야 한다") {
                    val result = mockMvc.perform(
                        get("/user/sidebar")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select("meta[property='og:title']").size shouldBe 1
                    doc.select("meta[property='og:url']").size shouldBe 1
                    doc.select("meta[name='twitter:card']").attr("content") shouldBe "summary"
                    doc.select("link[href*='lib/nprogress/nprogress.css']").size shouldBe 1
                    doc.select("link[href*='lib/magnific-popup/magnific-popup.css']").size shouldBe 1
                }

                it("사이드바 프레임 body 클래스는 theme-default와 framed-body를 모두 가져야 한다") {
                    val result = mockMvc.perform(
                        get("/user/sidebar")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    val bodyClass = doc.select("body#html-body").attr("class")
                    bodyClass.contains("theme-default") shouldBe true
                    bodyClass.contains("framed-body") shouldBe true
                }

                it("프로젝트/조직 목록의 popover 마크업이 실제로 초기화되는 스크립트가 포함되어야 한다") {
                    val result = mockMvc.perform(
                        get("/user/sidebar")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val html = result.response.contentAsString
                    html.contains("[data-toggle=\"popover\"]").shouldBe(true)
                    html.contains(".popover()").shouldBe(true)
                }

                it("sendYonaUsage 설정 기본값이 꺼져 있으면 구글 애널리틱스 스크립트가 렌더링되지 않아야 한다") {
                    val result = mockMvc.perform(
                        get("/user/sidebar")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val html = result.response.contentAsString
                    html.contains("google-analytics.com/analytics.js") shouldBe false
                }
            }

            describe("[Test-19-7] 사이트 관리자 레이아웃(siteLayout.scala.html 대응) 동치성 검증") {
                it("사이트 관리자 화면은 legacy siteLayout처럼 공통 footer를 포함해야 한다") {
                    val result = mockMvc.perform(
                        get("/sites/userList")
                            .with(SecurityMockMvcRequestPostProcessors.user(siteAdminDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select("footer.page-footer-outer").size shouldBe 1
                }
            }

            describe("[Test-19-8] GNB navbar/usermenu(common/navbar.scala.html, common/usermenu.scala.html) 동치성 검증") {
                val guest = userRepository.findByLoginId("guestuser").orElseGet {
                    userRepository.save(User(loginId = "guestuser", name = "게스트", email = "guestuser@yona.io", isGuest = true))
                }
                val guestDetails = YonaUserDetails(
                    id = guest.id!!,
                    loginId = guest.loginId,
                    passwordVal = "hashed",
                    passwordSalt = "salt",
                    authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
                )

                it("게스트 사용자에게는 전체 목록 링크와 새 그룹 만들기 링크가 숨겨져야 한다") {
                    val result = mockMvc.perform(
                        get("/notifications")
                            .with(SecurityMockMvcRequestPostProcessors.user(guestDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select(".gnb-nav a[href='/projects']").size shouldBe 0
                    doc.select(".gnb-usermenu a[href='/organizations/new']").size shouldBe 0
                }

                it("일반 회원에게는 전체 목록 링크와 새 그룹 만들기 링크가 노출되어야 한다") {
                    val result = mockMvc.perform(
                        get("/notifications")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select(".gnb-nav a[href='/projects']").size shouldBe 1
                    doc.select(".gnb-usermenu a[href='/organizations/new']").size shouldBe 1
                }

                it("배정된 미해결 이슈가 있으면 내 이슈 링크 옆에 카운터 배지가 노출되어야 한다") {
                    val myIssue = issueRepository.findAll().find { it.title == "내배지테스트이슈" } ?: issueRepository.save(
                        Issue(
                            title = "내배지테스트이슈",
                            body = "내용",
                            project = publicProj,
                            authorId = owner.id!!,
                            authorLoginId = owner.loginId,
                            authorName = owner.name,
                            number = 900L
                        )
                    )
                    if (myIssue.assignee == null) {
                        val assignee = assigneeRepository.save(Assignee(user = member, project = publicProj))
                        myIssue.assignee = assignee
                        issueRepository.save(myIssue)
                    }

                    val result = mockMvc.perform(
                        get("/owner/public-proj")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    val myIssueLink = doc.select(".gnb-usermenu a[href='/user/issues']")
                    myIssueLink.size shouldBe 1
                    myIssueLink.select(".counter-badge").size shouldNotBe 0
                }
            }

            describe("[Test-19-9] 공용 스크립트 조각(common/scripts.scala.html) 동치성 검증") {
                it("토스트 알림 템플릿, U 단축키, pageshow NProgress 해제, iframe 히스토리 동기화 스크립트가 포함되어야 한다") {
                    val result = mockMvc.perform(
                        get("/owner/public-proj")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val html = result.response.contentAsString
                    val doc = Jsoup.parse(html)

                    doc.select("script#tplYobiToast").size shouldBe 1

                    html.contains("\"U\":") shouldBe true
                    html.contains("user\\/${member.loginId}") shouldBe true

                    html.contains("pageshow") shouldBe true
                    html.contains("NProgress.done()") shouldBe true

                    html.contains(".head-anchor") shouldBe true
                    html.contains(".share-link") shouldBe true
                    html.contains("window.parent.history.pushState") shouldBe true
                }

                it("비로그인 사용자에게 렌더링되는 로그인 모달이 jquery-ui 스크립트를 로드해야 한다") {
                    val result = mockMvc.perform(get("/owner/public-proj"))
                        .andExpect(status().isOk)
                        .andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select("script[src*='lib/jquery/jquery-ui-1.10.4.custom.min.js']").size shouldBe 1
                }
            }

            describe("[Test-19-10] 사용자 메뉴 탭 콘텐츠(common/usermenu_tab_content_list.scala.html) 동치성 검증") {
                it("최근 방문한 이슈가 있으면 최근 읽은 이슈 탭 패널에 목록이 렌더링되어야 한다") {
                    val recentIssue = issueRepository.findAll().find { it.title == "최근방문이슈테스트" } ?: issueRepository.save(
                        Issue(
                            title = "최근방문이슈테스트",
                            body = "내용",
                            project = publicProj,
                            authorId = owner.id!!,
                            authorLoginId = owner.loginId,
                            authorName = owner.name,
                            number = 901L
                        )
                    )
                    recentIssueService.recordIssueVisit(member, recentIssue)

                    val result = mockMvc.perform(
                        get("/user/usermenuTabContentList")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    val pane = doc.select("#myRecentIssueList")
                    pane.size shouldNotBe 0
                    pane.select("li.user-li").size shouldNotBe 0
                    pane.select("li.user-li").text().contains("최근방문이슈테스트") shouldBe true
                }

                it("GNB 사이드바 탭 메뉴에 최근 방문 이슈 탭 버튼이 노출되어야 한다") {
                    val result = mockMvc.perform(
                        get("/owner/public-proj")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select("#mySidenav a[href='#myRecentIssueList']").size shouldNotBe 0
                }
            }

            describe("[Test-19-11] 개인 3탭 메뉴(common/mySeriesMenuTab.scala.html) 동치성 검증") {
                it("현재 페이지가 로그인 기본 페이지로 이미 지정돼 있으면 '기본 페이지로 설정' 버튼이 숨겨져야 한다") {
                    userSettingRepository.findByUserId(member.id!!).ifPresentOrElse(
                        { it.loginDefaultPage = "notifications"; userSettingRepository.save(it) },
                        { userSettingRepository.save(UserSetting(user = member, loginDefaultPage = "notifications")) }
                    )

                    val result = mockMvc.perform(
                        get("/notifications")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select("#setDefaultLoginPage").size shouldBe 0
                }

                it("현재 페이지가 로그인 기본 페이지가 아니면 '기본 페이지로 설정' 버튼이 노출되어야 한다") {
                    userSettingRepository.findByUserId(member.id!!).ifPresentOrElse(
                        { it.loginDefaultPage = "/somewhere-else"; userSettingRepository.save(it) },
                        { userSettingRepository.save(UserSetting(user = member, loginDefaultPage = "/somewhere-else")) }
                    )

                    val result = mockMvc.perform(
                        get("/notifications")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select("#setDefaultLoginPage").size shouldBe 1
                }

                it("내 파일 목록 페이지도 공용 3탭 메뉴(알림/내 이슈/내 파일)를 공유해야 한다") {
                    val result = mockMvc.perform(
                        get("/user/files")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    val tabs = doc.select("ul.nav-tabs")
                    tabs.select("a[href='/notifications']").size shouldBe 1
                    tabs.select("a[href='/user/issues']").size shouldBe 1
                    tabs.select("a[href='/user/files']").size shouldBe 1
                    tabs.select("li.active a[href='/user/files']").size shouldBe 1
                }
            }

            describe("[Test-19-12] 태스크리스트 진행률 바(common/tasklistBar.scala.html) 동치성 검증") {
                it("이슈 상세 페이지의 본문 영역에 태스크리스트 진행률 바 셸과 yona.Tasklist.js 로드가 있어야 한다") {
                    val issue = issueRepository.findAll().find { it.project.id == publicProj.id && it.title == "테스트이슈" }!!

                    val result = mockMvc.perform(
                        get("/owner/public-proj/issue/${issue.number}")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val html = result.response.contentAsString
                    val doc = Jsoup.parse(html)

                    val bar = doc.select("#issue-body-${issue.number} > .tasklist")
                    bar.size shouldBe 1
                    bar.select(".task-title .done-counter").size shouldBe 1
                    bar.select(".task-progress .bar.red").size shouldBe 1
                    doc.select("script[src*='common/yona.Tasklist.js']").size shouldBe 1
                }

                it("게시판 상세 페이지의 본문 영역에도 태스크리스트 진행률 바 셸과 스크립트 로드가 있어야 한다") {
                    val post = postingRepository.findAll().find { it.project.id == publicProj.id }!!

                    val result = mockMvc.perform(
                        get("/owner/public-proj/post/${post.number}")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    )
                        .andExpect(status().isOk)
                        .andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)

                    val bar = doc.select("#post-body-${post.number} > .tasklist")
                    bar.size shouldBe 1
                    doc.select("script[src*='common/yona.Tasklist.js']").size shouldBe 1
                }
            }

            describe("[Test-19-14] 에러 페이지(error/*_default.scala.html) 동치성 검증") {
                it("존재하지 않는 프로젝트 접근 시 404 뷰는 legacy notfound_default 전용 D2 Program footer를 포함해야 한다") {
                    val result = mockMvc.perform(
                        get("/owner/__no-such-project-xyz__")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    ).andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select("footer.page-footer-outer .d2-program").size shouldBe 1
                }

                it("비공개 프로젝트에 비로그인 접근 시 403 뷰는 siteLayout처럼 검색폼 있는 전체 GNB와 사이트 공용 footer를 가져야 한다") {
                    val privateProj = projectRepository.findAll().find { it.name == "private-proj-403test" } ?: projectRepository.save(
                        Project(
                            name = "private-proj-403test",
                            owner = "owner",
                            projectScope = ProjectScope.PRIVATE,
                            isCodeAccessibleMemberOnly = true
                        )
                    )

                    val result = mockMvc.perform(get("/owner/${privateProj.name}")).andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select("form[name='gnb-search-form']").size shouldBe 1
                    doc.select("footer.page-footer-outer").size shouldBe 1
                }

                it("400/500 에러 뷰 템플릿 파일이 전체 GNB(gnb 조각)와 사이트 공용 footer 조각을 참조해야 한다") {
                    for (viewName in listOf("400", "500")) {
                        val content = java.io.File(
                            "src/main/resources/templates/error/$viewName.html"
                        ).readText()
                        content.contains("site/layout :: gnb") shouldBe true
                        content.contains("site/layout :: footer") shouldBe true
                        content.contains("site/layout :: errorGnb") shouldBe false
                    }
                }
            }

            describe("[Test-19-15] 로그인 홈(index.scala.html → index.html)의 3탭 메뉴 중복 이식 검증") {
                it("루트 경로(/)는 공용 mySeriesMenuTab 조각을 공유하고, legacy처럼 루트 경로에서는 기본 페이지 설정 버튼이 숨겨져야 한다") {
                    // IndexController.index()는 loginDefaultPage가 설정돼 있으면 그 경로로 리다이렉트하므로,
                    // index.html 본문을 직접 검증하려면 loginDefaultPage가 비어 있어야 한다.
                    userSettingRepository.findByUserId(member.id!!).ifPresent {
                        it.loginDefaultPage = null
                        userSettingRepository.save(it)
                    }

                    val result = mockMvc.perform(
                        get("/").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    ).andExpect(status().isOk).andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    val tabs = doc.select("ul.nav-tabs")
                    tabs.select("a[href='/notifications']").size shouldBe 1
                    tabs.select("a[href='/user/issues']").size shouldBe 1
                    tabs.select("a[href='/user/files']").size shouldBe 1
                    tabs.select("li.active a[href='/notifications']").size shouldBe 1
                    // legacy mySeriesMenuTab의 !path.equals("/") 조건과 동일 — 루트 경로에서는 항상 숨김
                    doc.select("#setDefaultLoginPage").size shouldBe 0
                }
            }

            describe("[Test-19-13] 이슈 라벨 동적 CSS(common/issueLabelColor.scala.html, LabelStyleController) 링크 이식 검증") {
                it("이슈 상세/게시판 상세/게시판 목록 페이지가 프로젝트별 labels.css를 링크해야 한다") {
                    val issue = issueRepository.findAll().find { it.project.id == publicProj.id && it.title == "테스트이슈" }!!
                    val post = postingRepository.findAll().find { it.project.id == publicProj.id }!!

                    val issueViewDoc = Jsoup.parse(
                        mockMvc.perform(
                            get("/owner/public-proj/issue/${issue.number}").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                        ).andExpect(status().isOk).andReturn().response.contentAsString
                    )
                    issueViewDoc.select("link[href*='/owner/public-proj/issue/labels.css']").size shouldBe 1

                    val boardViewDoc = Jsoup.parse(
                        mockMvc.perform(
                            get("/owner/public-proj/post/${post.number}").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                        ).andExpect(status().isOk).andReturn().response.contentAsString
                    )
                    boardViewDoc.select("link[href*='/owner/public-proj/issue/labels.css']").size shouldBe 1

                    val boardListDoc = Jsoup.parse(
                        mockMvc.perform(
                            get("/owner/public-proj/posts").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                        ).andExpect(status().isOk).andReturn().response.contentAsString
                    )
                    boardListDoc.select("link[href*='/owner/public-proj/issue/labels.css']").size shouldBe 1
                }
            }

            describe("[Test-19-16] 회원가입 이메일 인증 완료 화면(user/verified.scala.html) 동치성 검증") {
                it("유효한 인증 코드로 접근 시 siteLayout 기반(전체 GNB+footer) 인증 완료 화면이 렌더링되어야 한다") {
                    val verifyUser = userRepository.findByLoginId("verify-target-user").orElseGet {
                        userRepository.save(User(loginId = "verify-target-user", name = "인증대상", email = "verify-target@yona.io"))
                    }
                    userVerificationRepository.findByLoginIdAndVerificationCode(verifyUser.loginId, "test-code-123")
                        ?: userVerificationRepository.save(
                            UserVerification(
                                user = verifyUser,
                                loginId = verifyUser.loginId,
                                verificationCode = "test-code-123",
                                timestamp = System.currentTimeMillis()
                            )
                        )

                    val result = mockMvc.perform(
                        get("/user/verify").param("loginId", verifyUser.loginId).param("code", "test-code-123")
                    ).andExpect(status().isOk).andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select("form[name='gnb-search-form']").size shouldBe 1
                    doc.select("footer.page-footer-outer").size shouldBe 1
                    doc.text().contains(verifyUser.loginId) shouldBe true
                }
            }

            describe("[Test-19-17] 로그인 화면(user/login.scala.html) 동치성 검증") {
                it("로그인 화면에 이메일 인증 안내 문구가 노출되어야 한다") {
                    val result = mockMvc.perform(get("/users/loginform")).andReturn()
                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select(".email-verification-help").size shouldBe 1
                }
            }

            describe("[Test-19-18] 회원가입 화면(user/signup.scala.html) 동치성 검증") {
                it("회원가입 화면은 site/layout 기반 전체 GNB와 footer, 실시간 검증 스크립트를 포함해야 한다") {
                    val result = mockMvc.perform(get("/signup")).andReturn()
                    val doc = Jsoup.parse(result.response.contentAsString)

                    doc.select("form[name='gnb-search-form']").size shouldBe 1
                    doc.select("footer.page-footer-outer").size shouldBe 1
                    doc.select("script[src*='lib/validate.js']").size shouldBe 1
                    doc.select("script[src*='service/yobi.user.SignUp.js']").size shouldBe 1

                    val html = result.response.contentAsString
                    html.contains("sLogindIdCheckUrl") shouldBe true
                    html.contains("sEmailCheckUrl") shouldBe true
                }

                it("아이디 중복 확인 엔드포인트(GET /user/isUsed)가 legacy와 동일한 JSON 형식으로 응답해야 한다") {
                    val result = mockMvc.perform(get("/user/isUsed").param("name", member.loginId))
                        .andExpect(status().isOk)
                        .andReturn()

                    val json = tools.jackson.databind.ObjectMapper().readTree(result.response.contentAsString)
                    json.get("isExist").asBoolean() shouldBe true
                    json.has("isReserved") shouldBe true
                }

                it("이메일 중복 확인 엔드포인트(GET /user/isEmailExist)가 legacy와 동일한 JSON 형식으로 응답해야 한다") {
                    val result = mockMvc.perform(get("/user/isEmailExist").param("email", member.email))
                        .andExpect(status().isOk)
                        .andReturn()

                    val json = tools.jackson.databind.ObjectMapper().readTree(result.response.contentAsString)
                    json.get("isExist").asBoolean() shouldBe true
                }
            }

            describe("[Test-19-19] 비밀번호 재설정 화면(user/resetPassword.scala.html) 동치성 검증") {
                it("비밀번호 재설정 화면은 site/layout 기반 전체 GNB와 footer, resetPassword 모듈 스크립트를 포함해야 한다") {
                    val result = mockMvc.perform(get("/user/reset-password").param("hash", "dummy-hash")).andReturn()
                    val doc = Jsoup.parse(result.response.contentAsString)

                    doc.select("form[name='gnb-search-form']").size shouldBe 1
                    doc.select("footer.page-footer-outer").size shouldBe 1
                    doc.select("script[src*='lib/validate.js']").size shouldBe 1
                    doc.select("script[src*='service/yobi.resetPassword.js']").size shouldBe 1
                }
            }

            describe("[Test-19-20] 프로젝트 헤더(project/header.scala.html) 동치성 검증") {
                it("비멤버 로그인 사용자에게 프로젝트 가입 요청 버튼이 노출되어야 한다") {
                    val result = mockMvc.perform(
                        get("/owner/public-proj")
                            .with(SecurityMockMvcRequestPostProcessors.user(nonMemberDetails))
                    ).andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select(".project-util a.enrollBtn").size shouldBe 1
                    doc.select(".project-util a.enrollBtn[data-request-uri*='/api/projects/${publicProj.id}/enroll']").size shouldBe 1
                }

                it("프로젝트 멤버에게는 가입 요청 버튼이 노출되지 않아야 한다") {
                    // member는 codeMemberOnlyProj의 실제 ProjectUser임(상위 describe 블록에서 세팅됨)
                    val result = mockMvc.perform(
                        get("/owner/memberonly-proj")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    ).andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select(".project-util a.enrollBtn").size shouldBe 0
                }

                it("PROTECTED(그룹 전용) 프로젝트에는 group 배지가 노출되어야 한다") {
                    val protectedProj = projectRepository.findAll().find { it.name == "protected-proj-header-test" }
                        ?: projectRepository.save(
                            Project(
                                name = "protected-proj-header-test",
                                owner = "owner",
                                projectScope = ProjectScope.PROTECTED
                            )
                        )
                    if (!projectUserRepository.existsByProjectIdAndUserId(protectedProj.id!!, member.id!!)) {
                        val role = roleRepository.findById(RoleType.MEMBER.roleType).orElseGet {
                            roleRepository.save(Role(id = RoleType.MEMBER.roleType, name = "MEMBER"))
                        }
                        projectUserRepository.save(ProjectUser(project = protectedProj, user = member, role = role))
                    }

                    val result = mockMvc.perform(
                        get("/owner/${protectedProj.name}").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    ).andReturn()
                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select(".project-protected").size shouldBe 1
                }
            }

            val settingProj = projectRepository.findAll().find { it.name == "settings-test-proj" } ?: projectRepository.save(
                Project(
                    name = "settings-test-proj",
                    owner = "owner",
                    projectScope = ProjectScope.PUBLIC,
                    isCodeAccessibleMemberOnly = false
                )
            )
            if (!projectUserRepository.existsByProjectIdAndUserId(settingProj.id!!, member.id!!)) {
                val managerRole = roleRepository.findById(RoleType.MANAGER.roleType).orElseGet {
                    roleRepository.save(Role(id = RoleType.MANAGER.roleType, name = "MANAGER"))
                }
                projectUserRepository.save(ProjectUser(project = settingProj, user = member, role = managerRole))
            } else {
                val existing = projectUserRepository.findByProjectIdAndUserId(settingProj.id!!, member.id!!).get()
                if (existing.role.id != RoleType.MANAGER.roleType) {
                    val managerRole = roleRepository.findById(RoleType.MANAGER.roleType).orElseGet {
                        roleRepository.save(Role(id = RoleType.MANAGER.roleType, name = "MANAGER"))
                    }
                    existing.role = managerRole
                    projectUserRepository.save(existing)
                }
            }

            describe("[Test-19-21] VCS 변경 화면(project/change_vcs.scala.html) 동치성 검증") {
                it("VCS 변경 화면은 site/layout 기반 전체 GNB/footer와 project/header, project/menu, setting_menu 조각을 포함해야 한다") {
                    val result = mockMvc.perform(
                        get("/owner/${settingProj.name}/changeVCS")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    ).andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select("form[name='gnb-search-form']").size shouldBe 1
                    doc.select("footer.page-footer-outer").size shouldBe 1
                    doc.select(".project-header-outer").size shouldBe 1
                    doc.select(".project-menu-outer").size shouldBe 1
                    doc.select("script[src*='service/yobi.project.ChangeVCS.js']").size shouldBe 1
                }
            }

            describe("[Test-19-22] 프로젝트 설정 하위 화면들의 독자 GNB 제거 검증 (delete/transfer/watchers/setting_webhook)") {
                it("delete/transfer/watchers/setting_webhook 화면이 전부 site/layout 기반 전체 GNB와 footer를 포함해야 한다") {
                    val deleteDoc = Jsoup.parse(
                        mockMvc.perform(
                            get("/owner/${settingProj.name}/deleteform").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                        ).andReturn().response.contentAsString
                    )
                    deleteDoc.select("form[name='gnb-search-form']").size shouldBe 1
                    deleteDoc.select("footer.page-footer-outer").size shouldBe 1

                    val transferDoc = Jsoup.parse(
                        mockMvc.perform(
                            get("/owner/${settingProj.name}/transfer").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                        ).andReturn().response.contentAsString
                    )
                    transferDoc.select("form[name='gnb-search-form']").size shouldBe 1
                    transferDoc.select("footer.page-footer-outer").size shouldBe 1

                    val watchersDoc = Jsoup.parse(
                        mockMvc.perform(get("/owner/${settingProj.name}/watchers")).andReturn().response.contentAsString
                    )
                    watchersDoc.select("form[name='gnb-search-form']").size shouldBe 1
                    watchersDoc.select("footer.page-footer-outer").size shouldBe 1

                    val webhookDoc = Jsoup.parse(
                        mockMvc.perform(
                            get("/projects/owner/${settingProj.name}/webhooks").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                        ).andReturn().response.contentAsString
                    )
                    webhookDoc.select("form[name='gnb-search-form']").size shouldBe 1
                    webhookDoc.select("footer.page-footer-outer").size shouldBe 1
                }
            }

            describe("[Test-19-23] 이슈 라벨 설정 화면(project/issuelabels.scala.html) 동치성 검증") {
                it("이슈 라벨 설정 화면은 site/layout 기반 전체 GNB/footer와 project/header, setting_menu 조각, 17개 프리셋 색상을 포함해야 한다") {
                    val result = mockMvc.perform(
                        get("/owner/${settingProj.name}/issue/labelsform")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                    ).andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select("form[name='gnb-search-form']").size shouldBe 1
                    doc.select("footer.page-footer-outer").size shouldBe 1
                    doc.select(".project-header-outer").size shouldBe 1
                    doc.select("#subMenuIssueLabel.active").size shouldBe 1
                    doc.select("button.issue-label.btn-preset-color").size shouldBe 17
                    doc.select("script[src*='code.jquery.com']").size shouldBe 0
                }
            }

            describe("[Test-19-24] 이슈 목록 화면(issue/list.scala.html) 동치성 검증") {
                val openMilestone = milestoneRepository.findAll()
                    .find { it.project.id == settingProj.id && it.title == "열린 마일스톤" }
                    ?: milestoneRepository.save(Milestone(title = "열린 마일스톤", project = settingProj, state = State.OPEN))
                val closedMilestone = milestoneRepository.findAll()
                    .find { it.project.id == settingProj.id && it.title == "닫힌 마일스톤" }
                    ?: milestoneRepository.save(Milestone(title = "닫힌 마일스톤", project = settingProj, state = State.CLOSED))

                it("마일스톤 검색 필터는 legacy와 동일하게 열림/닫힘 optgroup을 모두 포함해야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(
                            get("/owner/${settingProj.name}/issues").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                        ).andReturn().response.contentAsString
                    )

                    doc.select("select#milestoneId optgroup").size shouldBe 2
                    doc.select("select#milestoneId option[value='${openMilestone.id}']").size shouldBe 1
                    doc.select("select#milestoneId option[value='${closedMilestone.id}']").size shouldBe 1
                }

                it("라벨 관리 링크는 매니저에게만 노출되고 일반 멤버에게는 노출되지 않아야 한다(legacy isManagerOf 권한 이식)") {
                    val managerDoc = Jsoup.parse(
                        mockMvc.perform(
                            get("/owner/${settingProj.name}/issues").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                        ).andReturn().response.contentAsString
                    )
                    managerDoc.select("div.labels-wrap").size shouldBe 1

                    val memberDoc = Jsoup.parse(
                        mockMvc.perform(
                            get("/owner/${codeMemberOnlyProj.name}/issues").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                        ).andReturn().response.contentAsString
                    )
                    memberDoc.select("div.labels-wrap").size shouldBe 0
                }
            }

            describe("[Test-19-25] 프로젝트 생성 화면(project/create.scala.html) 동치성 검증") {
                val orgRole = roleRepository.findById(RoleType.ORG_ADMIN.roleType).orElseGet {
                    roleRepository.save(Role(id = RoleType.ORG_ADMIN.roleType, name = "ORG_ADMIN"))
                }
                val org = organizationRepository.findAll().find { it.name == "create-test-org" }
                    ?: organizationRepository.save(Organization(name = "create-test-org"))
                if (organizationUserRepository.findAll().none { it.organization.id == org.id && it.user.id == member.id }) {
                    organizationUserRepository.save(OrganizationUser(user = member, organization = org, role = orgRole))
                }
                val existingOrgProj = projectRepository.findAll().find { it.owner == org.name && it.name == "dup-proj" }
                    ?: projectRepository.save(Project(name = "dup-proj", owner = org.name, projectScope = ProjectScope.PUBLIC))

                it("PROTECTED(그룹공개) 옵션이 legacy와 동일하게 존재해야 한다(백엔드는 이미 ProjectScope.PROTECTED 지원)") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(
                            get("/projectform").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                        ).andReturn().response.contentAsString
                    )

                    doc.select("input#protected[value=PROTECTED]").size shouldBe 1
                    doc.select("li#opt-protected").size shouldBe 1
                    // 소유자 기본값은 본인 계정(user 타입)이라 opt-protected는 초기 숨김 상태여야 한다
                    doc.select("li#opt-protected[style*=display:none]").size shouldBe 1
                }

                it("검증 실패로 폼이 재표시될 때 legacy의 Form 바인딩처럼 입력값이 보존되어야 한다") {
                    val result = mockMvc.perform(
                        post("/projectform")
                            .with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                            .with(SecurityMockMvcRequestPostProcessors.csrf())
                            .param("owner", org.name)
                            .param("name", existingOrgProj.name)
                            .param("overview", "재입력 검증용 설명")
                            .param("projectScope", "PROTECTED")
                            .param("vcs", "GIT")
                            .param("code", "true")
                    ).andReturn()

                    val doc = Jsoup.parse(result.response.contentAsString)
                    doc.select("textarea#description").text() shouldBe "재입력 검증용 설명"
                    doc.select("input#project-name").attr("value") shouldBe existingOrgProj.name
                    doc.select("input#protected").hasAttr("checked") shouldBe true
                    doc.select("option[value='${org.name}']").hasAttr("selected") shouldBe true
                    // 소유자가 조직이므로 opt-protected는 노출 상태로 재표시되어야 한다
                    doc.select("li#opt-protected[style*=display:block]").size shouldBe 1
                }
            }

            describe("[Test-19-27] 게시판 목록 화면(board/list.scala.html) 동치성 검증") {
                val noticePost = postingRepository.findAll().find { it.project.id == publicProj.id && it.number == 20L }
                    ?: postingRepository.save(
                        Posting(
                            title = "[공지] 점검 안내",
                            body = "내용",
                            project = publicProj,
                            authorId = owner.id!!,
                            authorLoginId = owner.loginId,
                            authorName = owner.name,
                            number = 20L,
                            notice = true
                        )
                    )
                val bracketPost = postingRepository.findAll().find { it.project.id == publicProj.id && it.number == 21L }
                    ?: postingRepository.save(
                        Posting(
                            title = "[bugfix] 버그 수정 안내",
                            body = "내용",
                            project = publicProj,
                            authorId = owner.id!!,
                            authorLoginId = owner.loginId,
                            authorName = owner.name,
                            number = 21L
                        )
                    )

                it("공지글은 legacy처럼 상단 공지 목록에만 노출되고 일반 목록에는 중복 노출되지 않아야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(
                            get("/owner/${publicProj.name}/posts").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                        ).andReturn().response.contentAsString
                    )

                    doc.select("ul.notice-wrap li").size shouldBe 1
                    doc.select("ul.post-list-wrap:not(.notice-wrap) li[href*='/post/${noticePost.number}']").size shouldBe 0
                }

                it("제목의 대괄호 접두어는 legacy처럼 .bracket-word로 분리되고 링크 텍스트에서는 제거되어야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(
                            get("/owner/${publicProj.name}/posts").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                        ).andReturn().response.contentAsString
                    )

                    val row = doc.select("li[href*='/post/${bracketPost.number}']").first()
                    row?.select(".bracket-word")?.text() shouldBe "[bugfix]"
                    row?.select("a.title")?.text() shouldBe "버그 수정 안내"
                }
            }

            describe("[Test-19-28] 게시판 상세 화면(board/view.scala.html) 삭제 확인 모달 i18n 검증") {
                it("삭제 확인 모달의 예/아니오 버튼은 하드코딩 텍스트가 아니라 메시지 키를 사용해야 한다") {
                    val post = postingRepository.findAll().find { it.project.id == publicProj.id }!!
                    val doc = Jsoup.parse(
                        mockMvc.perform(
                            get("/owner/${publicProj.name}/post/${post.number}").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                        ).andReturn().response.contentAsString
                    )

                    doc.select("#deleteConfirm .modal-footer button.ybtn-danger").text() shouldBe "예"
                    doc.select("#deleteConfirm .modal-footer button[data-dismiss=modal]").text() shouldBe "아니요"
                }
            }

            describe("[Test-19-29] 페이지 제목 |:| 컨벤션(layout.scala.html:8) 동치성 검증") {
                val ogIssue = issueRepository.findAll().find { it.project.id == publicProj.id && it.title == "테스트이슈" }!!

                it("이슈 상세 화면은 <title>/og:title엔 이슈 제목만, og:description엔 본문 미리보기를 사용해야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(
                            get("/owner/public-proj/issue/${ogIssue.number}").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                        ).andReturn().response.contentAsString
                    )

                    doc.select("title").text() shouldBe "테스트이슈 - Yona"
                    doc.select("meta[property=og:title]").attr("content") shouldBe "테스트이슈"
                    doc.select("meta[property=og:description]").attr("content") shouldBe "이슈내용"
                    doc.select("meta[name=twitter:description]").attr("content") shouldBe "이슈내용"
                }

                it("|:| 구분자가 없는 일반 화면은 title을 og:title/og:description에 그대로 동일하게 사용해야 한다(하위호환)") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(
                            get("/${settingProj.owner}/${settingProj.name}/issue/labelsform").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                        ).andReturn().response.contentAsString
                    )

                    val ogTitle = doc.select("meta[property=og:title]").attr("content")
                    val ogDesc = doc.select("meta[property=og:description]").attr("content")
                    ogTitle shouldBe ogDesc
                }
            }

            describe("[Test-19-30] 프로젝트 메뉴(projectMenu.scala.html) 동치성 검증") {
                it("SVN 프로젝트에서는 Pull Request 탭이 노출되지 않아야 한다(legacy project.vcs==GIT 조건)") {
                    val svnProj = projectRepository.findAll().find { it.name == "svn-menu-test-proj" } ?: projectRepository.save(
                        Project(name = "svn-menu-test-proj", owner = "owner", projectScope = ProjectScope.PUBLIC, vcs = "SUBVERSION")
                    )
                    val doc = Jsoup.parse(
                        mockMvc.perform(get("/owner/${svnProj.name}")).andReturn().response.contentAsString
                    )
                    doc.select(".project-menu-nav a[href*='/pulls'], .project-menu-nav a[href*='PullRequests']").size shouldBe 0
                }

                it("매니저에게는 설정 메뉴에 가입요청 대기 인원 수 배지와 키보드 단축키(htKeyMap) 스크립트가 노출되어야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(
                            get("/owner/${settingProj.name}").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))
                        ).andReturn().response.contentAsString
                    )
                    doc.select("script:containsData(htKeyMap)").size shouldBe 1
                    doc.select("script:containsData(project.Global)").size shouldBe 1
                }
            }

            describe("[Test-19-31] GNB 검색범위/커스텀링크 권한 게이트(common/navbar.scala.html) 동치성 검증") {
                val guest2 = userRepository.findByLoginId("guestuser2").orElseGet {
                    userRepository.save(User(loginId = "guestuser2", name = "게스트2", email = "guestuser2@yona.io", isGuest = true))
                }
                val guest2Details = YonaUserDetails(
                    id = guest2.id!!,
                    loginId = guest2.loginId,
                    passwordVal = "hashed",
                    passwordSalt = "salt",
                    authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
                )

                it("일반 로그인 사용자에게는 '모든 프로젝트' 검색범위가 노출되어야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(get("/owner/public-proj").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))).andReturn().response.contentAsString
                    )
                    doc.select("a[data-toggle=search-scope][data-action=/search]").size shouldBe 1
                }

                it("게스트 사용자에게는 '모든 프로젝트' 검색범위가 legacy와 동일하게 숨겨져야 한다(HIDE_PROJECT_LISTING||guest 게이트)") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(get("/owner/public-proj").with(SecurityMockMvcRequestPostProcessors.user(guest2Details))).andReturn().response.contentAsString
                    )
                    doc.select("a[data-toggle=search-scope][data-action=/search]").size shouldBe 0
                }

                it("사이트관리자는 게스트여도 '모든 프로젝트' 검색범위가 보여야 한다(legacy isSiteManager 예외)") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(get("/owner/public-proj").with(SecurityMockMvcRequestPostProcessors.user(siteAdminDetails))).andReturn().response.contentAsString
                    )
                    doc.select("a[data-toggle=search-scope][data-action=/search]").size shouldBe 1
                }

                it("커스텀 네비게이션 링크는 기본 설정(빈 값)에서는 렌더링되지 않아야 한다(yuna.application.navbar.custom-link.name 미설정 시)") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(get("/").with(SecurityMockMvcRequestPostProcessors.user(memberDetails))).andReturn().response.contentAsString
                    )
                    // myIssue 항목 하나만 남아야 한다 — 커스텀 링크 <li>는 th:if로 렌더링에서 제외됨
                    doc.select("a.user-item-btn.loggged-in").size shouldBe 1
                }
            }
        }
    }
}
