package com.github.search5.yona.web

import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.issue.Assignee
import com.github.search5.yona.domain.issue.Issue
import com.github.search5.yona.domain.issue.IssueComment
import com.github.search5.yona.domain.issue.IssueLabel
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.pullrequest.PullRequest
import com.github.search5.yona.domain.pullrequest.PullRequestCommit
import com.github.search5.yona.domain.pullrequest.PullRequestMergeResult
import com.github.search5.yona.domain.pullrequest.ReviewComment
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.vcs.GitCommit
import java.time.Instant

// yona-wiki P3-02 Step8.7 2번(2026-09-01 실서버 골든패스 수동검증 중 발견, 심각도 높음) —
// IssueRestApiController/PullRequestApiController/SearchRestApiController가 JPA 엔티티
// (Issue/PullRequest/Project)를 가공 없이 그대로 반환하는데, User.projectUsers
// (@OneToMany mappedBy="user") <-> ProjectUser.user(@ManyToOne)가 양방향 연관관계라
// Jackson이 "이슈->project->projectUsers[]->user->projectUsers[]->user->..."로 무한
// 순환 직렬화한다(실측: curl로 60KB 넘는 깨진 채로 끊긴 JSON 확인, 서버가 open-in-view라
// 응답 작성 시점까지 세션이 열려있어 lazy 컬렉션이 실제로 초기화되며 재현됨).
//
// ProjectRestApiController.toProjectNode()가 이미 쓰고 있는 "엔티티를 그대로 반환하지 않고
// 필요한 필드만 담은 응답 모델로 변환" 패턴을 Issue/PullRequest에도 그대로 적용한다. 필드
// 이름은 yona-cli(internal/api/issue.go, pr.go, cmd/issue.go, cmd/pr.go)가 실제로 읽는
// 키(number/title/state/body/fromBranch/toBranch/fromProject.owner/fromProject.name 등)와
// 정확히 같은 camelCase를 유지한다 — CLI는 map[string]interface{}로 느슨하게 파싱하므로
// 서버가 이 키들을 계속 내려주기만 하면 CLI 코드 변경이 필요 없다.
//
// User를 이슈/PR 응답에 중첩해야 하는 지점(작성자/담당자/리뷰어/받는사람)에는 항상 이 최소
// 참조 DTO만 쓴다 — User 엔티티를 통째로 넣으면 projectUsers 등 백레퍼런스 컬렉션이 다시
// 따라와 같은 순환 문제가 재발한다.
data class UserRefResponse(
    val id: Long?,
    val loginId: String,
    val name: String
)

fun User.toRefResponse() = UserRefResponse(id = id, loginId = loginId, name = name)

data class AssigneeResponse(
    val id: Long?,
    val userId: Long?,
    val loginId: String?,
    val name: String?
)

fun Assignee.toResponse() = AssigneeResponse(id = id, userId = user.id, loginId = user.loginId, name = user.name)

data class IssueLabelResponse(
    val id: Long?,
    val name: String,
    val color: String,
    val categoryId: Long?,
    val category: String?
)

fun IssueLabel.toResponse() = IssueLabelResponse(
    id = id,
    name = name,
    color = color,
    categoryId = category.id,
    category = category.name
)

// Issue/PullRequest 응답 안에 toProject/fromProject를 중첩할 때 쓰는 최소 프로젝트 참조 DTO.
// yona-cli cmd/pr.go의 planCheckout()("yona pr checkout")이 pr["fromProject"]["owner"]/
// ["name"]을 그대로 읽으므로 owner/name은 반드시 유지해야 한다.
data class ProjectRefResponse(
    val id: Long?,
    val owner: String?,
    val name: String,
    val overview: String?,
    val vcs: String?,
    val scope: String
)

fun Project.toRefResponse() = ProjectRefResponse(
    id = id,
    owner = owner,
    name = name,
    overview = overview,
    vcs = vcs,
    scope = projectScope.name
)

data class IssueResponse(
    val id: Long?,
    val number: Long?,
    val title: String,
    val body: String?,
    val state: State,
    val createdDate: Instant?,
    val updatedDate: Instant?,
    val authorId: Long?,
    val authorLoginId: String?,
    val authorName: String?,
    val updatedByAuthorId: Long?,
    val updatedByAuthorLoginId: String?,
    val updatedByAuthorName: String?,
    val numOfComments: Int,
    val dueDate: Instant?,
    val isDraft: Boolean,
    val weight: Int,
    val milestoneId: Long?,
    val assignee: AssigneeResponse?,
    val labels: List<IssueLabelResponse>,
    val projectId: Long?
)

