package com.github.search5.yona.web

import com.github.search5.yona.AbstractIntegrationTest
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.vcs.RepositoryService
import io.kotest.matchers.string.shouldContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext

// TASK-0259 (P-템플릿 그룹3 #45/#47/#50, BranchViewController/CodeViewController 담당분) 대응 —
// mockk 단위 테스트(CodeViewControllerSpec/BranchViewControllerSpec)는 뷰 이름만 확인하고 실제
// Thymeleaf 렌더링을 거치지 않으므로, 이 스펙은 실제 ViewResolver(webAppContextSetup)로 요청을 태워
// CodeViewController의 컨텍스트 인지형 에러 화면 전환(error/forbidden, error/notfound)이 문법
// 오류 없이 렌더링되고 기대한 문구/프로젝트 헤더가 실제 HTML에 나타나는지 확인한다. 이 세션에서
// mockk 테스트만 통과하고 실제 렌더링은 검증하지 않은 파샬에서 SpelEvaluationException이 실제로
// 발견된 전례가 있어(원인: 프래그먼트 인자 안의 T(...)/gathering 제약, th:with 미사용) 반드시 이
// 방식으로 검증한다.
//
// error/notfound.html·error/forbidden.html·error/badrequest.html과
// TemplateHelper.notFoundActiveMenu/notFoundReturnUrl/notFoundMessage는 P-템플릿 그룹3 작업에서
// 신설된 공용 자산이며(다른 병렬 워크트리에서 먼저 만들어졌다가 이번 세션에 함께 반영), 이 스펙은
// 그중 BranchViewController/CodeViewController가 실제로 사용하는 두 경로만 검증한다.
@Transactional
class ErrorPageTemplateRenderingSpec @Autowired constructor(
    private val webApplicationContext: WebApplicationContext,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val repositoryService: RepositoryService
) : AbstractIntegrationTest() {

    private val mockMvc: MockMvc by lazy { MockMvcBuilders.webAppContextSetup(webApplicationContext).build() }

    init {
        describe("에러 페이지 컨텍스트 인지형 렌더링 (TASK-0259, Branch/CodeViewController 담당분)") {
            // 이름이 고유한(errpage2- 접두) 픽스처만 만들고 클래스에 붙은 @Transactional이 각 테스트
            // 종료 시 롤백하므로, 다른 스펙의 데이터를 건드리는 전역 deleteAll()은 쓰지 않는다.

            it("멤버 전용 코드 프로젝트에 비멤버가 접근하면 error/forbidden이 프로젝트 헤더와 함께 실제로 렌더링돼야 한다 (CodeViewController#codeBrowser, yona CodeApp.java:60-62)") {
                // BootstrapSetupInterceptor는 DB에 유저가 0명이면 무조건 /bootstrap-setup으로
                // 리다이렉트하므로, 인증 없이 GET하는 이 화면도 유저를 최소 1명 만들어둬야 한다.
                userRepository.save(User(loginId = "errpage2-bootstrap1", name = "부트스트랩", email = "errpage2-bootstrap1@yona.io"))
                val project = projectRepository.save(
                    Project(
                        name = "errpage2-proj1",
                        owner = "errpage2-owner1",
                        vcs = "GIT",
                        projectScope = ProjectScope.PUBLIC,
                        isCodeAccessibleMemberOnly = true
                    )
                )

                val body = mockMvc.perform(get("/${project.owner}/${project.name}/code"))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                // messageKey 기본값 "error.forbidden" 메시지 + 프로젝트 헤더(owner/name breadcrumb)가
                // 실제 HTML에 함께 나타나야 한다(제네릭 error/403이었다면 프로젝트 헤더가 없다).
                body shouldContain "권한이 없습니다"
                body shouldContain project.name!!
            }

            it("존재하지 않는 브랜치로 코드 브라우저에 접근하면 error/notfound가 브랜치명을 포함한 메시지와 함께 실제로 렌더링돼야 한다 (CodeViewController#codeBrowserWithBranch, yona CodeApp.java:115-117)") {
                userRepository.save(User(loginId = "errpage2-bootstrap2", name = "부트스트랩2", email = "errpage2-bootstrap2@yona.io"))
                val project = projectRepository.save(
                    Project(name = "errpage2-proj2", owner = "errpage2-owner2", vcs = "GIT", projectScope = ProjectScope.PUBLIC)
                )
                // 커밋이 하나도 없는 빈 저장소만 실제로 만들어둔다 — 어떤 브랜치를 요청해도
                // getMetaDataFromAncestorDirectories()가 null을 반환해 notfound 경로를 탄다.
                repositoryService.getRepository(project).create()

                val body = mockMvc.perform(get("/${project.owner}/${project.name}/code/no-such-branch"))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                // targetType="code" -> error.notfound.code="{0} branch does not exist. Check project
                // default branch!" 메시지(title={0}=브랜치명) + 프로젝트 설정 페이지로 가는 복귀 링크
                // (TemplateHelper.notFoundReturnUrl의 "code" 케이스) + 프로젝트 헤더가 모두 실제
                // HTML에 나타나야 한다.
                body shouldContain "no-such-branch 브랜치가 없습니다"
                body shouldContain "/${project.owner}/${project.name}/setting"
                body shouldContain project.name!!
            }
        }
    }
}
