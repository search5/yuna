package com.github.search5.yona.config

import com.github.search5.yona.AbstractIntegrationTest
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.vcs.BareCommit
import com.github.search5.yona.domain.vcs.RepositoryService
import io.kotest.assertions.withClue
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.io.File
import java.nio.file.Files

// TASK-0416 회귀 방지: yona-cli의 골든패스를 실제 `git clone` 커맨드로 재현하다가 발견한 버그.
//
// 근본원인: GitServletConfig가 만드는 디스패처 서블릿은 JGit의 GitServlet(내부 GitFilter)을
// 컨테이너에 등록하지 않고 이 디스패처의 service()에서 gitServlet.service(req,res)를 수동으로
// 호출만 해왔다. 그런데 GitServlet(MetaServlet 상속)은 init(ServletConfig) 시점에 비로소
// GitFilter가 upload-pack/receive-pack/info-refs 등 URL 파이프라인(bindings)을 등록한다.
// init()이 한 번도 호출되지 않으면 파이프라인이 텅 빈 채로 남아, 모든 요청이 첫 매치 실패로
// 기본 체인(chain.doFilter)에 떨어져 예외 없이 조용히 404를 반환한다 — RepositoryResolver까지
// 도달하지도 못했다. mockk 기반 GitServletConfigSpec은 servlet.service() 호출을 통째로
// try/catch로 감싸고 결과를 검증하지 않아 이 문제를 놓쳤었다.
//
// 이 스펙은 webEnvironment=RANDOM_PORT로 실제 임베디드 톰캣을 띄워 ServletRegistrationBean이
// 정상적인 서블릿 생명주기(init 포함)를 타게 하고, 실제 `git` 바이너리로 스마트 HTTP 프로토콜
// clone까지 재현해 회귀를 고정한다(MockMvc는 DispatcherServlet만 태우고 이 raw 서블릿을
// 우회하므로 이 버그를 검증할 수 없다).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GitSmartHttpProtocolIntegrationSpec @Autowired constructor(
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val repositoryService: RepositoryService,
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    @LocalServerPort
    private var port: Int = 0

    companion object {
        private val gitBaseDirHolder = Files.createTempDirectory("git-smart-http-it-").toFile()

        @JvmStatic
        @DynamicPropertySource
        fun overrideGitBaseDir(registry: DynamicPropertyRegistry) {
            registry.add("yona.git.base-dir") { gitBaseDirHolder.absolutePath }
        }
    }

    init {
        describe("실제 git clone 커맨드로 검증하는 스마트 HTTP 프로토콜 (TASK-0416)") {
            it("PUBLIC 프로젝트를 실제 git clone 바이너리로 clone하면 성공하고 커밋된 파일이 있어야 한다") {
                val owner = userRepository.findByLoginId("git-proto-owner").orElseGet {
                    userRepository.save(User(loginId = "git-proto-owner", name = "깃프로토콜오너", email = "git-proto-owner@yona.io"))
                }
                val project = projectRepository.findAll().find { it.name == "git-proto-proj" && it.owner == owner.loginId }
                    ?: projectRepository.save(
                        Project(name = "git-proto-proj", owner = owner.loginId, projectScope = ProjectScope.PUBLIC, vcs = "GIT")
                    )

                val gitDir = File(gitBaseDirHolder, "${project.owner}/${project.name}.git")
                if (!gitDir.exists()) {
                    repositoryService.getRepository(project).create()
                    BareCommit(project, owner, gitBaseDirHolder.absolutePath).commitTextFile(
                        "README.md", "# git-proto-proj", "초기 커밋"
                    )
                }

                val cloneUrl = "http://127.0.0.1:$port/git/${project.owner}/${project.name}.git"
                val cloneDest = Files.createTempDirectory("git-smart-http-clone-").toFile()

                try {
                    val process = ProcessBuilder("git", "clone", cloneUrl, cloneDest.absolutePath)
                        .redirectErrorStream(true)
                        .start()
                    val output = process.inputStream.bufferedReader().readText()
                    val exitCode = process.waitFor()

                    withClue(output) { exitCode shouldBe 0 }
                    File(cloneDest, "README.md").exists() shouldBe true
                } finally {
                    cloneDest.deleteRecursively()
                }
            }
        }
    }
}
