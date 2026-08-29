package com.github.search5.yona.domain.apitoken

import com.github.search5.yona.AbstractIntegrationTest
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant
import java.time.temporal.ChronoUnit

// yona-wiki P3-02 Step1 — 2026-08-24 결정("무기한 토큰 발급 금지")을 실제 DB 제약으로 강제하는지
// 검증한다. OrganizationRepositorySpec 패턴(AbstractIntegrationTest + 리포지토리 직접 주입) 참고.
class ApiTokenSpec @Autowired constructor(
    private val apiTokenRepository: ApiTokenRepository,
    private val userRepository: UserRepository
) : AbstractIntegrationTest() {

    init {
        describe("ApiToken") {
            beforeEach {
                apiTokenRepository.deleteAll()
                userRepository.deleteAll()
            }

            // beforeEach는 각 테스트 "이전"에만 청소하므로 스펙의 마지막 테스트가 남긴 데이터는
            // 청소되지 않고 공유 테스트 DB에 그대로 남는다 — api_token.owner_id가 cascade delete
            // 없는 FK라, 이후 실행되는 다른 스펙의 userRepository.deleteAll()이 그 유저를 못 지워
            // 깨지는 문제가 전체 스위트 실행에서 실제로 발생했다(ApiTokenScopedAuthorizationIntegrationSpec과
            // 동일한 원인).
            afterSpec {
                apiTokenRepository.deleteAll()
                userRepository.deleteAll()
            }

            it("expiresAt이 null이면 저장이 거부되어야 한다") {
                val owner = userRepository.save(
                    User(loginId = "token-owner", name = "토큰소유자", email = "token-owner@example.com")
                )
                val token = ApiToken(owner = owner, tokenHash = "hash-without-expiry", expiresAt = null)

                shouldThrow<DataIntegrityViolationException> {
                    apiTokenRepository.saveAndFlush(token)
                }
            }

            it("expiresAt이 있으면 정상적으로 저장되고 tokenHash로 조회할 수 있어야 한다") {
                val owner = userRepository.save(
                    User(loginId = "token-owner2", name = "토큰소유자2", email = "token-owner2@example.com")
                )
                // MariaDB datetime 컬럼은 마이크로초까지만 저장한다 — Instant.now()가 나노초 정밀도를
                // 가지면 저장 후 재조회 시 미세하게 달라져 shouldBe가 실패한다(실측 확인). 기대값을
                // 미리 마이크로초로 절삭해 DB 왕복 후에도 동일하게 비교되도록 한다.
                val expiry = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS)
                val token = ApiToken(
                    owner = owner,
                    tokenHash = "hash-with-expiry",
                    expiresAt = expiry,
                    allRepositories = true
                )

                val saved = apiTokenRepository.saveAndFlush(token)

                saved.id shouldNotBe null

                val found = apiTokenRepository.findByTokenHash("hash-with-expiry").orElse(null)
                found shouldNotBe null
                found.expiresAt shouldBe expiry
                found.allRepositories shouldBe true
                found.owner?.loginId shouldBe "token-owner2"
            }

            it("lastUsedAt은 발급 시점에 비워둘 수 있어야 한다(아직 사용된 적 없음)") {
                val owner = userRepository.save(
                    User(loginId = "token-owner3", name = "토큰소유자3", email = "token-owner3@example.com")
                )
                val token = ApiToken(
                    owner = owner,
                    tokenHash = "hash-unused",
                    expiresAt = Instant.now().plus(1, ChronoUnit.DAYS)
                )

                val saved = apiTokenRepository.saveAndFlush(token)

                saved.lastUsedAt shouldBe null
            }
        }
    }
}
