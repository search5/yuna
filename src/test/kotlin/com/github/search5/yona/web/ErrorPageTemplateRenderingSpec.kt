package com.github.search5.yona.web

import com.github.search5.yona.AbstractIntegrationTest
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import io.kotest.matchers.string.shouldContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext

// P-템플릿 그룹3 error/notfound·error/forbidden(#45/#47) 실제 렌더링 검증 — mockk 단위 테스트(예:
// BoardViewControllerSpec/CompareViewControllerSpec)는 뷰 이름만 확인하고 실제 Thymeleaf 렌더링을
// 거치지 않으므로, 이 스펙은 실제 ViewResolver(webAppContextSetup)로 요청을 태워 새로 만든 컨텍스트
// 인지형 에러 화면들이 문법 오류 없이 렌더링되고 기대한 문구/링크가 실제 HTML에 나타나는지 확인한다.
//
// 이 worktree는 P-템플릿 그룹3(#45/#47/#49/#50/#53) 기반 작업이 완료된 커밋(04e4a06,
// [TASK-0255])의 자식이 아닌 별도 병렬 워크트리에서 분기됐다 — 그래서 error/notfound.html,
// error/forbidden.html, TemplateHelper.notFoundActiveMenu/notFoundReturnUrl/notFoundMessage,
// 그리고 이 스펙 파일 자체가 이 worktree에는 아직 없었다. Board/Compare 컨트롤러 작업(TASK-0259)에
// 필요한 만큼만(notfound.html/forbidden.html + 3개 헬퍼 메서드) 이식해 왔고, 이 스펙 파일도 지금
// 여기서 처음 만든다 — Organization(#49)/badrequest(#50)/413(#53) 관련 테스트는 해당 인프라
// (forbidden_organization.html, badrequest.html, GlobalExceptionHandler)가 아직 이 worktree에
// 없어 포함하지 않았다(다른 병렬 그룹의 몫).
@Transactional
class ErrorPageTemplateRenderingSpec @Autowired constructor(
    private val webApplicationContext: WebApplicationContext,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository
) : AbstractIntegrationTest() {

    private val mockMvc: MockMvc by lazy { MockMvcBuilders.webAppContextSetup(webApplicationContext).build() }

    init {
        describe("에러 페이지 컨텍스트 인지형 렌더링 (P-템플릿 그룹3 #45/#47, Board/Compare 담당분)") {
            // 이름이 고유한(errpage- 접두) 픽스처만 만들고 클래스에 붙은 @Transactional이 각
            // 테스트 종료 시 롤백하므로, 다른 스펙의 데이터를 건드리는 전역 deleteAll()은 쓰지 않는다.

            it("게시글을 찾지 못하면 error/notfound가 프로젝트 헤더/메뉴와 함께 실제로 렌더링돼야 한다 (BoardViewController#viewPost, #45)") {
                // BootstrapSetupInterceptor는 DB에 유저가 0명이면 무조건 /bootstrap-setup으로
                // 리다이렉트하므로, 인증 없이 GET하는 이 화면도 유저를 최소 1명 만들어둬야 한다.
                userRepository.save(User(loginId = "errpage-bootstrap-board", name = "부트스트랩", email = "errpage-bootstrap-board@yona.io"))
                val project = projectRepository.save(
                    Project(name = "errpage-board-proj", owner = "errpage-board-owner", projectScope = ProjectScope.PUBLIC)
                )

                val body = mockMvc.perform(get("/${project.owner}/${project.name}/post/999"))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                // targetType="board_post" -> error.notfound.board_post 메시지 + 게시글 목록으로 가는
                // 링크(TemplateHelper.notFoundReturnUrl) + 프로젝트 헤더(owner/name breadcrumb)가
                // 모두 실제 HTML에 나타나야 한다.
                body shouldContain "존재하지 않는 글입니다"
                body shouldContain "/${project.owner}/${project.name}/posts"
                body shouldContain project.name!!
            }

            it("비공개 프로젝트에 비회원이 게시글 목록에 접근하면 error/forbidden이 프로젝트 헤더와 함께 실제로 렌더링돼야 한다 (BoardViewController#listPosts, #47)") {
                val project = projectRepository.save(
                    Project(name = "errpage-board-proj2", owner = "errpage-board-owner2", projectScope = ProjectScope.PRIVATE)
                )
                val outsider = userRepository.save(
                    User(loginId = "errpage-board-outsider", name = "외부인", email = "errpage-board-outsider@yona.io")
                )
                val auth = UsernamePasswordAuthenticationToken(outsider.loginId, "pw")

                val body = mockMvc.perform(get("/${project.owner}/${project.name}/posts").principal(auth))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "권한이 없습니다"
                body shouldContain project.name!!
            }

            it("비공개 프로젝트에 비회원이 코드 비교 화면에 접근하면 error/forbidden이 프로젝트 헤더와 함께 실제로 렌더링돼야 한다 (CompareViewController#compare, #47)") {
                val project = projectRepository.save(
                    Project(name = "errpage-compare-proj", owner = "errpage-compare-owner", projectScope = ProjectScope.PRIVATE, vcs = "GIT")
                )
                val outsider = userRepository.save(
                    User(loginId = "errpage-compare-outsider", name = "외부인2", email = "errpage-compare-outsider@yona.io")
                )
                val auth = UsernamePasswordAuthenticationToken(outsider.loginId, "pw")

                val body = mockMvc.perform(
                    get("/${project.owner}/${project.name}/compare/aaaaaaa..bbbbbbb").principal(auth)
                )
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "권한이 없습니다"
                body shouldContain project.name!!
            }
        }
    }
}
