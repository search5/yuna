package com.github.search5.yona.domain.project

import com.github.search5.yona.AbstractIntegrationTest
import com.github.search5.yona.domain.organization.Organization
import com.github.search5.yona.domain.organization.OrganizationRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

import org.springframework.transaction.annotation.Transactional

@Transactional
class ProjectRepositorySpec @Autowired constructor(
    private val projectRepository: ProjectRepository,
    private val organizationRepository: OrganizationRepository
) : AbstractIntegrationTest() {

    init {
        describe("ProjectRepository") {
            beforeEach {
                projectRepository.deleteAll()
                organizationRepository.deleteAll()
            }

            it("프로젝트를 정상적으로 저장하고 조회할 수 있어야 한다") {
                // Given
                val organization = organizationRepository.save(
                    Organization(name = "test-org", created = Instant.now(), descr = "테스트 조직")
                )

                val project = Project(
                    name = "test-project",
                    owner = "test-user",
                    overview = "테스트 프로젝트입니다.",
                    createdDate = Instant.now(),
                    organization = organization,
                    projectScope = ProjectScope.PUBLIC
                )

                // When
                val savedProject = projectRepository.save(project)

                // Then
                savedProject.id shouldNotBe null
                
                val foundProject = projectRepository.findByOwnerAndName("test-user", "test-project").orElse(null)
                foundProject shouldNotBe null
                foundProject.name shouldBe "test-project"
                foundProject.organization?.name shouldBe "test-org"
                foundProject.projectScope shouldBe ProjectScope.PUBLIC
            }

            it("특정 소유자의 모든 프로젝트를 조회할 수 있어야 한다") {
                // Given
                projectRepository.save(
                    Project(name = "project-1", owner = "test-user", createdDate = Instant.now())
                )
                projectRepository.save(
                    Project(name = "project-2", owner = "test-user", createdDate = Instant.now())
                )
                projectRepository.save(
                    Project(name = "project-3", owner = "other-user", createdDate = Instant.now())
                )

                // When
                val projects = projectRepository.findByOwner("test-user")

                // Then
                projects.size shouldBe 2
                projects.map { it.name } shouldBe listOf("project-1", "project-2")
            }
        }
    }
}
