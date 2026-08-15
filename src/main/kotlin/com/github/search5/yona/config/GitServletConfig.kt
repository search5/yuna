package com.github.search5.yona.config

import org.eclipse.jgit.http.server.GitServlet
import org.eclipse.jgit.lfs.server.LfsProtocolServlet
import org.eclipse.jgit.lfs.server.LargeFileRepository
import org.eclipse.jgit.lfs.server.fs.FileLfsRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.servlet.ServletRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.File
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

@Configuration
class GitServletConfig(
    @Value("\${yuna.git.base-dir:/tmp/yuna/git}")
    private val baseDir: String,
    @Value("\${yuna.lfs.base-dir:/tmp/yuna/lfs}")
    private val lfsBaseDir: String,
    @Value("\${yuna.lfs.url:http://localhost:8080/git-lfs}")
    private val lfsUrl: String
) {

    @Bean
    fun gitServletRegistrationBean(): ServletRegistrationBean<HttpServlet> {
        val gitBaseDir = File(baseDir)
        if (!gitBaseDir.exists()) {
            gitBaseDir.mkdirs()
        }

        val gitServlet = GitServlet().apply {
            setRepositoryResolver { _, name ->
                val repoFile = File(gitBaseDir, name)
                val builder = org.eclipse.jgit.storage.file.FileRepositoryBuilder()
                builder.setGitDir(repoFile).build()
            }
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
                    gitServlet.service(req, res)
                }
            }
        }

        val registrationBean = ServletRegistrationBean<HttpServlet>(dispatcherServlet, "/git/*")
        registrationBean.setName("GitDispatcherServlet")
        return registrationBean
    }
}
