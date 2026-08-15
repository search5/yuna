package com.github.search5.yona.web

import com.github.search5.yona.domain.enumeration.SearchType
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.organization.OrganizationRepository
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.support.SearchService
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException

@Controller
class SearchController(
    private val searchService: SearchService,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val organizationRepository: OrganizationRepository
) {

    @GetMapping("/search")
    fun searchInAll(
        @RequestParam(value = "keyword", required = false, defaultValue = "") keyword: String,
        @RequestParam(value = "searchType", required = false, defaultValue = "auto") searchTypeVal: String,
        @RequestParam(value = "pageNum", required = false, defaultValue = "1") pageNum: Int,
        authentication: Authentication?,
        model: Model
    ): String {
        if (keyword.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "검색어를 입력해주세요.")
        }

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        val searchType = SearchType.getValue(searchTypeVal)
        val pageable = PageRequest.of(pageNum - 1, 20)

        val searchResult = searchService.searchInAll(keyword, searchType, loginUser, pageable)

        model.addAttribute("keyword", keyword)
        model.addAttribute("searchResult", searchResult)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("org", null)
        model.addAttribute("project", null)

        return "search/list"
    }

    @GetMapping(value = ["/org/{organizationName}/search", "/organizations/{organizationName}/search"])
    fun searchInAGroup(
        @PathVariable organizationName: String,
        @RequestParam(value = "keyword", required = false, defaultValue = "") keyword: String,
        @RequestParam(value = "searchType", required = false, defaultValue = "auto") searchTypeVal: String,
        @RequestParam(value = "pageNum", required = false, defaultValue = "1") pageNum: Int,
        authentication: Authentication?,
        model: Model
    ): String {
        if (keyword.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "검색어를 입력해주세요.")
        }

        val organization = organizationRepository.findByName(organizationName).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "조직을 찾을 수 없습니다.")
        }

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        val searchType = SearchType.getValue(searchTypeVal)
        val pageable = PageRequest.of(pageNum - 1, 20)

        val searchResult = searchService.searchInAGroup(keyword, searchType, loginUser, organization, pageable)

        model.addAttribute("keyword", keyword)
        model.addAttribute("searchResult", searchResult)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("org", organization)
        model.addAttribute("project", null)

        return "search/list"
    }

    @GetMapping("/{owner}/{projectName}/search")
    fun searchInAProject(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(value = "keyword", required = false, defaultValue = "") keyword: String,
        @RequestParam(value = "searchType", required = false, defaultValue = "auto") searchTypeVal: String,
        @RequestParam(value = "pageNum", required = false, defaultValue = "1") pageNum: Int,
        authentication: Authentication?,
        model: Model
    ): String {
        if (keyword.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "검색어를 입력해주세요.")
        }

        val project = projectRepository.findByOwnerAndName(owner, projectName).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다.")
        }

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        val searchType = SearchType.getValue(searchTypeVal)
        val pageable = PageRequest.of(pageNum - 1, 20)

        val searchResult = searchService.searchInAProject(keyword, searchType, loginUser, project, pageable)

        model.addAttribute("keyword", keyword)
        model.addAttribute("searchResult", searchResult)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("org", null)
        model.addAttribute("project", project)

        return "search/list"
    }
}
