package com.github.search5.yona.domain.vcs

import com.github.search5.yona.domain.event.GitPostReceiveEvent
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.pullrequest.PullRequest
import com.github.search5.yona.domain.pullrequest.PullRequestRepository
import com.github.search5.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.transport.ReceiveCommand
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional

class GitPushHooksSpec : DescribeSpec({

    val zero = ObjectId.zeroId()
    val sha1 = ObjectId.fromString("1".repeat(40))
    val sha2 = ObjectId.fromString("2".repeat(40))

    describe("RejectPushToReservedRefsPreReceiveHook") {
        val hook = RejectPushToReservedRefsPreReceiveHook()

        it("refs/yobi/* 로의 push는 거부되어야 한다") {
            val command = ReceiveCommand(sha1, sha2, "refs/yobi/merge/1")

            hook.onPreReceive(mockk(relaxed = true), listOf(command))

            command.result shouldBe ReceiveCommand.Result.REJECTED_OTHER_REASON
        }

        it("refs/yobi 자체(하위 경로 없이)도 거부되어야 한다") {
            val command = ReceiveCommand(sha1, sha2, "refs/yobi")

            hook.onPreReceive(mockk(relaxed = true), listOf(command))

            command.result shouldBe ReceiveCommand.Result.REJECTED_OTHER_REASON
        }

        it("일반 브랜치(refs/heads/main)로의 push는 거부되지 않아야 한다") {
            val command = ReceiveCommand(zero, sha1, "refs/heads/main")

            hook.onPreReceive(mockk(relaxed = true), listOf(command))

            command.result shouldBe ReceiveCommand.Result.NOT_ATTEMPTED
        }
    }

    describe("YunaPostReceiveHook") {
        val projectRepository = mockk<ProjectRepository>()
        val pullRequestRepository = mockk<PullRequestRepository>()
        val pushedBranchRepository = mockk<PushedBranchRepository>()
        val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
        val project = Project(id = 1L, name = "yona-project", owner = "gildong")
        val pusher = User(id = 9L, loginId = "gildong", name = "길동")

        fun newHook() = YunaPostReceiveHook(
            project, pusher, projectRepository, pullRequestRepository, pushedBranchRepository, eventPublisher
        )

        beforeTest {
            clearMocks(projectRepository, pullRequestRepository, pushedBranchRepository, eventPublisher)
            every { projectRepository.save(any()) } returns project
            every { pushedBranchRepository.findByProjectAndPushedDateBefore(any(), any()) } returns emptyList()
            every { pushedBranchRepository.findByProjectAndName(any(), any()) } returns Optional.empty()
            every { pullRequestRepository.existsByFromProjectAndFromBranch(any(), any()) } returns false
            every { pullRequestRepository.findRelatedPullRequests(any(), any()) } returns emptyList()
            every { pushedBranchRepository.save(any()) } answers { it.invocation.args[0] as PushedBranch }
            every { pushedBranchRepository.deleteAll(any<List<PushedBranch>>()) } returns Unit
            every { pushedBranchRepository.delete(any()) } returns Unit
        }

        it("push가 일어나면 project.lastPushedDate가 갱신되어야 한다") {
            val command = ReceiveCommand(zero, sha1, "refs/heads/main")

            newHook().onPostReceive(mockk(relaxed = true), listOf(command))

            project.lastPushedDate shouldBe project.lastPushedDate
            verify { projectRepository.save(project) }
        }

        it("push가 일어나면 GitPostReceiveEvent가 발행되어야 한다 (커밋 알림 트리거)") {
            val command = ReceiveCommand(zero, sha1, "refs/heads/main")
            val captured = slot<GitPostReceiveEvent>()
            every { eventPublisher.publishEvent(capture(captured)) } returns Unit

            newHook().onPostReceive(mockk(relaxed = true), listOf(command))

            captured.captured.project shouldBe project
            captured.captured.user shouldBe pusher
            captured.captured.commands shouldBe listOf(command)
        }

        it("브랜치가 삭제되면 해당 브랜치와 연관된 열린 PullRequest가 삭제되어야 한다") {
            val deleteCommand = ReceiveCommand(sha1, zero, "refs/heads/feature/x")
            val relatedPr = mockk<PullRequest>(relaxed = true)
            every { pullRequestRepository.findRelatedPullRequests(project, "feature/x") } returns listOf(relatedPr)
            every { pullRequestRepository.deleteAll(any<List<PullRequest>>()) } returns Unit

            newHook().onPostReceive(mockk(relaxed = true), listOf(deleteCommand))

            verify { pullRequestRepository.deleteAll(listOf(relatedPr)) }
        }

        it("태그 삭제(refs/tags/*)는 PullRequest 정리 대상이 아니어야 한다") {
            val deleteTagCommand = ReceiveCommand(sha1, zero, "refs/tags/v1.0.0")

            newHook().onPostReceive(mockk(relaxed = true), listOf(deleteTagCommand))

            verify(exactly = 0) { pullRequestRepository.findRelatedPullRequests(any(), any()) }
        }

        it("브랜치 생성/갱신(삭제가 아님)은 PullRequest 정리를 시도하지 않아야 한다") {
            val updateCommand = ReceiveCommand(sha1, sha2, "refs/heads/main")

            newHook().onPostReceive(mockk(relaxed = true), listOf(updateCommand))

            verify(exactly = 0) { pullRequestRepository.findRelatedPullRequests(any(), any()) }
        }

        describe("최근 push된 브랜치 추적 (P1-24, yona UpdateRecentlyPushedBranch 대응)") {
            it("새 브랜치가 push되면 PushedBranch가 새로 저장되어야 한다") {
                val command = ReceiveCommand(zero, sha1, "refs/heads/feature/x")
                val saved = slot<PushedBranch>()
                every { pushedBranchRepository.save(capture(saved)) } answers { saved.captured }

                newHook().onPostReceive(mockk(relaxed = true), listOf(command))

                saved.captured.name shouldBe "feature/x"
                saved.captured.project shouldBe project
            }

            it("이미 기록된 브랜치를 다시 push하면 새로 만들지 않고 pushedDate만 갱신해야 한다") {
                val command = ReceiveCommand(sha1, sha2, "refs/heads/main")
                val existing = PushedBranch(id = 5L, name = "main", project = project)
                every { pushedBranchRepository.findByProjectAndName(project, "main") } returns Optional.of(existing)

                newHook().onPostReceive(mockk(relaxed = true), listOf(command))

                verify { pushedBranchRepository.save(existing) }
            }

            it("이 브랜치를 fromBranch로 하는 PullRequest가 이미 있으면 PushedBranch를 새로 만들지 않아야 한다") {
                val command = ReceiveCommand(zero, sha1, "refs/heads/pr-branch")
                every { pullRequestRepository.existsByFromProjectAndFromBranch(project, "pr-branch") } returns true

                newHook().onPostReceive(mockk(relaxed = true), listOf(command))

                verify(exactly = 0) { pushedBranchRepository.save(any()) }
            }

            it("태그 push(refs/tags/*)는 추적하지 않아야 한다") {
                val command = ReceiveCommand(zero, sha1, "refs/tags/v1.0.0")

                newHook().onPostReceive(mockk(relaxed = true), listOf(command))

                verify(exactly = 0) { pushedBranchRepository.save(any()) }
            }

            it("push마다 1시간 이상 지난 오래된 PushedBranch를 정리해야 한다") {
                val command = ReceiveCommand(zero, sha1, "refs/heads/main")
                val old = PushedBranch(id = 1L, name = "old-branch", project = project)
                every { pushedBranchRepository.findByProjectAndPushedDateBefore(project, any()) } returns listOf(old)

                newHook().onPostReceive(mockk(relaxed = true), listOf(command))

                verify { pushedBranchRepository.deleteAll(listOf(old)) }
            }

            it("브랜치가 삭제되면 해당 PushedBranch 레코드도 함께 삭제되어야 한다") {
                val command = ReceiveCommand(sha1, zero, "refs/heads/feature/y")
                val existing = PushedBranch(id = 7L, name = "feature/y", project = project)
                every { pushedBranchRepository.findByProjectAndName(project, "feature/y") } returns Optional.of(existing)

                newHook().onPostReceive(mockk(relaxed = true), listOf(command))

                verify { pushedBranchRepository.delete(existing) }
            }
        }
    }
})
