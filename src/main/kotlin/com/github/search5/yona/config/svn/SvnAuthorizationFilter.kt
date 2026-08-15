package com.github.search5.yona.config.svn

import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.regex.Pattern

@Component
class SvnAuthorizationFilter(
    private val projectService: ProjectService
) : OncePerRequestFilter() {

    private val svnUriPattern = Pattern.compile("^/svn/([^/]+)/([^/]+?)(?:/.*)?$")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val uri = request.requestURI
        val matcher = svnUriPattern.matcher(uri)

        if (!matcher.matches()) {
            filterChain.doFilter(request, response)
            return
        }

        val owner = matcher.group(1)
        val projectName = matcher.group(2)

        val project = projectService.findByOwnerAndName(owner, projectName)
        if (project == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Project Not Found")
            return
        }

        val vcs = project.vcs?.lowercase()
        if (vcs != "subversion" && vcs != "svn") {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Not a Subversion project")
            return
        }

        val isWriteRequest = isWriteRequest(request)

        val requiresAuth = project.projectScope == com.github.search5.yona.domain.project.ProjectScope.PRIVATE 
                || project.isCodeAccessibleMemberOnly 
                || isWriteRequest

        if (requiresAuth) {
            val authentication = SecurityContextHolder.getContext().authentication
            if (authentication == null || !authentication.isAuthenticated || isAnonymous(authentication)) {
                response.setHeader("WWW-Authenticate", "Basic realm=\"SVN Repository\"")
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")
                return
            }

            val loginId = authentication.name
            if (!isMember(project, loginId)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden")
                return
            }
        }

        filterChain.doFilter(request, response)
    }

    private fun isWriteRequest(request: HttpServletRequest): Boolean {
        val method = request.method.uppercase()
        val readMethods = setOf("GET", "PROPFIND", "OPTIONS", "REPORT", "HEAD")
        return !readMethods.contains(method)
    }

    private fun isAnonymous(authentication: org.springframework.security.core.Authentication): Boolean {
        return authentication is org.springframework.security.authentication.AnonymousAuthenticationToken
    }

    private fun isMember(project: Project, loginId: String): Boolean {
        val projectId = project.id ?: return false
        return projectService.isMember(projectId, loginId)
    }
}
