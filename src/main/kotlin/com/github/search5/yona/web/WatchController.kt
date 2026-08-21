package com.github.search5.yona.web

import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.watch.WatchService
import com.github.search5.yona.domain.notification.UserProjectNotification
import com.github.search5.yona.domain.notification.UserProjectNotificationRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.board.PostingRepository
import org.springframework.ui.Model

import com.github.search5.yona.domain.pullrequest.PullRequestRepository
import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.enumeration.Operation

@Controller
class WatchController(
    private val watchService: WatchService,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val userProjectNotificationRepository: UserProjectNotificationRepository,
    private val issueRepository: IssueRepository,
    private val postingRepository: PostingRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val accessControl: AccessControl
) {

    private fun getLoginUser(authentication: Authentication?): User {
        if (authentication == null) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.")
        }
        return userRepository.findByLoginId(authentication.name).orElseThrow {
            ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자 정보를 찾을 수 없습니다.")
        }
    }

    private fun checkWatchPermission(user: User, resourceType: ResourceType, resourceId: String) {
        val project = when (resourceType) {
            ResourceType.PROJECT -> {
                projectRepository.findById(resourceId.toLongOrNull() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 리소스 ID입니다."))
                    .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다.") }
            }
            ResourceType.ISSUE_POST -> {
                val issue = issueRepository.findById(resourceId.toLongOrNull() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 리소스 ID입니다."))
                    .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "이슈를 찾을 수 없습니다.") }
                issue.project
            }
            ResourceType.BOARD_POST -> {
                val posting = postingRepository.findById(resourceId.toLongOrNull() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 리소스 ID입니다."))
                    .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다.") }
                posting.project
            }
            ResourceType.PULL_REQUEST -> {
                val pullRequest = pullRequestRepository.findById(resourceId.toLongOrNull() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 리소스 ID입니다."))
                    .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Pull Request를 찾을 수 없습니다.") }
                pullRequest.toProject
            }
            else -> {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 리소스 타입입니다.")
            }
        }

        if (!accessControl.isAllowed(user, project, Operation.WATCH)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "권한이 없습니다.")
        }
    }


    @PostMapping("/watch")
    @ResponseBody
    fun watchResource(
        @RequestParam("resource.type") resourceTypeStr: String,
        @RequestParam("resource.id") resourceId: String,
        authentication: Authentication?
    ): ResponseEntity<Unit> {
        val user = getLoginUser(authentication)
        val resourceType = ResourceType.valueOf(resourceTypeStr)
        checkWatchPermission(user, resourceType, resourceId)
        watchService.watch(user, resourceType, resourceId)
        return ResponseEntity.ok().build()
    }

    @RequestMapping(value = ["/unwatch"], method = [RequestMethod.GET, RequestMethod.POST])
    fun unwatchResource(
        @RequestParam("resource.type") resourceTypeStr: String,
        @RequestParam("resource.id") resourceId: String,
        @RequestHeader(value = "Referer", required = false) referer: String?,
        authentication: Authentication?
    ): String {
        val user = getLoginUser(authentication)
        val resourceType = ResourceType.valueOf(resourceTypeStr)
        checkWatchPermission(user, resourceType, resourceId)
        watchService.unwatch(user, resourceType, resourceId)
        return "redirect:${referer ?: "/"}"
    }

    @PostMapping("/{owner}/{projectName}/watch")
    @ResponseBody
    fun watchProject(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?
    ): ResponseEntity<Unit> {
        val user = getLoginUser(authentication)
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다.")
        }
        if (!accessControl.isAllowed(user, project, Operation.WATCH)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "권한이 없습니다.")
        }
        watchService.watch(user, ResourceType.PROJECT, project.id.toString())
        return ResponseEntity.ok().build()
    }

    @PostMapping("/{owner}/{projectName}/unwatch")
    @ResponseBody
    @org.springframework.transaction.annotation.Transactional
    fun unwatchProject(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?
    ): ResponseEntity<Unit> {
        val user = getLoginUser(authentication)
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다.")
        }
        if (!accessControl.isAllowed(user, project, Operation.WATCH)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "권한이 없습니다.")
        }
        watchService.unwatch(user, ResourceType.PROJECT, project.id.toString())
        userProjectNotificationRepository.deleteByUserAndProject(user, project)
        return ResponseEntity.ok().build()
    }

    @RequestMapping(value = ["/watch/toggle/{projectId}/{notificationType}", "/noti/toggle/{projectId}/{notificationType}"], method = [RequestMethod.GET, RequestMethod.POST])
    @ResponseBody
    fun toggleProjectNotification(
        @PathVariable projectId: Long,
        @PathVariable notificationType: String,
        authentication: Authentication?
    ): ResponseEntity<Map<String, Any>> {
        val user = getLoginUser(authentication)
        val project = projectRepository.findById(projectId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다.")
        }

        if (!accessControl.isAllowed(user, project, Operation.WATCH)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "권한이 없습니다.")
        }

        if (!watchService.isWatching(user, ResourceType.PROJECT, projectId.toString())) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "프로젝트를 감시하고 있지 않습니다.")
        }

        val notiType = try {
            com.github.search5.yona.domain.enumeration.EventType.valueOf(notificationType)
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 알림 타입입니다.")
        }

        val isNotifiedByDefault = isNotifiedByDefault(notiType)
        val existing = userProjectNotificationRepository.findByUserAndProjectAndNotificationType(user, project, notiType)
        
        if (existing == null) {
            val newNotification = UserProjectNotification(
                user = user,
                project = project,
                notificationType = notiType,
                allowed = !isNotifiedByDefault
            )
            userProjectNotificationRepository.save(newNotification)
        } else {
            existing.toggle()
            if (existing.allowed == isNotifiedByDefault(notiType)) {
                userProjectNotificationRepository.delete(existing)
            } else {
                userProjectNotificationRepository.save(existing)
            }
        }

        return ResponseEntity.ok(mapOf("status" to "success"))
    }

    private fun isNotifiedByDefault(eventType: com.github.search5.yona.domain.enumeration.EventType): Boolean {
        return eventType != com.github.search5.yona.domain.enumeration.EventType.NEW_COMMENT
    }

    @GetMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/posts/{number}/watchers")
    @ResponseBody
    fun getWatchers(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        @RequestParam("type") type: String
    ): ResponseEntity<WatchersResponse> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다.")
        }

        val watchers = when (type.lowercase()) {
            "issues" -> {
                val issue = issueRepository.findByProjectAndNumber(project, number)
                    ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "이슈를 찾을 수 없습니다.")
                watchService.findWatchers(ResourceType.ISSUE_POST, issue.id.toString())
            }
            "posts" -> {
                val posting = postingRepository.findByProjectAndNumber(project, number)
                    ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다.")
                watchService.findWatchers(ResourceType.BOARD_POST, posting.id.toString())
            }
            else -> emptySet()
        }

        val watcherDtos = watchers.map {
            WatcherDto(name = it.name, url = "/user/${it.loginId}")
        }

        return ResponseEntity.ok(
            WatchersResponse(
                totalWatchers = watchers.size,
                watchersInList = watchers.size,
                watchers = watcherDtos
            )
        )
    }

    @GetMapping("/{owner}/{projectName}/watchers")
    fun watchers(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다.")
        }

        val watchers = watchService.findWatchers(ResourceType.PROJECT, project.id.toString())
        model.addAttribute("project", project)
        model.addAttribute("watchers", watchers)
        return "project/watchers"
    }

    data class WatchersResponse(
        val totalWatchers: Int,
        val watchersInList: Int,
        val watchers: List<WatcherDto>
    )

    data class WatcherDto(
        val name: String,
        val url: String
    )
}

