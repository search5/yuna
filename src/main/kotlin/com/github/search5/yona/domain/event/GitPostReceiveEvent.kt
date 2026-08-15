package com.github.search5.yona.domain.event

import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.user.User
import org.eclipse.jgit.transport.ReceiveCommand

data class GitPostReceiveEvent(
    val project: Project,
    val user: User,
    val commands: List<ReceiveCommand>
)
