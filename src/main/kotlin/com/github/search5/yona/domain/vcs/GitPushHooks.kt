package com.github.search5.yona.domain.vcs

import com.github.search5.yona.domain.event.GitPostReceiveEvent
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.pullrequest.PullRequestRepository
import com.github.search5.yona.domain.user.User
import org.eclipse.jgit.transport.PostReceiveHook
import org.eclipse.jgit.transport.PreReceiveHook
import org.eclipse.jgit.transport.ReceiveCommand
import org.eclipse.jgit.transport.ReceivePack
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant

private const val RESERVED_REF = "refs/yobi"
private const val RESERVED_REF_PREFIX = "refs/yobi/"
private const val BRANCH_PREFIX = "refs/heads/"

/**
 * yona의 playRepository/hooks/RejectPushToReservedRefs.java 대응.
 * refs/yobi 하위 ref는 내부적으로 PR 병합 상태 추적 등에 쓰이는 예약 ref이므로
 * 클라이언트가 직접 push하지 못하도록 막는다.
 */
class RejectPushToReservedRefsPreReceiveHook : PreReceiveHook {
    override fun onPreReceive(rp: ReceivePack, commands: Collection<ReceiveCommand>) {
        for (command in commands) {
            val refName = command.refName
            if (refName == RESERVED_REF || refName.startsWith(RESERVED_REF_PREFIX)) {
                command.setResult(
                    ReceiveCommand.Result.REJECTED_OTHER_REASON,
                    "refs/yobi/* is reserved for internal use"
                )
            }
        }
    }
}

/**
 * yona의 UpdateLastPushedDate / NotifyPushedCommits / PullRequestCheck(브랜치 삭제 부분)를
 * 하나의 PostReceiveHook으로 통합 이식한 것.
 */
class YunaPostReceiveHook(
    private val project: Project,
    private val pusher: User,
    private val projectRepository: ProjectRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val eventPublisher: ApplicationEventPublisher
) : PostReceiveHook {

    override fun onPostReceive(rp: ReceivePack, commands: Collection<ReceiveCommand>) {
        updateLastPushedDate()
        notifyPushedCommits(commands)
        cleanupPullRequestsForDeletedBranches(commands)
    }

    private fun updateLastPushedDate() {
        project.lastPushedDate = Instant.now()
        projectRepository.save(project)
    }

    private fun notifyPushedCommits(commands: Collection<ReceiveCommand>) {
        eventPublisher.publishEvent(GitPostReceiveEvent(project, pusher, commands.toList()))
    }

    private fun cleanupPullRequestsForDeletedBranches(commands: Collection<ReceiveCommand>) {
        commands
            .filter { it.type == ReceiveCommand.Type.DELETE && it.refName.startsWith(BRANCH_PREFIX) }
            .map { it.refName.removePrefix(BRANCH_PREFIX) }
            .forEach { branch ->
                val related = pullRequestRepository.findRelatedPullRequests(project, branch)
                if (related.isNotEmpty()) {
                    pullRequestRepository.deleteAll(related)
                }
            }
    }
}
