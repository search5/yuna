package com.github.search5.yona.domain.user

import com.github.search5.yona.AbstractIntegrationTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

class UserRepositorySpec @Autowired constructor(
    private val userRepository: UserRepository
) : AbstractIntegrationTest() {

    init {
        describe("UserRepository") {
            beforeEach {
                userRepository.deleteAll()
            }

            it("사용자를 정상적으로 저장하고 조회할 수 있어야 한다") {
                // Given
                val user = User(
                    name = "홍길동",
                    loginId = "gildong",
                    email = "gildong@example.com",
                    createdDate = Instant.now()
                )

                // When
                val savedUser = userRepository.save(user)

                // Then
                savedUser.id shouldNotBe null
                
                val foundUser = userRepository.findByLoginId("gildong").orElse(null)
                foundUser shouldNotBe null
                foundUser.name shouldBe "홍길동"
                foundUser.email shouldBe "gildong@example.com"
            }
        }
    }
}
