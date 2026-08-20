package com.github.search5.yona.domain.support

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.Runs
import io.mockk.spyk
import io.mockk.verify

// yona YobiUpdate.java:40-41 대응 (P2-10). **표현 정정**: 최초 등록 문구는 "yona 1시간 기본값 대비
// yuna 24시간, 24배 차이"였으나 코드 레벨 fallback(1시간)만 확인하고 실제 배포용 conf 템플릿을
// 대조하지 않은 것이었음 — `application.conf.default:253`에 `application.update.notification.interval = 6h`로
// 명시적 오버라이드가 존재해 실제 legacy 동작 기준으로는 6시간 대비 24시간(4배 차이)이 정확하다.
// 또한 yona는 interval을 설정 가능하게 만들고(`application.update.notification.interval`), 0 이하면
// 폴링 자체를 등록하지 않는다(`YobiUpdate.onStart()`) — yuna는 두 가지 다 하드코딩으로 축약돼 있었다.
class YonaUpdateServiceSpec : DescribeSpec({
    describe("YonaUpdateService.refreshVersionToUpdate") {
        it("interval이 0 이하면 실제 업데이트 확인 로직을 실행하지 않아야 한다(yona YobiUpdate.onStart()의 폴링 비활성화 대응)") {
            val service = spyk(
                YonaUpdateService(
                    repositoryUrl = "https://github.com/yona-projects/yona.git",
                    currentVersion = "1.15.0",
                    intervalMillis = 0L
                )
            )
            every { service.checkForUpdate() } just Runs

            service.refreshVersionToUpdate()

            verify(exactly = 0) { service.checkForUpdate() }
        }

        it("interval이 양수면 실제 업데이트 확인 로직을 실행해야 한다") {
            val service = spyk(
                YonaUpdateService(
                    repositoryUrl = "https://github.com/yona-projects/yona.git",
                    currentVersion = "1.15.0",
                    intervalMillis = 21600000L
                )
            )
            every { service.checkForUpdate() } just Runs

            service.refreshVersionToUpdate()

            verify(exactly = 1) { service.checkForUpdate() }
        }
    }
})
