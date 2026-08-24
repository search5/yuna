import sys

with open("src/test/kotlin/com/github/search5/yona/domain/user/UserServiceImplSpec.kt", "r") as f:
    lines = f.readlines()

# Find the last "})"
last_idx = -1
for i, line in enumerate(lines):
    if line.strip() == "})":
        last_idx = i

if last_idx != -1:
    new_test = """
        describe("Coverage addition for UserServiceImpl") {
            it("should handle null email.user.id in deleteEmail") {
                val nullIdUser = com.github.search5.yona.domain.user.User(id = null, loginId = "tester", email = "test@yona.io")
                val email = com.github.search5.yona.domain.user.Email(id = 10L, user = nullIdUser, email = "sub@yona.io")
                io.mockk.every { emailRepository.findById(10L) } returns java.util.Optional.of(email)
                
                val ex = io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> {
                    userService.deleteEmail(1L, 10L)
                }
                ex.message shouldBe "삭제 권한이 없습니다."
            }

            it("should handle null email.user.id in sendValidationEmail") {
                val nullIdUser = com.github.search5.yona.domain.user.User(id = null, loginId = "tester", email = "test@yona.io")
                val email = com.github.search5.yona.domain.user.Email(id = 10L, user = nullIdUser, email = "sub@yona.io")
                io.mockk.every { emailRepository.findById(10L) } returns java.util.Optional.of(email)
                
                val ex = io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> {
                    userService.sendValidationEmail(1L, 10L, "http://localhost")
                }
                ex.message shouldBe "메일을 보낼 권한이 없습니다."
            }

            it("should handle null email.user.id in setAsMainEmail") {
                val nullIdUser = com.github.search5.yona.domain.user.User(id = null, loginId = "tester", email = "test@yona.io")
                val email = com.github.search5.yona.domain.user.Email(id = 10L, user = nullIdUser, email = "sub@yona.io")
                io.mockk.every { userRepository.findById(1L) } returns java.util.Optional.of(com.github.search5.yona.domain.user.User(id = 1L, loginId = "test", email = "test@yona.io"))
                io.mockk.every { emailRepository.findById(10L) } returns java.util.Optional.of(email)
                
                val ex = io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> {
                    userService.setAsMainEmail(1L, 10L)
                }
                ex.message shouldBe "변경 권한이 없습니다."
            }
        }
    """
    lines.insert(last_idx, new_test + "\n")
    with open("src/test/kotlin/com/github/search5/yona/domain/user/UserServiceImplSpec.kt", "w") as f:
        f.writelines(lines)
