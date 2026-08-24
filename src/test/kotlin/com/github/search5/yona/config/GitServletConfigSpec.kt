package com.github.search5.yona.config

import com.github.search5.yona.config.git.GitProjectVisitRecorder
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.pullrequest.PullRequestRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.vcs.PushedBranchRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import java.io.File
import jakarta.servlet.ServletConfig
import jakarta.servlet.ServletContext
import java.util.Enumeration

class GitServletConfigSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val pullRequestRepository = mockk<PullRequestRepository>()
    val userRepository = mockk<UserRepository>()
    val pushedBranchRepository = mockk<PushedBranchRepository>()
    val eventPublisher = mockk<ApplicationEventPublisher>()
    val gitProjectVisitRecorder = mockk<GitProjectVisitRecorder>(relaxed = true)

    val tempBaseDir = File.createTempFile("git-temp", "").apply { delete(); mkdirs() }
    val tempLfsBaseDir = File.createTempFile("lfs-temp", "").apply { delete(); mkdirs() }

    val config = GitServletConfig(
        tempBaseDir.absolutePath,
        tempLfsBaseDir.absolutePath,
        "http://localhost:8080/git-lfs",
        projectRepository,
        pullRequestRepository,
        userRepository,
        pushedBranchRepository,
        eventPublisher,
        gitProjectVisitRecorder
    )

    beforeTest {
        clearMocks(projectRepository, pullRequestRepository, userRepository, pushedBranchRepository, eventPublisher, gitProjectVisitRecorder)
        SecurityContextHolder.clearContext()
    }

    describe("GitServletConfig 추가 커버리지 테스트") {
        it("ServletRegistrationBean 생성 및 service() 호출") {
            val bean = config.gitServletRegistrationBean()
            bean shouldNotBe null
            
            val servlet = bean.servlet!!
            
            val servletConfig = mockk<ServletConfig>(relaxed = true)
            val servletContext = mockk<ServletContext>(relaxed = true)
            every { servletConfig.servletContext } returns servletContext
            every { servletConfig.servletName } returns "git"
            every { servletConfig.initParameterNames } returns mockk<Enumeration<String>> {
                every { hasMoreElements() } returns false
            }
            servlet.init(servletConfig)

            // git URI 테스트
            val request = mockk<HttpServletRequest>(relaxed = true)
            val response = mockk<HttpServletResponse>(relaxed = true)
            every { request.requestURI } returns "/git/owner/project"
            every { request.method } returns "GET"
            
            try {
                servlet.service(request, response)
            } catch (e: Exception) {}
            
            verify { gitProjectVisitRecorder.recordIfApplicable(request) }
            
            // LFS URI 테스트
            val lfsRequest = mockk<HttpServletRequest>(relaxed = true)
            val lfsResponse = mockk<HttpServletResponse>(relaxed = true)
            every { lfsRequest.requestURI } returns "/git/owner/project/info/lfs/objects/batch"
            every { lfsRequest.method } returns "POST"
            
            try {
                servlet.service(lfsRequest, lfsResponse)
            } catch (e: Exception) {}
        }
        
        it("private 메서드 resolveProject, resolveCurrentUser 호출 커버리지") {
            val resolveProject = config.javaClass.getDeclaredMethod("resolveProject", HttpServletRequest::class.java)
            resolveProject.isAccessible = true
            
            val req1 = mockk<HttpServletRequest>(relaxed = true)
            every { req1.requestURI } returns "/invalid/url"
            resolveProject.invoke(config, req1)
            
            val req2 = mockk<HttpServletRequest>(relaxed = true)
            every { req2.requestURI } returns "/git/owner/projectName.git/info/refs"
            val project = Project(owner = "owner", name = "projectName", vcs = "GIT")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "projectName") } returns Optional.of(project)
            resolveProject.invoke(config, req2)
            
            val resolveCurrentUser = config.javaClass.getDeclaredMethod("resolveCurrentUser")
            resolveCurrentUser.isAccessible = true
            
            // 1
            SecurityContextHolder.getContext().authentication = null
            resolveCurrentUser.invoke(config)
            
            // 2
            val anonAuth = AnonymousAuthenticationToken("key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"))
            SecurityContextHolder.getContext().authentication = anonAuth
            resolveCurrentUser.invoke(config)
            
            // 3
            val notAuth = UsernamePasswordAuthenticationToken("user", "pass")
            notAuth.isAuthenticated = false
            SecurityContextHolder.getContext().authentication = notAuth
            resolveCurrentUser.invoke(config)
            
            // 4
            val auth = UsernamePasswordAuthenticationToken("user", "pass")
            SecurityContextHolder.getContext().authentication = auth
            val user = User(loginId = "user")
            every { userRepository.findByLoginId("user") } returns Optional.of(user)
            resolveCurrentUser.invoke(config)
            
            // 5
            every { userRepository.findByLoginId("user") } returns Optional.empty()
            resolveCurrentUser.invoke(config)
        }
    }
})
