package com.github.search5.yona.domain.support

import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.vcs.RepositoryService
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
    private val autoLinkRenderer: AutoLinkRenderer,
    private val repositoryService: RepositoryService
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

        // yona Markdown.java:363/372 imageLink / :373 normalLocalLink 대응 (P1-139).
        // "!\[text](./path)" (이미지) / "[text](./path)" (일반 링크) 형태의 상대경로 링크만 매칭한다
        // (http:/https:/ftp:/file: 스킴이거나 절대경로는 건드리지 않음 — 원본과 동일).
        private val IMAGE_LINK_PATTERN =
            Regex("""!\[(?<text>[^]]*)]\(/?(?!https:|http:|ftp:|file:)\.\/(?<link>[^)]*)\)""")
        private val NORMAL_LOCAL_LINK_PATTERN =
            Regex("""(?<space>[^!])\[(?<text>[^]]*)]\(/?(?!https:|http:|ftp:|file:)\.\/(?<link>[^)]*)\)""")
    }

    override fun render(body: String): String {
        return render(body, true, null)
    }

    override fun render(body: String, breaks: Boolean): String {
        return render(body, breaks, null)
    }

    override fun render(body: String, breaks: Boolean, project: Project?): String {
        return render(body, breaks, project, null)
    }

    override fun render(body: String, breaks: Boolean, project: Project?, lang: String?): String {
        if (body.isEmpty()) {
            return ""
        }
        val sanitized = renderWithHighlight(body, breaks)
        return autoLinkRenderer.render(sanitized, project, lang)
    }

    // yona utils/Markdown.java:218-270 renderWithHighlight() 대응 (P2-43). 사용자 지시로 원본의
    // 캐시 로직을 구조 그대로 포팅했다 — source.hashCode()만 캐시 키로 쓰고 breaks는 키에 포함되지
    // 않으며(동일 source를 breaks만 바꿔 렌더링하면 캐시가 이전 breaks 결과를 돌려줄 수 있음, 원본과
    // 동일한 특성), 캐시 히트 시에도 전체 파이프라인을 다시 계산해 캐시는 갱신하되 반환값은 히트
    // 당시의 예전 캐시 값을 그대로 쓴다(원본 그대로 — 원본에도 TTL이 없어 이 재계산이 사실상
    // 무의미해 보이지만 임의로 "고치지" 않고 구조 그대로 옮겼다).
    private fun renderWithHighlight(source: String, breaks: Boolean): String {
        val sourceHashCode = source.hashCode()
        val cached = MarkdownRenderCache.renderedMarkdown.getIfPresent(sourceHashCode)
        if (cached != null) {
            val sanitized = renderCore(source, breaks)
            MarkdownRenderCache.renderedMarkdown.put(sourceHashCode, ZipUtil.compress(sanitized))
            return ZipUtil.decompress(cached)
        }
        val sanitized = renderCore(source, breaks)
        MarkdownRenderCache.renderedMarkdown.put(sourceHashCode, ZipUtil.compress(sanitized))
        return sanitized
    }

    private fun renderCore(body: String, breaks: Boolean): String {
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
        return sanitize(html)
    }

override fun renderFileInCodeBrowser(source: String, project: Project): String {
        val defaultBranch = getDefaultBranch(project)
        val imageLinkFiltered = replaceImageLinkPath(project, source, defaultBranch)
        return render(imageLinkFiltered, true, project)
    }

    override fun renderFileInReadme(source: String, project: Project): String {
        val defaultBranch = getDefaultBranch(project)
        val relativeLinksToCodeBrowserPath = replaceContentsLinkToCodeBrowserPath(project, source, defaultBranch)
        return render(relativeLinksToCodeBrowserPath, true, project)
    }

    private fun getDefaultBranch(project: Project): String {
        return try {
            repositoryService.getRepository(project).getDefaultBranch().removePrefix("refs/heads/")
        } catch (e: Exception) {
            "master"
        }
    }

    // yona Markdown.java:358-365 replaceImageLinkPath() 대응.
    private fun replaceImageLinkPath(project: Project, text: String, defaultBranch: String): String {
        return IMAGE_LINK_PATTERN.replace(text) { m ->
            "![${m.groups["text"]!!.value}](/${project.owner}/${project.name}/files/$defaultBranch/${m.groups["link"]!!.value})"
        }
    }

    // yona Markdown.java:367-377 replaceContentsLinkToCodeBrowerPath() 대응.
    private fun replaceContentsLinkToCodeBrowserPath(project: Project, text: String, defaultBranch: String): String {
        val imageFiltered = replaceImageLinkPath(project, text, defaultBranch)
        return NORMAL_LOCAL_LINK_PATTERN.replace(imageFiltered) { m ->
            "${m.groups["space"]!!.value}[${m.groups["text"]!!.value}](/${project.owner}/${project.name}/code/$defaultBranch/${m.groups["link"]!!.value})"
        }
    }

    private fun sanitize(html: String): String {
        return SANITIZER_POLICY.sanitize(html)
    }
}
