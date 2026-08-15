package com.github.search5.yona.domain.project

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import java.io.File

class GitServiceSpec : DescribeSpec({

    describe("GitService") {
        it("임시 디렉터리 내에 베어(Bare) 저장소를 정상적으로 생성할 수 있어야 한다") {
            // Given
            val tempGitBase = tempdir()
            val gitService = GitServiceImpl(tempGitBase.absolutePath)

            // When
            val repoFile = gitService.createRepository("test-owner", "test-project")

            // Then
            repoFile.exists() shouldBe true
            repoFile.name shouldBe "test-project.git"
            
            File(repoFile, "HEAD").exists() shouldBe true
            File(repoFile, "refs").exists() shouldBe true
        }

        it("저장소 삭제를 요청하면 디렉터리가 물리적으로 지워져야 한다") {
            // Given
            val tempGitBase = tempdir()
            val gitService = GitServiceImpl(tempGitBase.absolutePath)
            gitService.createRepository("test-owner", "test-project")

            // When
            val deleteResult = gitService.deleteRepository("test-owner", "test-project")

            // Then
            deleteResult shouldBe true
            gitService.getRepositoryPath("test-owner", "test-project").exists() shouldBe false
        }
    }
})
