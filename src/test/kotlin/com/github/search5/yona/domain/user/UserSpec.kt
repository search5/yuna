package com.github.search5.yona.domain.user

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

// yona models/User.java:563-569 isSiteManager() 대응 (P1-119). yona는 별도 SiteAdmin 테이블
// 소속 여부로만 판단하고 loginId 자체를 특별 취급하지 않는다 — loginId=="admin"이면 상태와
// 무관하게 항상 site manager로 취급하는 분기는 yona에 없는 yuna 자체 버그였다.
class UserSpec : DescribeSpec({
    describe("User.isSiteManager") {
        it("state가 SITE_ADMIN이면 true여야 한다") {
            val user = User(loginId = "someone", name = "누군가", email = "someone@example.com", state = UserState.SITE_ADMIN)
            user.isSiteManager shouldBe true
        }

        it("loginId가 admin이어도 state가 SITE_ADMIN이 아니면 false여야 한다") {
            val user = User(loginId = "admin", name = "관리자아님", email = "admin@example.com", state = UserState.ACTIVE)
            user.isSiteManager shouldBe false
        }

        it("state가 SITE_ADMIN이 아니고 loginId도 admin이 아니면 false여야 한다") {
            val user = User(loginId = "gildong", name = "홍길동", email = "gildong@example.com", state = UserState.ACTIVE)
            user.isSiteManager shouldBe false
        }
    }
})
