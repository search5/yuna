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

                val attachment = attachmentService.store(
                    stream,
                    "test.txt",
                    ResourceType.USER,
                    "user1",
                    "chulsoo"
                )

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

                val attach1 = attachmentService.store(
                    stream1,
                    "first.txt",
                    ResourceType.USER,
                    "user1",
                    "chulsoo"
                )

                val attach2 = attachmentService.store(
                    stream2,
                    "second.txt",
                    ResourceType.USER,
                    "user1",
                    "chulsoo"
                )

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

            it("4. 임시 업로드 파일 자동 클린업 스케줄러 기능 검증") {
                val stream = ByteArrayInputStream("Old Temp File".toByteArray())
                val attachment = attachmentService.store(
                    stream,
                    "old_temp.txt",
                    ResourceType.USER,
                    "user1",
                    "chulsoo"
                )

                attachment.createdDate = Instant.now().minus(25, ChronoUnit.HOURS)
                attachmentRepository.saveAndFlush(attachment)

                val file = attachmentService.getFile(attachment)
                file.exists() shouldBe true

                cleanupScheduler.cleanupTemporaryFiles()

                attachmentRepository.existsById(attachment.id!!) shouldBe false
                file.exists() shouldBe false
            }
        }
    }
}
