package com.github.search5.yona.web

import jakarta.servlet.ServletConfig
import jakarta.servlet.ServletContext
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.tmatesoft.svn.core.internal.server.dav.DAVServlet
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@RestController
class SvnController(
    @Value("\${yuna.svn.base-dir:/tmp/yuna/svn}")
    private val baseDir: String,
    private val servletContext: ServletContext
) {

    private val davServletCache = ConcurrentHashMap<String, DAVServlet>()

    @RequestMapping("/svn/{ownerName}/{projectName}/**")
    fun service(
        request: HttpServletRequest,
        response: HttpServletResponse
    ) {
        val uri = request.requestURI
        val segments = uri.split("/").filter { it.isNotEmpty() }
        if (segments.size < 3) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN)
            return
        }

        val ownerName = segments[1]

        val davServlet = davServletCache.computeIfAbsent(ownerName) { owner ->
            val servlet = DAVServlet()
            servlet.init(object : ServletConfig {
                override fun getServletName(): String = "DAVServlet-$owner"
                override fun getServletContext(): ServletContext = this@SvnController.servletContext
                
                override fun getInitParameter(name: String): String? {
                    return if (name == "SVNParentPath") {
                        val parentPath = File(baseDir, owner)
                        if (!parentPath.exists()) {
                            parentPath.mkdirs()
                        }
                        parentPath.absolutePath
                    } else {
                        null
                    }
                }

                override fun getInitParameterNames(): Enumeration<String> {
                    return Collections.enumeration(listOf("SVNParentPath"))
                }
            })
            servlet
        }

        val wrappedRequest = SvnServletRequestWrapper(request, ownerName)
        davServlet.service(wrappedRequest, response)
    }
}
