package com.github.search5.yona.config

import com.github.search5.yona.config.git.GitProjectVisitRecorder
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.pullrequest.PullRequestRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.vcs.PushedBranchRepository
import com.github.search5.yona.domain.vcs.RejectPushToReservedRefsPreReceiveHook
import com.github.search5.yona.domain.vcs.YonaPostReceiveHook
import org.eclipse.jgit.http.server.GitServlet
import org.eclipse.jgit.lfs.server.LfsProtocolServlet
import org.eclipse.jgit.lfs.server.LargeFileRepository
import org.eclipse.jgit.lfs.server.fs.FileLfsRepository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.ReceivePack
import org.eclipse.jgit.transport.resolver.ReceivePackFactory
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.servlet.ServletRegistrationBean
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.io.File
import java.util.regex.Pattern
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

@Configuration
class GitServletConfig(
    @Value("\${yona.git.base-dir:/tmp/yona/git}")
    private val baseDir: String,
    @Value("\${yona.lfs.base-dir:/tmp/yona/lfs}")
    private val lfsBaseDir: String,
    @Value("\${yona.lfs.url:http://localhost:8080/git-lfs}")
    private val lfsUrl: String,
    private val projectRepository: ProjectRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val userRepository: UserRepository,
    private val pushedBranchRepository: PushedBranchRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val gitProjectVisitRecorder: GitProjectVisitRecorder
) {
    private val logger = LoggerFactory.getLogger(GitServletConfig::class.java)

    // GitAuthorizationFilter의 URI 패턴과 동일하게 맞춰, 동일한 요청에서
    // 인가 필터와 push 훅이 같은 프로젝트를 가리키도록 보장한다.
    private val gitUriPattern = Pattern.compile("^/(git|git-lfs)/([^/]+)/([^/]+?)(?:\\.git)?(?:/.*)?$")

    @Bean
    fun gitServletRegistrationBean(): ServletRegistrationBean<HttpServlet> {
        val gitBaseDir = File(baseDir)
        if (!gitBaseDir.exists()) {
            gitBaseDir.mkdirs()
        }

        val gitServlet = GitServlet().apply {
            setRepositoryResolver { _, name ->
                val repoFile = File(gitBaseDir, name)
                val builder = FileRepositoryBuilder()
                builder.setGitDir(repoFile).build()
            }
            setReceivePackFactory(ReceivePackFactory { req, repo ->
                val receivePack = ReceivePack(repo)
                receivePack.setPreReceiveHook(RejectPushToReservedRefsPreReceiveHook())

                val project = resolveProject(req)
                val pusher = resolveCurrentUser()
                if (project != null && pusher != null) {
                    receivePack.setPostReceiveHook(
                        YonaPostReceiveHook(
                            project, pusher, projectRepository, pullRequestRepository, pushedBranchRepository, eventPublisher
                        )
                    )
                } else {
                    logger.warn(
                        "git push post-receive hook skipped: project or pusher could not be resolved (path='${req.pathInfo}')"
                    )
                }
                receivePack
            })
        }

        val lfsServlet = object : LfsProtocolServlet() {
            override fun getLargeFileRepository(request: LfsRequest, path: String, action: String): LargeFileRepository {
                println(">>> LFS debug: path='$path', action='$action'")
                
                var cleanPath = path
                if (cleanPath.startsWith("/git/")) {
                    cleanPath = cleanPath.substring("/git/".length)
                } else if (cleanPath.startsWith("/")) {
                    cleanPath = cleanPath.substring(1)
                }
                
                // 뒤쪽 info/lfs/objects/batch 부분 제거
                val suffixIndex = cleanPath.indexOf("/info/lfs/")
                if (suffixIndex != -1) {
                    cleanPath = cleanPath.substring(0, suffixIndex)
                }
                
                val parts = cleanPath.split("/")
                val owner = parts.getOrNull(0) ?: "default"
                val project = parts.getOrNull(1) ?: "default"
                
                println(">>> LFS parsed: owner='$owner', project='$project'")

                val projectLfsDir = File(lfsBaseDir, "$owner/$project")
                if (!projectLfsDir.exists()) {
                    projectLfsDir.mkdirs()
                }

                val projectLfsUrl = "$lfsUrl/$owner/$project"
                return FileLfsRepository(projectLfsUrl, projectLfsDir.toPath())
            }
        }

        // 단일 진입점 디스패처 서블릿 정의
        val dispatcherServlet = object : HttpServlet() {
            override fun service(req: HttpServletRequest, res: HttpServletResponse) {
                println(">>> Dispatcher received URI: '${req.requestURI}'")
                if (req.requestURI.contains("/info/lfs/")) {
                    lfsServlet.service(req, res)
                } else {
                    // yona GitApp.java:129-136 대응 (P2-09) — git 프로토콜로만 접근하는 사용자도
                    // "최근 방문 프로젝트"에 기록되도록 실제 RPC 처리 전에 방문을 남긴다.
                    gitProjectVisitRecorder.recordIfApplicable(req)
                    gitServlet.service(req, res)
                }
            }
        }

        val registrationBean = ServletRegistrationBean<HttpServlet>(dispatcherServlet, "/git/*")
        registrationBean.setName("GitDispatcherServlet")
        return registrationBean
    }

    private fun resolveProject(req: HttpServletRequest): Project? {
        val matcher = gitUriPattern.matcher(req.requestURI)
        if (!matcher.matches()) {
            return null
        }
        val owner = matcher.group(2)
        val projectName = matcher.group(3)
        // yona GitApp.java:95-104의 findByPreviousPlaceOf() 폴백 대응 (P1-76) — 프로젝트가
        // 이전/개명된 뒤에도 기존 git remote URL이 계속 동작해야 한다.
        return projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
    }

    private fun resolveCurrentUser(): User? {
        val authentication = SecurityContextHolder.getContext().authentication ?: return null
        if (!authentication.isAuthenticated ||
            authentication is AnonymousAuthenticationToken
        ) {
            return null
        }
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }
}
