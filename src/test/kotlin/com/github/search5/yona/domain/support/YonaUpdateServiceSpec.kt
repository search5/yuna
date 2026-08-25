package com.github.search5.yona.domain.support

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.LsRemoteCommand
import org.eclipse.jgit.lib.Ref

// yona YobiUpdate.java:40-41 대응 (P2-10). **표현 정정**: 최초 등록 문구는 "yona 1시간 기본값 대비 [GL-models_YobiUpdate-002]
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

    describe("YonaUpdateService properties and checkForUpdate") {
        it("기본 프로퍼티 반환 테스트") {
            val service = YonaUpdateService("repo", "1.15.0")
            service.getLatestVersion() shouldBe null
            service.isUpdateRequired() shouldBe false
            service.getReleaseUrl() shouldBe "https://github.com/yona-projects/yona/releases/tag/v"
        }

        it("checkForUpdate에서 Exception이 발생하면 로그만 남기고 지나간다") {
            val service = YonaUpdateService("invalid-repo", "1.15.0")
            io.mockk.mockkStatic(Git::class)
            every { Git.lsRemoteRepository() } throws RuntimeException("Git Error")
            
            service.checkForUpdate()
            service.isUpdateRequired() shouldBe false
            io.mockk.unmockkStatic(Git::class)
        }

        it("정상적으로 태그를 읽어와 업데이트가 필요한 경우") {
            val service = YonaUpdateService("repo", "1.15.0")
            
            val mockRef1 = mockk<Ref>()
            every { mockRef1.name } returns "^refs/tags/v1.16.0"
            val mockRef2 = mockk<Ref>()
            every { mockRef2.name } returns "^refs/tags/1.14.0-beta"
            val mockRef3 = mockk<Ref>()
            every { mockRef3.name } returns "^refs/tags/vX.Y.Z" // parse 실패용
            
            val lsCommand = mockk<LsRemoteCommand>()
            every { lsCommand.setRemote(any()) } returns lsCommand
            every { lsCommand.setHeads(any()) } returns lsCommand
            every { lsCommand.setTags(any()) } returns lsCommand
            every { lsCommand.call() } returns listOf(mockRef1, mockRef2, mockRef3)

            io.mockk.mockkStatic(Git::class)
            every { Git.lsRemoteRepository() } returns lsCommand
            
            service.checkForUpdate()
            
            service.isUpdateRequired() shouldBe true
            service.getLatestVersion() shouldBe "1.16.0"
            io.mockk.unmockkStatic(Git::class)
        }

        it("최신 버전이 이미 적용된 경우") {
            val service = YonaUpdateService("repo", "1.16.0")
            
            val mockRef = mockk<Ref>()
            every { mockRef.name } returns "^refs/tags/v1.15.0"
            
            val lsCommand = mockk<LsRemoteCommand>()
            every { lsCommand.setRemote(any()) } returns lsCommand
            every { lsCommand.setHeads(any()) } returns lsCommand
            every { lsCommand.setTags(any()) } returns lsCommand
            every { lsCommand.call() } returns listOf(mockRef)

            io.mockk.mockkStatic(Git::class)
            every { Git.lsRemoteRepository() } returns lsCommand
            
            service.checkForUpdate()
            
            service.isUpdateRequired() shouldBe false
            service.getLatestVersion() shouldBe null
            io.mockk.unmockkStatic(Git::class)
        }
        
        it("currentVersion 파싱 실패 시 1.15.0을 기본으로 동작한다") {
            val service = YonaUpdateService("repo", "invalid-version")
            
            val mockRef = mockk<Ref>()
            every { mockRef.name } returns "^refs/tags/v1.16.0"
            
            val lsCommand = mockk<LsRemoteCommand>()
            every { lsCommand.setRemote(any()) } returns lsCommand
            every { lsCommand.setHeads(any()) } returns lsCommand
            every { lsCommand.setTags(any()) } returns lsCommand
            every { lsCommand.call() } returns listOf(mockRef)

            io.mockk.mockkStatic(Git::class)
            every { Git.lsRemoteRepository() } returns lsCommand
            
            service.checkForUpdate()
            
            service.isUpdateRequired() shouldBe true
            io.mockk.unmockkStatic(Git::class)
        }
    }
})
