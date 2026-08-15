package com.github.search5.yona.domain.support

import com.github.search5.yona.domain.project.Project

interface MarkdownService {
    fun render(body: String): String
    fun render(body: String, breaks: Boolean): String
    fun render(body: String, breaks: Boolean, project: Project?): String
}
