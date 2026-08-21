package com.github.search5.yona.domain.attachment

import com.github.search5.yona.AbstractIntegrationTest
import com.github.search5.yona.domain.enumeration.ResourceType
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit

@Transactional
class AttachmentServiceSpec @Autowired constructor(
    private val attachmentService: AttachmentService,
    private val attachmentRepository: AttachmentRepository,
    private val cleanupScheduler: AttachmentCleanupScheduler,
    @Value("\${yuna.upload.base-dir:/tmp/yuna/uploads}")
    private val baseDir: String
) : AbstractIntegrationTest() {

    init {
        describe("AttachmentService 파일 업로드 및 관리 테스트") {
            beforeEach {
                attachmentRepository.deleteAll()
                val uploadDir = File(baseDir)
                if (uploadDir.exists()) {
                    uploadDir.deleteRecursively()
                }
            }

            it("1. 단일 파일 업로드 및 해시 기반 물리 파일 생성 검증") {
                val content = "Yona Project Attachment Test Data"
                val stream = ByteArrayInputStream(content.toByteArray())

                val (attachment, isNew) = attachmentService.store(
                    stream,
                    "test.txt",
                    ResourceType.USER,
                    "user1",
                    "chulsoo"
                )

                isNew shouldBe true
                attachment.id shouldNotBe null
                attachment.name shouldBe "test.txt"
                attachment.containerType shouldBe ResourceType.USER
                attachment.containerId shouldBe "user1"
                attachment.ownerLoginId shouldBe "chulsoo"
                attachment.size shouldBe content.toByteArray().size.toLong()

                val physicalFile = attachmentService.getFile(attachment)
                physicalFile.exists() shouldBe true
                physicalFile.readText() shouldBe content
            }

            it("2. 동일 파일 중복 업로드 시 물리 파일 단일성 및 DB 메타데이터 다중성 검증") {
                val content = "Duplicate File Content"
                val stream1 = ByteArrayInputStream(content.toByteArray())
                val stream2 = ByteArrayInputStream(content.toByteArray())

                val (attach1, isNew1) = attachmentService.store(
                    stream1,
                    "first.txt",
                    ResourceType.USER,
                    "user1",
                    "chulsoo"
                )

                val (attach2, isNew2) = attachmentService.store(
                    stream2,
                    "second.txt",
                    ResourceType.USER,
                    "user1",
                    "chulsoo"
                )

                // 이름이 서로 다르므로(first.txt vs second.txt) yona 대응 dedup 키
                // (name+hash+containerType+containerId)에 걸리지 않아 둘 다 새 행이어야 한다.
                isNew1 shouldBe true
                isNew2 shouldBe true
                attach1.hash shouldBe attach2.hash
                attachmentRepository.count() shouldBe 2

                val file1 = attachmentService.getFile(attach1)
                val file2 = attachmentService.getFile(attach2)
                file1.absolutePath shouldBe file2.absolutePath
                file1.exists() shouldBe true

                attachmentService.delete(attach1)
                attachmentRepository.count() shouldBe 1
                file2.exists() shouldBe true

                attachmentService.delete(attach2)
                attachmentRepository.count() shouldBe 0
                file2.exists() shouldBe false
            }

            it("3. 임시 업로드 파일들의 최종 컨테이너 바인딩(moveAll) 검증") {
                val stream1 = ByteArrayInputStream("File 1".toByteArray())
                val stream2 = ByteArrayInputStream("File 2".toByteArray())

                attachmentService.store(stream1, "file1.txt", ResourceType.USER, "user1", "chulsoo")
                attachmentService.store(stream2, "file2.txt", ResourceType.USER, "user1", "chulsoo")

                val movedCount = attachmentService.moveAll(
                    ResourceType.USER, "user1",
                    ResourceType.ISSUE_POST, "42"
                )

                movedCount shouldBe 2

                val list = attachmentRepository.findByContainerTypeAndContainerId(ResourceType.ISSUE_POST, "42")
                list.size shouldBe 2
                list.any { it.name == "file1.txt" } shouldBe true
                list.any { it.name == "file2.txt" } shouldBe true
            }

            // yona Attachment.java:438-477 cleanupTemporaryUploadFilesWithSchedule() 대응 (P2-26).
            // 원본은 .ge("createdDate", now-keepAlive) — "오래된" 파일이 아니라 keepAlive 이내에
            // "최근" 업로드된 파일을 정리 대상으로 삼는다(스케줄러 취지와 반대로 보이는 yona 자체의
            // 버그로 의심되지만, 사용자 지시로 레거시 비교 방향을 그대로 포팅했다 — 백로그 P2-26 TODO).
            it("4. 임시 업로드 파일 자동 클린업 스케줄러는 keepAlive 이내의 '최근' 파일을 삭제한다(yona 원본 비교 방향, P2-26)") {
                val stream = ByteArrayInputStream("Recent Temp File".toByteArray())
                val (attachment, _) = attachmentService.store(
                    stream,
                    "recent_temp.txt",
                    ResourceType.USER,
                    "user1",
                    "chulsoo"
                )

                attachment.createdDate = Instant.now().minus(1, ChronoUnit.HOURS)
                attachmentRepository.saveAndFlush(attachment)

                val file = attachmentService.getFile(attachment)
                file.exists() shouldBe true

                cleanupScheduler.cleanupTemporaryFiles()

                attachmentRepository.existsById(attachment.id!!) shouldBe false
                file.exists() shouldBe false
            }

            it("4-1. keepAlive보다 더 오래된 파일은 yona 원본 비교 방향상 삭제되지 않는다(P2-26)") {
                val stream = ByteArrayInputStream("Truly Old Temp File".toByteArray())
                val (attachment, _) = attachmentService.store(
                    stream,
                    "old_temp.txt",
                    ResourceType.USER,
                    "user1",
                    "chulsoo"
                )

                attachment.createdDate = Instant.now().minus(25, ChronoUnit.HOURS)
                attachmentRepository.saveAndFlush(attachment)

                cleanupScheduler.cleanupTemporaryFiles()

                attachmentRepository.existsById(attachment.id!!) shouldBe true
            }

            // yona Attachment.moveOnlySelected() 대응 (P0-22). 원컨테이너/업로더 검증 없이 요청받은
            // ID를 그대로 재배선하면, 다른 사람이 업로드했거나 이미 다른 리소스에 붙은 첨부파일을
            // 임의로 자기 새 이슈/게시글/마일스톤에 강제 재배선할 수 있었다.
            it("5. 본인이 업로드한 임시 첨부만 moveOnlySelected로 옮겨져야 한다") {
                val (attachment, _) = attachmentService.store(
                    ByteArrayInputStream("mine".toByteArray()), "mine.txt",
                    ResourceType.NOT_A_RESOURCE, "", "chulsoo"
                )

                val movedCount = attachmentService.moveOnlySelected(
                    ResourceType.NOT_A_RESOURCE, "",
                    ResourceType.ISSUE_POST, "99",
                    listOf(attachment.id!!), "chulsoo"
                )

                movedCount shouldBe 1
                val moved = attachmentRepository.findById(attachment.id!!).get()
                moved.containerType shouldBe ResourceType.ISSUE_POST
                moved.containerId shouldBe "99"
            }

            it("6. 다른 사용자가 업로드한 첨부는 ownerLoginId가 일치하지 않으면 옮기지 않아야 한다") {
                val (attachment, _) = attachmentService.store(
                    ByteArrayInputStream("victim".toByteArray()), "victim.txt",
                    ResourceType.NOT_A_RESOURCE, "", "victim-user"
                )

                val movedCount = attachmentService.moveOnlySelected(
                    ResourceType.NOT_A_RESOURCE, "",
                    ResourceType.ISSUE_POST, "99",
                    listOf(attachment.id!!), "attacker"
                )

                movedCount shouldBe 0
                val unchanged = attachmentRepository.findById(attachment.id!!).get()
                unchanged.containerType shouldBe ResourceType.NOT_A_RESOURCE
                unchanged.containerId shouldBe ""
            }

            it("7. 이미 다른 컨테이너에 붙어있는 첨부는 from 컨테이너가 일치하지 않으면 옮기지 않아야 한다") {
                val (attachment, _) = attachmentService.store(
                    ByteArrayInputStream("already-attached".toByteArray()), "already.txt",
                    ResourceType.ISSUE_POST, "1", "chulsoo"
                )

                val movedCount = attachmentService.moveOnlySelected(
                    ResourceType.NOT_A_RESOURCE, "",
                    ResourceType.ISSUE_POST, "99",
                    listOf(attachment.id!!), "chulsoo"
                )

                movedCount shouldBe 0
                val unchanged = attachmentRepository.findById(attachment.id!!).get()
                unchanged.containerType shouldBe ResourceType.ISSUE_POST
                unchanged.containerId shouldBe "1"
            }

            // yona Attachment.java:75-85 findBy(Attachment) 대응 (P2-24). 이전에는 매번 새 행을
            // 저장해 재업로드마다 DB에 중복 행이 쌓였다 — 동일 컨테이너에 동일 이름·내용으로
            // 재업로드하면 새 행을 만들지 않고 기존 행을 재사용해야 한다.
            it("8. 동일 이름·내용·컨테이너로 재업로드하면 새 행을 만들지 않고 기존 첨부를 재사용해야 한다 (P2-24)") {
                val content = "Exactly The Same Content"

                val (first, isNew1) = attachmentService.store(
                    ByteArrayInputStream(content.toByteArray()), "dup.txt",
                    ResourceType.ISSUE_POST, "7", "chulsoo"
                )
                val (second, isNew2) = attachmentService.store(
                    ByteArrayInputStream(content.toByteArray()), "dup.txt",
                    ResourceType.ISSUE_POST, "7", "chulsoo"
                )

                isNew1 shouldBe true
                isNew2 shouldBe false
                second.id shouldBe first.id
                attachmentRepository.count() shouldBe 1
            }

            // yona FileUtil.java:113-142 detectMediaType() 대응 (P2-25). 실제 저장 파일은 해시
            // 이름(확장자 없음)이므로, JDK Files.probeContentType()은 사실상 항상 감지에 실패해
            // application/octet-stream만 반환했다 — Tika로 실제 콘텐츠(매직 바이트)를 감지해야 한다.
            it("9. 물리 저장 파일명이 확장자 없는 해시여도 실제 콘텐츠(매직 바이트)로 MIME 타입을 정확히 감지해야 한다 (P2-25)") {
                val pngMagicBytes = byteArrayOf(
                    0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                    1, 2, 3, 4, 5, 6, 7, 8
                )

                val (attachment, _) = attachmentService.store(
                    ByteArrayInputStream(pngMagicBytes), "screenshot.png",
                    ResourceType.ISSUE_POST, "1", "chulsoo"
                )

                attachment.mimeType shouldBe "image/png"
            }

            it("10. 텍스트 파일은 콘텐츠 기반 charset 파라미터를 포함한 MIME 타입으로 감지돼야 한다 (P2-25)") {
                // 비-ASCII(한글) 바이트가 있어야 UniversalDetector가 US-ASCII가 아닌 UTF-8로 확정 감지한다.
                val content = "안녕하세요, 요나!"

                val (attachment, _) = attachmentService.store(
                    ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)), "note.txt",
                    ResourceType.ISSUE_POST, "2", "chulsoo"
                )

                attachment.mimeType shouldBe "text/plain; charset=UTF-8"
            }
        }
    }
}
