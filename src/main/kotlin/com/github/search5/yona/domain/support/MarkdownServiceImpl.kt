package com.github.search5.yona.domain.support

import com.github.search5.yona.domain.project.Project
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.autolink.AutolinkExtension
import org.owasp.html.HtmlPolicyBuilder
import org.owasp.html.PolicyFactory
import org.owasp.html.Sanitizers
import org.springframework.stereotype.Service

@Service
class MarkdownServiceImpl(
    private val autoLinkRenderer: AutoLinkRenderer
) : MarkdownService {

    companion object {
        // yona의 utils/Markdown.java 새니타이저 정책과 동등한 allowlist.
        // 이벤트 핸들러(onclick/onload/onerror 등)와 <script>/<svg> 등은
        // 명시적으로 허용하지 않는 한 OWASP 정책상 자동으로 제거된다.
        private val SANITIZER_POLICY: PolicyFactory = Sanitizers.FORMATTING
            .and(Sanitizers.IMAGES)
            .and(Sanitizers.STYLES)
            .and(Sanitizers.TABLES)
            .and(Sanitizers.BLOCKS)
            .and(
                HtmlPolicyBuilder()
                    .allowUrlProtocols("http", "https", "mailto")
                    .allowElements("video", "source", "a", "input", "pre", "br", "hr", "iframe", "ol", "span")
                    .allowAttributes("href", "name", "target").onElements("a")
                    .allowAttributes("src", "type", "target").onElements("source")
                    .allowAttributes(
                        "data-setup", "controls", "preload", "type", "autoplay",
                        "responsive", "height", "width", "fluid", "liveui", "src"
                    ).onElements("video")
                    .allowAttributes("type", "disabled", "checked").onElements("input")
                    .allowAttributes("start").onElements("ol")
                    .allowAttributes("width", "height", "src", "frameborder", "allow", "allowfullscreen")
                    .onElements("iframe")
                    .allowAttributes("class", "id", "style", "width", "height").globally()
                    .toFactory()
            )
    }

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
        return SANITIZER_POLICY.sanitize(html)
    }
}