fun Issue.toResponse() = IssueResponse(
    id = id,
    number = number,
    title = title,
    body = body,
    state = state,
    createdDate = createdDate,
    updatedDate = updatedDate,
    authorId = authorId,
    authorLoginId = authorLoginId,
    authorName = authorName,
    updatedByAuthorId = updatedByAuthorId,
    updatedByAuthorLoginId = updatedByAuthorLoginId,
    updatedByAuthorName = updatedByAuthorName,
    numOfComments = numOfComments,
    dueDate = dueDate,
    isDraft = isDraft,
    weight = weight,
    milestoneId = milestone?.id,
    assignee = assignee?.toResponse(),
    labels = labels.map { it.toResponse() },
    projectId = project.id
)

data class IssueCommentResponse(
    val id: Long?,
    val contents: String,
    val createdDate: Instant?,
    val authorId: Long?,
    val authorLoginId: String?,
    val authorName: String?,
    val parentCommentId: Long?,
    val issueId: Long?
)

fun IssueComment.toResponse() = IssueCommentResponse(
    id = id,
    contents = contents,
    createdDate = createdDate,
    authorId = authorId,
    authorLoginId = authorLoginId,
    authorName = authorName,
    parentCommentId = parentComment?.id,
    issueId = issue.id
)

data class PullRequestResponse(
    val id: Long?,
    val number: Long?,
    val title: String,
    val body: String?,
    val state: State,
    val toProject: ProjectRefResponse,
    val fromProject: ProjectRefResponse,
    val toBranch: String,
    val fromBranch: String,
    val contributor: UserRefResponse,
    val receiver: UserRefResponse?,
    val created: Instant?,
    val updated: Instant?,
    val received: Instant?,
    val isConflict: Boolean?,
    val isMerging: Boolean?,
    val lastCommitId: String?,
    val assignee: AssigneeResponse?,
    val labels: List<IssueLabelResponse>,
    val reviewers: List<UserRefResponse>
)

fun PullRequest.toResponse() = PullRequestResponse(
    id = id,
    number = number,
    title = title,
    body = body,
    state = state,
    toProject = toProject.toRefResponse(),
    fromProject = fromProject.toRefResponse(),
    toBranch = toBranch,
    fromBranch = fromBranch,
    contributor = contributor.toRefResponse(),
    receiver = receiver?.toRefResponse(),
    created = created,
    updated = updated,
    received = received,
    isConflict = isConflict,
    isMerging = isMerging,
    lastCommitId = lastCommitId,
    assignee = assignee?.toResponse(),
    labels = labels.map { it.toResponse() },
    reviewers = reviewers.map { it.toRefResponse() }
)

data class ReviewCommentResponse(
    val id: Long?,
    val contents: String,
    val createdDate: Instant,
    val authorId: Long?,
    val authorLoginId: String?,
    val authorName: String?,
    val threadId: Long?
)

fun ReviewComment.toResponse() = ReviewCommentResponse(
    id = id,
    contents = contents,
    createdDate = createdDate,
    authorId = author?.id,
    authorLoginId = author?.loginId,
    authorName = author?.name,
    threadId = thread?.id
)

data class GitCommitResponse(
    val id: String,
    val shortId: String,
    val message: String?,
    val authorName: String?,
    val authorEmail: String?
)

fun GitCommit.toResponse() = GitCommitResponse(
    id = getId(),
    shortId = getShortId(),
    message = getMessage(),
    authorName = getAuthorName(),
    authorEmail = getAuthorEmail()
)

data class PullRequestCommitResponse(
    val id: Long?,
    val commitId: String,
    val commitShortId: String,
    val commitMessage: String,
    val authorEmail: String?,
    val authorDate: Instant?,
    val created: Instant?
)

fun PullRequestCommit.toResponse() = PullRequestCommitResponse(
    id = id,
    commitId = commitId,
    commitShortId = commitShortId,
    commitMessage = commitMessage,
    authorEmail = authorEmail,
    authorDate = authorDate,
    created = created
)

// PullRequestMergeResult.conflicts()는 Kotlin에서 "get"/"is" 접두사가 없는 일반 메서드라
// Jackson 빈 컨벤션상 프로퍼티로 노출되지 않는다 — 그대로 반환하면 CLI의
// cmd/pr.go newPRMergeCmd()가 항상 result["conflicts"]를 못 찾아(타입 단언 실패) 충돌 여부를
// 절대 감지하지 못하는 별도의 잠재 버그가 있었다(순환 직렬화 문제와 별개). DTO로 옮기면서
// "conflicts" 필드를 명시적으로 채워 이 문제도 함께 해소한다.
data class PullRequestMergeResultResponse(
    val conflicts: Boolean,
    val pullRequest: PullRequestResponse,
    val gitCommits: List<GitCommitResponse>,
    val newCommits: List<PullRequestCommitResponse>
)

fun PullRequestMergeResult.toResponse() = PullRequestMergeResultResponse(
    conflicts = conflicts(),
    pullRequest = pullRequest.toResponse(),
    gitCommits = gitCommits.map { it.toResponse() },
    newCommits = newCommits.map { it.toResponse() }
)
