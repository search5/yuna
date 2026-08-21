package com.github.search5.yona.domain.support

import com.github.search5.yona.domain.project.Project

interface MarkdownService {
    fun render(body: String): String
    fun render(body: String, breaks: Boolean): String
    fun render(body: String, breaks: Boolean, project: Project?): String

    // yona Markdown.java:346-356 renderFileInCodeBrowser()/renderFileInReadme() 대응 (P1-139).
    // 렌더링 파이프라인은 render()와 동일하되, 렌더링 전에 소스 안의 상대경로 링크(`./path` 형태)를
    // 프로젝트 기본 브랜치 기준 절대경로(코드브라우저/파일 다운로드 경로)로 먼저 치환한다.
    fun renderFileInCodeBrowser(source: String, project: Project): String
    fun renderFileInReadme(source: String, project: Project): String
}
