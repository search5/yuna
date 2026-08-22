package com.github.search5.yona.web

import com.github.search5.yona.AbstractIntegrationTest
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
import io.kotest.matchers.string.shouldContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext

// P-템플릿 그룹3 #45("존재하지 않는 페이지" 컨텍스트 인지형 404)/#47(컨텍스트 인지형 403)의
// error/notfound, error/forbidden 템플릿과 config/TemplateHelper.kt의
// notFoundMessage/notFoundActiveMenu/notFoundReturnUrl은 병렬 워크트리(에러 화면 그룹)에서 만든
// 것을 그대로 가져왔다(TASK-0259 — 아래 리뷰/리뷰스레드 컨트롤러 그룹은 그 화면들을 재사용만
// 한다). 다른 error/* 템플릿(#49 forbidden_organization, #50 badrequest, #53 GlobalExceptionHandler
// 등)에 대한 실제 렌더링 검증은 해당 그룹의 스펙 파일 쪽에서 담당하므로 이 파일에는 없다 — 병합
// 시 두 워크트리의 이 파일이 합쳐지면 그 테스트들도 함께 들어온다.
//
// mockk 단위 테스트는 뷰 이름만 확인하고 실제 Thymeleaf 렌더링을 거치지 않으므로, 이 스펙은 실제
// ViewResolver(webAppContextSetup)로 요청을 태워 리뷰/리뷰스레드 컨트롤러가 컨텍스트 인지형
// error/notfound, error/forbidden으로 바꾼 지점들이 문법 오류 없이 렌더링되고 기대한 문구/프로젝트
// 헤더가 실제 HTML에 나타나는지 확인한다.
@Transactional
class ErrorPageTemplateRenderingSpec @Autowired constructor(
    private val webApplicationContext: WebApplicationContext,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val roleRepository: RoleRepository
) : AbstractIntegrationTest() {

    private val mockMvc: MockMvc by lazy { MockMvcBuilders.webAppContextSetup(webApplicationContext).build() }

    init {
        describe("에러 페이지 컨텍스트 인지형 렌더링 — 리뷰/리뷰스레드 컨트롤러 (TASK-0259, P-템플릿 #45/#47)") {
            // errpage-rev- 접두 픽스처만 만들고 클래스에 붙은 @Transactional이 각 테스트 종료 시
            // 롤백하므로, 다른 스펙의 데이터를 건드리는 전역 deleteAll()은 쓰지 않는다.

            it("PR 리뷰어 등록(review)을 프로젝트 비멤버가 요청하면 error/forbidden이 프로젝트 헤더와 함께 렌더링돼야 한다 (ReviewApiController#review, P-템플릿 #47)") {
                // yona ReviewApp.java:41 @IsAllowed(Operation.ACCEPT, ResourceType.PULL_REQUEST) 대응.
                val project = projectRepository.save(
                    Project(name = "errpage-revproj1", owner = "errpage-revowner1", projectScope = ProjectScope.PUBLIC)
                )
                val outsider = userRepository.save(
                    User(loginId = "errpage-rev-outsider1", name = "리뷰외부인1", email = "errpage-rev-outsider1@yona.io")
                )
                val auth = UsernamePasswordAuthenticationToken(outsider.loginId, "pw")

                val body = mockMvc.perform(
                    post("/api/${project.owner}/${project.name}/pullRequest/999999/review").principal(auth)
                )
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "권한이 없습니다"
                body shouldContain project.name!!
            }

            it("존재하지 않는 PR에 리뷰어 등록(review)을 요청하면 error/notfound가 프로젝트 헤더와 함께 렌더링돼야 한다 (ReviewApiController#review, P-템플릿 #45)") {
                // yona IsAllowedAction.call()의 resourceObject == null 분기는 notfound.render(
                // "error.notfound", project, resourceType.resource())를 호출하는데 PULL_REQUEST의
                // resource()는 "pull_request"라 notfound.scala.html의 4개 case 중 어느 것과도
                // 매치되지 않고 항상 default(제네릭 문구)로 빠진다 — targetType을 비워 그 실제
                // 도달 분기를 그대로 재현했으므로 제네릭 "페이지를 찾을 수 없습니다" 문구가 나온다.
                val project = projectRepository.save(
                    Project(name = "errpage-revproj2", owner = "errpage-revowner2", projectScope = ProjectScope.PUBLIC)
                )
                val member = userRepository.save(
                    User(loginId = "errpage-rev-member1", name = "리뷰멤버1", email = "errpage-rev-member1@yona.io")
                )
                val memberRole = roleRepository.findById(RoleType.MEMBER.roleType).orElseGet {
                    roleRepository.save(Role(id = RoleType.MEMBER.roleType, name = "MEMBER"))
                }
                projectUserRepository.save(ProjectUser(user = member, project = project, role = memberRole))
                val auth = UsernamePasswordAuthenticationToken(member.loginId, "pw")

                val body = mockMvc.perform(
                    post("/api/${project.owner}/${project.name}/pullRequest/999999/review").principal(auth)
                )
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "페이지를 찾을 수 없습니다"
                body shouldContain project.name!!
            }

            it("PR 리뷰어 해제(unreview)를 프로젝트 비멤버가 요청하면 error/forbidden이 프로젝트 헤더와 함께 렌더링돼야 한다 (ReviewApiController#unreview, P-템플릿 #47)") {
                // yona ReviewApp.java:55 @IsAllowed(Operation.ACCEPT, ResourceType.PULL_REQUEST) 대응.
                val project = projectRepository.save(
                    Project(name = "errpage-revproj3", owner = "errpage-revowner3", projectScope = ProjectScope.PUBLIC)
                )
                val outsider = userRepository.save(
                    User(loginId = "errpage-rev-outsider2", name = "리뷰외부인2", email = "errpage-rev-outsider2@yona.io")
                )
                val auth = UsernamePasswordAuthenticationToken(outsider.loginId, "pw")

                val body = mockMvc.perform(
                    post("/api/${project.owner}/${project.name}/pullRequest/999999/unreview").principal(auth)
                )
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "권한이 없습니다"
                body shouldContain project.name!!
            }

            it("비공개 프로젝트의 리뷰 스레드 목록(reviewThreads)을 코드 접근 권한 없는 사용자가 요청하면 error/forbidden이 프로젝트 헤더와 함께 렌더링돼야 한다 (ReviewThreadController#reviewThreads, P-템플릿 #47)") {
                // yona ReviewThreadApp.java:41 @IsAllowed(Operation.READ) 대응.
                val project = projectRepository.save(
                    Project(name = "errpage-revproj4", owner = "errpage-revowner4", projectScope = ProjectScope.PRIVATE)
                )
                val outsider = userRepository.save(
                    User(loginId = "errpage-rev-outsider3", name = "리뷰외부인3", email = "errpage-rev-outsider3@yona.io")
                )
                val auth = UsernamePasswordAuthenticationToken(outsider.loginId, "pw")

                val body = mockMvc.perform(
                    get("/${project.owner}/${project.name}/reviews").principal(auth)
                )
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "권한이 없습니다"
                body shouldContain project.name!!
            }
        }
    }
}
