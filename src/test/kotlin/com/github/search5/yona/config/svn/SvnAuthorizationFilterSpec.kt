package com.github.search5.yona.config.svn

import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectService
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletResponse
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import java.util.Optional

// yona SvnApp.java(119-131, AccessControl.isAllowed 위임)의 두 가지 축소 지점 대응 (P1-23):
// 1) PROTECTED 프로젝트가 PUBLIC과 동일하게 인증 없이 열람 가능했던 문제
// 2) 게스트(isGuest) 계정이 공개 프로젝트라도 읽기를 거부당해야 하는데 그 검사가 전혀 없던 문제
class SvnAuthorizationFilterSpec : DescribeSpec({
    val projectService = mockk<ProjectService>()
    val userRepository = mockk<UserRepository>()
    val filter = SvnAuthorizationFilter(projectService, userRepository)
    val filterChain = mockk<FilterChain>(relaxed = true)

    beforeTest {
        clearMocks(projectService, userRepository, filterChain)
        SecurityContextHolder.clearContext()
    }

    describe("SvnAuthorizationFilter") {
        it("존재하지 않는 프로젝트 요청 시 404를 응답해야 한다") {
            val request = MockHttpServletRequest("GET", "/svn/gildong/non-exist")
            val response = MockHttpServletResponse()
            every { projectService.findByOwnerAndName("gildong", "non-exist") } returns null

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_NOT_FOUND
            verify(exactly = 0) { filterChain.doFilter(any(), any()) }
        }

        it("PUBLIC 프로젝트의 읽기 요청은 익명 사용자도 통과되어야 한다") {
            val request = MockHttpServletRequest("PROPFIND", "/svn/gildong/public-repo/trunk")
            val response = MockHttpServletResponse()
            val project = Project(owner = "gildong", name = "public-repo", vcs = "SUBVERSION", projectScope = ProjectScope.PUBLIC)
            every { projectService.findByOwnerAndName("gildong", "public-repo") } returns project

            val auth = AnonymousAuthenticationToken("key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"))
            SecurityContextHolder.setContext(SecurityContextImpl(auth))

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_OK
            verify(exactly = 1) { filterChain.doFilter(any(), any()) }
        }

        it("PUBLIC 프로젝트라도 쓰기 요청 시 익명 사용자는 401을 응답해야 한다") {
            val request = MockHttpServletRequest("PUT", "/svn/gildong/public-repo/trunk/a.txt")
            val response = MockHttpServletResponse()
            val project = Project(owner = "gildong", name = "public-repo", vcs = "SUBVERSION", projectScope = ProjectScope.PUBLIC)
            every { projectService.findByOwnerAndName("gildong", "public-repo") } returns project

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_UNAUTHORIZED
            verify(exactly = 0) { filterChain.doFilter(any(), any()) }
        }

        it("PRIVATE 프로젝트 요청 시 익명 사용자는 401을 응답해야 한다") {
            val request = MockHttpServletRequest("PROPFIND", "/svn/gildong/private-repo/trunk")
            val response = MockHttpServletResponse()
            val project = Project(owner = "gildong", name = "private-repo", vcs = "SUBVERSION", projectScope = ProjectScope.PRIVATE)
            every { projectService.findByOwnerAndName("gildong", "private-repo") } returns project

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_UNAUTHORIZED
            verify(exactly = 0) { filterChain.doFilter(any(), any()) }
        }

        it("PRIVATE 프로젝트 요청 시 멤버가 아닌 인증된 유저는 403을 응답해야 한다") {
            val request = MockHttpServletRequest("PROPFIND", "/svn/gildong/private-repo/trunk")
            val response = MockHttpServletResponse()
            val project = Project(id = 1L, owner = "gildong", name = "private-repo", vcs = "SUBVERSION", projectScope = ProjectScope.PRIVATE)
            every { projectService.findByOwnerAndName("gildong", "private-repo") } returns project
            every { projectService.isMember(1L, "chulsoo") } returns false

            val auth = UsernamePasswordAuthenticationToken("chulsoo", "password", AuthorityUtils.createAuthorityList("ROLE_ACTIVE"))
            SecurityContextHolder.setContext(SecurityContextImpl(auth))

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_FORBIDDEN
            verify(exactly = 0) { filterChain.doFilter(any(), any()) }
        }

        it("PRIVATE 프로젝트 요청 시 멤버인 인증된 유저는 통과되어야 한다") {
            val request = MockHttpServletRequest("PROPFIND", "/svn/gildong/private-repo/trunk")
            val response = MockHttpServletResponse()
            val project = Project(id = 1L, owner = "gildong", name = "private-repo", vcs = "SUBVERSION", projectScope = ProjectScope.PRIVATE)
            every { projectService.findByOwnerAndName("gildong", "private-repo") } returns project
            every { projectService.isMember(1L, "chulsoo") } returns true

            val auth = UsernamePasswordAuthenticationToken("chulsoo", "password", AuthorityUtils.createAuthorityList("ROLE_ACTIVE"))
            SecurityContextHolder.setContext(SecurityContextImpl(auth))

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_OK
            verify(exactly = 1) { filterChain.doFilter(any(), any()) }
        }

        it("PROTECTED 프로젝트 요청 시 익명 사용자는 401을 응답해야 한다 (P1-23)") {
            val request = MockHttpServletRequest("PROPFIND", "/svn/gildong/protected-repo/trunk")
            val response = MockHttpServletResponse()
            val project = Project(id = 2L, owner = "gildong", name = "protected-repo", vcs = "SUBVERSION", projectScope = ProjectScope.PROTECTED)
            every { projectService.findByOwnerAndName("gildong", "protected-repo") } returns project

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_UNAUTHORIZED
            verify(exactly = 0) { filterChain.doFilter(any(), any()) }
        }

        it("PROTECTED 프로젝트 요청 시 멤버가 아닌 인증된 유저는 403을 응답해야 한다 (P1-23)") {
            val request = MockHttpServletRequest("PROPFIND", "/svn/gildong/protected-repo/trunk")
            val response = MockHttpServletResponse()
            val project = Project(id = 2L, owner = "gildong", name = "protected-repo", vcs = "SUBVERSION", projectScope = ProjectScope.PROTECTED)
            every { projectService.findByOwnerAndName("gildong", "protected-repo") } returns project
            every { projectService.isMember(2L, "chulsoo") } returns false

            val auth = UsernamePasswordAuthenticationToken("chulsoo", "password", AuthorityUtils.createAuthorityList("ROLE_ACTIVE"))
            SecurityContextHolder.setContext(SecurityContextImpl(auth))

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_FORBIDDEN
            verify(exactly = 0) { filterChain.doFilter(any(), any()) }
        }

        it("PROTECTED 프로젝트 요청 시 멤버인 인증된 유저는 통과되어야 한다 (P1-23)") {
            val request = MockHttpServletRequest("PROPFIND", "/svn/gildong/protected-repo/trunk")
            val response = MockHttpServletResponse()
            val project = Project(id = 2L, owner = "gildong", name = "protected-repo", vcs = "SUBVERSION", projectScope = ProjectScope.PROTECTED)
            every { projectService.findByOwnerAndName("gildong", "protected-repo") } returns project
            every { projectService.isMember(2L, "chulsoo") } returns true

            val auth = UsernamePasswordAuthenticationToken("chulsoo", "password", AuthorityUtils.createAuthorityList("ROLE_ACTIVE"))
            SecurityContextHolder.setContext(SecurityContextImpl(auth))

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_OK
            verify(exactly = 1) { filterChain.doFilter(any(), any()) }
        }

        it("PUBLIC 프로젝트라도 게스트 계정으로 인증된 요청은 403을 응답해야 한다 (P1-23, yona !user.isGuest 대응)") {
            val request = MockHttpServletRequest("PROPFIND", "/svn/gildong/public-repo/trunk")
            val response = MockHttpServletResponse()
            val project = Project(owner = "gildong", name = "public-repo", vcs = "SUBVERSION", projectScope = ProjectScope.PUBLIC)
            every { projectService.findByOwnerAndName("gildong", "public-repo") } returns project
            every { userRepository.findByLoginId("guest-user") } returns Optional.of(
                User(loginId = "guest-user", name = "게스트", email = "guest@yona.io", isGuest = true)
            )

            val auth = UsernamePasswordAuthenticationToken("guest-user", "password", AuthorityUtils.createAuthorityList("ROLE_ACTIVE"))
            SecurityContextHolder.setContext(SecurityContextImpl(auth))

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_FORBIDDEN
            verify(exactly = 0) { filterChain.doFilter(any(), any()) }
        }

        it("PUBLIC 프로젝트를 게스트가 아닌 인증된 유저가 요청하면 통과되어야 한다") {
            val request = MockHttpServletRequest("PROPFIND", "/svn/gildong/public-repo/trunk")
            val response = MockHttpServletResponse()
            val project = Project(owner = "gildong", name = "public-repo", vcs = "SUBVERSION", projectScope = ProjectScope.PUBLIC)
            every { projectService.findByOwnerAndName("gildong", "public-repo") } returns project
            every { userRepository.findByLoginId("chulsoo") } returns Optional.of(
                User(loginId = "chulsoo", name = "철수", email = "chulsoo@yona.io", isGuest = false)
            )

            val auth = UsernamePasswordAuthenticationToken("chulsoo", "password", AuthorityUtils.createAuthorityList("ROLE_ACTIVE"))
            SecurityContextHolder.setContext(SecurityContextImpl(auth))

            filter.doFilter(request, response, filterChain)

            response.status shouldBe HttpServletResponse.SC_OK
            verify(exactly = 1) { filterChain.doFilter(any(), any()) }
        }
    }
})
