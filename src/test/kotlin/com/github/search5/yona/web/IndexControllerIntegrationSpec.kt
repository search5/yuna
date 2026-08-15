package com.github.search5.yona.web

import com.github.search5.yona.AbstractIntegrationTest
import com.github.search5.yona.domain.user.YonaUserDetails
import io.kotest.extensions.spring.SpringExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

class IndexControllerIntegrationSpec @Autowired constructor(
    private val wac: WebApplicationContext
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        describe("IndexController 통합 테스트") {
            it("로그인하지 않은 익명 사용자가 메인 홈(/) 접근 시, 인트로 화면이 노출되어야 한다") {
                mockMvc.perform(get("/"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("index"))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("21st Century Software Development Platform")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("로그인")))
            }

            it("로그인한 사용자가 메인 홈(/) 접근 시, 대시보드 화면과 사용자명이 노출되어야 한다") {
                // Given
                val userDetails = YonaUserDetails(
                    id = 1L,
                    loginId = "gildong",
                    passwordVal = "hashed",
                    passwordSalt = "salt",
                    authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
                )

                // When & Then
                mockMvc.perform(
                    get("/").with(SecurityMockMvcRequestPostProcessors.user(userDetails))
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("index"))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("gildong")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("님")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("새 프로젝트 만들기")))
            }
        }
    }
}
