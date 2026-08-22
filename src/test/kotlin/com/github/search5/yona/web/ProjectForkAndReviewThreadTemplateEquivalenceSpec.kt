package com.github.search5.yona.web

import com.github.search5.yona.AbstractIntegrationTest
import com.github.search5.yona.domain.organization.Organization
import com.github.search5.yona.domain.organization.OrganizationRepository
import com.github.search5.yona.domain.organization.OrganizationUser
import com.github.search5.yona.domain.organization.OrganizationUserRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.pullrequest.CommentThread
import com.github.search5.yona.domain.pullrequest.CommentThreadRepository
import com.github.search5.yona.domain.pullrequest.ReviewComment
import com.github.search5.yona.domain.pullrequest.ReviewCommentRepository
import com.github.search5.yona.domain.pullrequest.SimpleCommentThread
import com.github.search5.yona.domain.role.Role
import com.github.search5.yona.domain.role.RoleRepository
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserIdent
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.user.YonaUserDetails
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.jsoup.Jsoup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

// 그룹11(PR/git 도메인) 서브셋 + project-domain #173 전용 동치성 회귀 스펙.
// TemplateEquivalenceSpec.kt는 다른 세션이 동시에 편집 중이라 건드리지 않고 별도 파일로 분리했다.
class ProjectForkAndReviewThreadTemplateEquivalenceSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val roleRepository: RoleRepository,
    private val organizationRepository: OrganizationRepository,
    private val organizationUserRepository: OrganizationUserRepository,
    private val commentThreadRepository: CommentThreadRepository,
    private val reviewCommentRepository: ReviewCommentRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        describe("project/fork.html, reviewthread/list.html, reviewthread/partial_list.html 동치성 회귀 검증") {
            val owner = userRepository.findByLoginId("pfr-owner").orElseGet {
                userRepository.save(User(loginId = "pfr-owner", name = "포크소유자", email = "pfr-owner@yona.io"))
            }
            val orgAdminMember = userRepository.findByLoginId("pfr-org-admin").orElseGet {
                userRepository.save(User(loginId = "pfr-org-admin", name = "조직관리자", email = "pfr-org-admin@yona.io"))
            }

            val publicProj = projectRepository.findAll().find { it.name == "pfr-public-proj" } ?: projectRepository.save(
                Project(
                    name = "pfr-public-proj",
                    owner = "pfr-owner",
                    projectScope = ProjectScope.PUBLIC,
                    isCodeAccessibleMemberOnly = false
                )
            )

            val orgAdminRole = roleRepository.findById(RoleType.ORG_ADMIN.roleType).orElseGet {
                roleRepository.save(Role(id = RoleType.ORG_ADMIN.roleType, name = "ORG_ADMIN"))
            }
            val org = organizationRepository.findAll().find { it.name == "pfr-org" } ?: organizationRepository.save(
                Organization(name = "pfr-org")
            )
            if (organizationUserRepository.findAll().none { it.organization.id == org.id && it.user.id == orgAdminMember.id }) {
                organizationUserRepository.save(OrganizationUser(user = orgAdminMember, organization = org, role = orgAdminRole))
            }

            val ownerDetails = YonaUserDetails(
                id = owner.id!!,
                loginId = owner.loginId,
                passwordVal = "hashed",
                passwordSalt = "salt",
                authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
            )
            val orgAdminDetails = YonaUserDetails(
                id = orgAdminMember.id!!,
                loginId = orgAdminMember.loginId,
                passwordVal = "hashed",
                passwordSalt = "salt",
                authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
            )

            describe("[#173] 프로젝트 포크 화면(project/fork.html ← legacy git/fork.scala.html) 동치성 검증") {
                it("legacy와 동일하게 GNB/프로젝트 헤더/메뉴(pullRequest 활성) 조각을 포함해야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(
                            get("/pfr-owner/pfr-public-proj/newFork").with(SecurityMockMvcRequestPostProcessors.user(orgAdminDetails))
                        ).andReturn().response.contentAsString
                    )

                    doc.select("form[name='gnb-search-form']").size shouldBe 1
                    doc.select(".project-header-outer").size shouldBe 1
                    doc.select(".project-menu-outer li.active a[href*='/pulls']").size shouldBe 1
                }

                it("폼 action은 fork POST 엔드포인트를 그대로 가리키고, hidden owner 필드는 기본 목적지(currentUser)로 채워져야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(
                            get("/pfr-owner/pfr-public-proj/newFork").with(SecurityMockMvcRequestPostProcessors.user(orgAdminDetails))
                        ).andReturn().response.contentAsString
                    )

                    val form = doc.select("form")
                    form.attr("action") shouldBe "/pfr-owner/pfr-public-proj/fork"
                    form.attr("method") shouldBe "post"

                    val hiddenOwner = doc.select("input[type=hidden][name=owner]")
                    hiddenOwner.size shouldBe 1
                    hiddenOwner.attr("value") shouldBe orgAdminMember.loginId
                }

                it("소유자 select#project-owner는 본인 계정 + 관리 조직 옵션을 legacy와 동일한 개수로 렌더링해야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(
                            get("/pfr-owner/pfr-public-proj/newFork").with(SecurityMockMvcRequestPostProcessors.user(orgAdminDetails))
                        ).andReturn().response.contentAsString
                    )

                    val select = doc.select("select#project-owner")
                    select.size shouldBe 1
                    select.select("option").size shouldBe 2
                    select.select("option[value='${orgAdminMember.loginId}']").size shouldBe 1
                    select.select("option[value='${org.name}']").size shouldBe 1
                }

                it("이미 포크된 프로젝트가 없는 기본 상태에서는 legacy의 fork.help 도움말 분기(이미지+안내문)가 노출되어야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(
                            get("/pfr-owner/pfr-public-proj/newFork").with(SecurityMockMvcRequestPostProcessors.user(orgAdminDetails))
                        ).andReturn().response.contentAsString
                    )

                    doc.select("#helpMessage img.img-polaroid").size shouldBe 1
                    doc.select("#helpMessage p.lead").size shouldBe 1
                    // forkedProjects 모델 속성이 아직 컨트롤러에서 내려오지 않아(TODO 문서화) "이미 존재" 분기는
                    // 구조상 존재하되 항상 비활성이어야 한다.
                    doc.select("#helpMessage .help-messages.center-txt").size shouldBe 0
                }

                it("공개 범위 라디오: PUBLIC/PRIVATE는 항상 노출되고, PROTECTED는 관리 중인 조직이 있을 때만 노출되어야 한다") {
                    val orgAdminDoc = Jsoup.parse(
                        mockMvc.perform(
                            get("/pfr-owner/pfr-public-proj/newFork").with(SecurityMockMvcRequestPostProcessors.user(orgAdminDetails))
                        ).andReturn().response.contentAsString
                    )
                    orgAdminDoc.select("input#public[value=PUBLIC]").size shouldBe 1
                    orgAdminDoc.select("input#private[value=PRIVATE]").size shouldBe 1
                    orgAdminDoc.select("input#protected[value=PROTECTED]").size shouldBe 1

                    val noOrgDoc = Jsoup.parse(
                        mockMvc.perform(
                            get("/pfr-owner/pfr-public-proj/newFork").with(SecurityMockMvcRequestPostProcessors.user(ownerDetails))
                        ).andReturn().response.contentAsString
                    )
                    noOrgDoc.select("input#public[value=PUBLIC]").size shouldBe 1
                    noOrgDoc.select("input#private[value=PRIVATE]").size shouldBe 1
                    noOrgDoc.select("input#protected[value=PROTECTED]").size shouldBe 0
                }

                it("project.Fork JS 모듈(yobi.project.Fork.js)이 legacy와 동일하게 로드되어야 한다") {
                    val html = mockMvc.perform(
                        get("/pfr-owner/pfr-public-proj/newFork").with(SecurityMockMvcRequestPostProcessors.user(orgAdminDetails))
                    ).andReturn().response.contentAsString

                    html.contains("loadModule(\"project.Fork\")") shouldBe true
                    Jsoup.parse(html).select("script[src*='service/yobi.project.Fork.js']").size shouldBe 1
                }
            }

            describe("[#184/#185] 리뷰 스레드 목록(reviewthread/list.html, reviewthread/partial_list.html) 동치성 검증") {
                val thread = commentThreadRepository.findAll()
                    .find { it.project?.id == publicProj.id && it.commitId == "pfr-review-commit-1" }
                    ?: run {
                        val saved = commentThreadRepository.save(
                            SimpleCommentThread(
                                author = UserIdent(owner),
                                state = CommentThread.ThreadState.OPEN,
                                project = publicProj,
                                commitId = "pfr-review-commit-1"
                            )
                        )
                        reviewCommentRepository.save(
                            ReviewComment(
                                contents = "리뷰 코멘트 테스트 내용",
                                author = UserIdent(owner),
                                thread = saved
                            )
                        )
                        saved
                    }

                it("legacy와 동일하게 GNB/프로젝트 헤더/메뉴(review 활성)와 검색/필터 사이드바를 포함해야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(
                            get("/pfr-owner/pfr-public-proj/reviews").with(SecurityMockMvcRequestPostProcessors.user(orgAdminDetails))
                        ).andReturn().response.contentAsString
                    )

                    doc.select("form[name='gnb-search-form']").size shouldBe 1
                    doc.select(".project-header-outer").size shouldBe 1
                    doc.select(".project-menu-outer li.active a[href*='/reviews']").size shouldBe 1

                    val searchForm = doc.select("form#search[name=search]")
                    searchForm.size shouldBe 1
                    searchForm.select("input[type=hidden][name=authorId]").size shouldBe 1
                    searchForm.select("input[type=hidden][name=participantId]").size shouldBe 1
                    searchForm.select("input[type=hidden][name=orderBy]").size shouldBe 1
                    searchForm.select("input[type=hidden][name=orderDir]").size shouldBe 1
                    searchForm.select("input[type=hidden][name=state]").size shouldBe 1
                    searchForm.select("input[name=filter]").size shouldBe 1
                }

                it("reviewthread/partial_list 조각이 목록 li(아바타/제목/작성자/작성일)를 legacy 구조대로 렌더링해야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(
                            get("/pfr-owner/pfr-public-proj/reviews").with(SecurityMockMvcRequestPostProcessors.user(orgAdminDetails))
                        ).andReturn().response.contentAsString
                    )

                    val items = doc.select(".review-list-wrap ul.post-list-wrap li.post-item")
                    items.size shouldBe 1

                    val item = items.first()!!
                    item.select(".post-id").text() shouldBe thread.id.toString()
                    item.select("a.title").text() shouldBe "리뷰 코멘트 테스트 내용"
                    item.select("a.title").attr("href").contains("#thread-${thread.id}") shouldBe true
                    item.select("a.avatar-wrap").size shouldBe 1
                    item.select(".infos .infos-item").isNotEmpty() shouldBe true
                }

                it("리뷰가 없는 검색 조건에서는 legacy의 error-wrap 빈 목록 메시지가 노출되어야 한다") {
                    val doc = Jsoup.parse(
                        mockMvc.perform(
                            get("/pfr-owner/pfr-public-proj/reviews")
                                .param("authorId", "999999999")
                                .with(SecurityMockMvcRequestPostProcessors.user(orgAdminDetails))
                        ).andReturn().response.contentAsString
                    )

                    doc.select(".review-list-wrap .error-wrap").size shouldBe 1
                    doc.select(".review-list-wrap ul.post-list-wrap").size shouldBe 0
                }

                it("엑셀 다운로드 링크와 #pagination, review.List JS 모듈 로드가 legacy와 동일하게 포함되어야 한다") {
                    val html = mockMvc.perform(
                        get("/pfr-owner/pfr-public-proj/reviews").with(SecurityMockMvcRequestPostProcessors.user(orgAdminDetails))
                    ).andReturn().response.contentAsString
                    val doc = Jsoup.parse(html)

                    doc.select("a[href*='format=xls']").size shouldBe 1
                    doc.select("#pagination").size shouldBe 1
                    html.contains("loadModule(\"review.List\"") shouldBe true
                    doc.select("script[src*='service/yobi.review.List.js']").size shouldBe 1
                }

                it("정렬 링크는 orderDir에 따라 down 아이콘/토글 방향이 legacy makeSortLink처럼 바뀌어야 한다") {
                    val descDoc = Jsoup.parse(
                        mockMvc.perform(
                            get("/pfr-owner/pfr-public-proj/reviews").with(SecurityMockMvcRequestPostProcessors.user(orgAdminDetails))
                        ).andReturn().response.contentAsString
                    )
                    val descLink = descDoc.select("a.filter[data-toggle=order][data-field=createdDate]")
                    descLink.size shouldBe 1
                    descLink.select("i.ico.btn-gray-arrow.down").size shouldBe 1
                    descLink.attr("data-value") shouldBe "asc"

                    val ascDoc = Jsoup.parse(
                        mockMvc.perform(
                            get("/pfr-owner/pfr-public-proj/reviews")
                                .param("orderDir", "asc")
                                .with(SecurityMockMvcRequestPostProcessors.user(orgAdminDetails))
                        ).andReturn().response.contentAsString
                    )
                    val ascLink = ascDoc.select("a.filter[data-toggle=order][data-field=createdDate]")
                    ascLink.size shouldBe 1
                    ascLink.select("i.ico.btn-gray-arrow.down").size shouldBe 0
                    ascLink.attr("data-value") shouldBe "desc"
                }
            }
        }
    }
}
