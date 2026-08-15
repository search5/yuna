package com.github.search5.yona.web

import com.github.search5.yona.domain.pullrequest.CommentThread.ThreadState
import com.github.search5.yona.domain.pullrequest.CodeReviewService
import com.github.search5.yona.domain.user.UserRepository
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseBody

@Controller
class CommentThreadController(
    private val codeReviewService: CodeReviewService,
    private val userRepository: UserRepository
) {

    @PostMapping("/threads/{id}/open")
    @ResponseBody
    fun open(
        @PathVariable id: Long,
        authentication: Authentication?
    ): ResponseEntity<Unit> {
        val user = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return ResponseEntity.status(401).build()

        codeReviewService.updateThreadState(id, ThreadState.OPEN, user)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/threads/{id}/close")
    @ResponseBody
    fun close(
        @PathVariable id: Long,
        authentication: Authentication?
    ): ResponseEntity<Unit> {
        val user = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return ResponseEntity.status(401).build()

        codeReviewService.updateThreadState(id, ThreadState.CLOSED, user)
        return ResponseEntity.ok().build()
    }
}

