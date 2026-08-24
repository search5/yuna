package com.github.search5.yona.domain.user

import com.github.search5.yona.AbstractIntegrationTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import javax.naming.AuthenticationException
import javax.naming.CommunicationException
import javax.naming.NamingException
import javax.naming.directory.BasicAttributes
import javax.naming.directory.InitialDirContext
import javax.naming.directory.SearchResult
import javax.naming.NamingEnumeration

@Transactional
@TestPropertySource(properties = [
    "yuna.ldap.enabled=true",
    "yuna.ldap.host=127.0.0.1",
    "yuna.ldap.port=389",
    "yuna.ldap.protocol=ldap",
    "yuna.ldap.base-dn=dc=yona,dc=io"
])
class LdapServiceSpec @Autowired constructor(
    private val ldapService: LdapService,
    private val userRepository: UserRepository
) : AbstractIntegrationTest() {

    init {
        describe("LdapService") {
            beforeEach {
                mockkConstructor(InitialDirContext::class)
            }
            afterEach {
                unmockkConstructor(InitialDirContext::class)
            }

            it("인증 실패 시 InvalidCredentials를 반환해야 한다") {
                every { anyConstructed<InitialDirContext>().search(any<String>(), any<String>(), any()) } throws AuthenticationException("Auth failed")

                val result = ldapService.authenticate("testuser", "password")
                result.shouldBeInstanceOf<LdapAuthResult.InvalidCredentials>()
            }

            it("통신 실패 시 ConnectionFailed(CommunicationException)를 반환해야 한다") {
                every { anyConstructed<InitialDirContext>().search(any<String>(), any<String>(), any()) } throws CommunicationException("Comm failed")

                val result = ldapService.authenticate("testuser", "password")
                result.shouldBeInstanceOf<LdapAuthResult.ConnectionFailed>()
            }

            it("기타 NamingException 발생 시 ConnectionFailed를 반환해야 한다") {
                every { anyConstructed<InitialDirContext>().search(any<String>(), any<String>(), any()) } throws NamingException("Naming error")

                val result = ldapService.authenticate("testuser", "password")
                result.shouldBeInstanceOf<LdapAuthResult.ConnectionFailed>()
            }

            it("사용자 검색 결과가 없으면 InvalidCredentials를 반환해야 한다") {
                val mockEnum = mockk<NamingEnumeration<SearchResult>>()
                every { mockEnum.hasMoreElements() } returns false
                every { anyConstructed<InitialDirContext>().search(any<String>(), any<String>(), any()) } returns mockEnum

                val result = ldapService.authenticate("testuser", "password")
                result.shouldBeInstanceOf<LdapAuthResult.InvalidCredentials>()
            }

            it("사용자 검색 결과가 2개 이상이면 InvalidCredentials를 반환해야 한다") {
                val mockEnum = mockk<NamingEnumeration<SearchResult>>()
                val mockResult = mockk<SearchResult>()
                every { mockEnum.hasMoreElements() } returns true
                every { mockEnum.nextElement() } returns mockResult
                // 다시 호출할 때도 true를 반환하면 2개 이상임을 나타냄
                every { anyConstructed<InitialDirContext>().search(any<String>(), any<String>(), any()) } answers {
                    val enum2 = mockk<NamingEnumeration<SearchResult>>()
                    var count = 0
                    every { enum2.hasMoreElements() } answers {
                        count < 2
                    }
                    every { enum2.nextElement() } answers {
                        count++
                        mockResult
                    }
                    enum2
                }

                val result = ldapService.authenticate("testuser", "password")
                result.shouldBeInstanceOf<LdapAuthResult.InvalidCredentials>()
            }

            it("정상적으로 사용자를 찾아 Success를 반환해야 한다") {
                val attrs = BasicAttributes()
                attrs.put("displayName", "Test User")
                attrs.put("mail", "test@yona.io")
                attrs.put("sAMAccountName", "testuser")
                val mockResult = SearchResult("cn=test", null, attrs)

                every { anyConstructed<InitialDirContext>().search(any<String>(), any<String>(), any()) } answers {
                    val enum2 = mockk<NamingEnumeration<SearchResult>>()
                    var count = 0
                    every { enum2.hasMoreElements() } answers {
                        count < 1
                    }
                    every { enum2.nextElement() } answers {
                        count++
                        mockResult
                    }
                    enum2
                }

                val result = ldapService.authenticate("testuser", "password")
                result.shouldBeInstanceOf<LdapAuthResult.Success>()
                val success = result as LdapAuthResult.Success
                success.user.loginId shouldBe "testuser"
            }
        }
    }
}
