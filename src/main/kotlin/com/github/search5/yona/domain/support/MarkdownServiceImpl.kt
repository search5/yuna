package com.github.search5.yona.domain.support

import com.github.search5.yona.domain.project.Project
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.autolink.AutolinkExtension
import org.springframework.stereotype.Service

@Service
class MarkdownServiceImpl(
    private val autoLinkRenderer: AutoLinkRenderer
) : MarkdownService {

    override fun render(body: String): String {
        return render(body, true, null)
    }

    override fun render(body: String, breaks: Boolean): String {
        return render(body, breaks, null)
    }

    override fun render(body: String, breaks: Boolean, project: Project?): String {
        if (body.isEmpty()) {
            return ""
        }
        val extensions = listOf(
            TablesExtension.create(),
            StrikethroughExtension.create(),
            AutolinkExtension.create()
        )
        val parser = Parser.builder().extensions(extensions).build()
        val document = parser.parse(body)
        val renderer = if (breaks) {
            HtmlRenderer.builder().softbreak("<br />\n").extensions(extensions).build()
        } else {
            HtmlRenderer.builder().extensions(extensions).build()
        }
        val html = renderer.render(document)
        val sanitized = sanitize(html)
        return autoLinkRenderer.render(sanitized, project)
    }

    private fun sanitize(html: String): String {
        // 간단한 XSS 방지 처리
        return html.replace(Regex("(?i)<script.*?>.*?</script.*?>"), "")
            .replace(Regex("(?i)javascript:"), "#")
            .replace(Regex("(?i)onload\\s*="), "data-onload=")
            .replace(Regex("(?i)onerror\\s*="), "data-onerror=")
    }
}
