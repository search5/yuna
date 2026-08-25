package com.github.search5.yona.domain.user

import com.github.search5.yona.AbstractIntegrationTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.Transferable
import javax.naming.AuthenticationException
import javax.naming.CommunicationException
import javax.naming.NamingException
import javax.naming.directory.BasicAttributes
import javax.naming.directory.InitialDirContext
import javax.naming.directory.SearchResult
import javax.naming.NamingEnumeration

// LdapService.kt의 클래스 KDoc은 "이 저장소에 LDAP 테스트 서버가 없어 실제 바인딩 경로는
// 단위테스트 대상에서 제외한다"고 적혀 있었으나, 2026-08-25 podman으로 실제 OpenLDAP을 띄워
// 재현한 뒤 Testcontainers(GenericContainer)로 자동화했다 — 이제 실제 바인딩(InitialDirContext
// 생성자의 LDAP simple bind)까지 커버한다. 생성자 자체는 mockkConstructor로 가로챌 수 없어서
// (JNDI가 생성자 안에서 즉시 실제 bind를 수행) 실제 서버가 반드시 필요했다 — search() 결과만
// 시나리오별로 mockkConstructor로 오버라이드해 세부 분기(검색결과 0/1/2건)를 만든다.
@Transactional
@TestPropertySource(properties = [
    "yuna.ldap.enabled=true",
    "yuna.ldap.protocol=ldap",
    "yuna.ldap.base-dn=dc=yona,dc=io",
    "yuna.ldap.dn-postfix=dc=yona,dc=io",
    "yuna.ldap.login-property=uid",
    "yuna.ldap.user-name-property=uid"
])
class LdapServiceSpec @Autowired constructor(
    private val ldapService: LdapService,
    private val userRepository: UserRepository
) : AbstractIntegrationTest() {

    companion object {
        private val ldapContainer = GenericContainer("osixia/openldap:1.5.0").apply {
            withExposedPorts(389)
            withEnv("LDAP_ORGANISATION", "Yona")
            withEnv("LDAP_DOMAIN", "yona.io")
            withEnv("LDAP_ADMIN_PASSWORD", "admin")
            withCopyToContainer(
                Transferable.of(
                    """
                    dn: uid=testuser,dc=yona,dc=io
                    objectClass: inetOrgPerson
                    objectClass: posixAccount
                    objectClass: shadowAccount
                    uid: testuser
                    cn: Test User
                    sn: User
                    givenName: Test
                    mail: test@yona.io
                    userPassword: password
                    uidNumber: 10001
                    gidNumber: 10001
                    homeDirectory: /home/testuser
                    """.trimIndent()
                ),
                "/tmp/testuser.ldif"
            )
            waitingFor(Wait.forListeningPort())
        }

        init {
            ldapContainer.start()
            val addResult = ldapContainer.execInContainer(
                "ldapadd", "-x", "-D", "cn=admin,dc=yona,dc=io", "-w", "admin", "-f", "/tmp/testuser.ldif"
            )
            check(addResult.exitCode == 0) { "LDAP 테스트 유저 등록 실패: ${addResult.stderr}" }
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerLdapProperties(registry: DynamicPropertyRegistry) {
            registry.add("yuna.ldap.host") { ldapContainer.host }
            registry.add("yuna.ldap.port") { ldapContainer.getMappedPort(389).toString() }
        }
    }

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
                attrs.put("uid", "testuser")
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
