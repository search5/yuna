import re

path = "/home/jiho/yona-convert/yuna/src/test/kotlin/com/github/search5/yona/web/AttachmentControllerSpec.kt"
with open(path, "r") as f:
    content = f.read()

new_tests = """
        describe("미커버 분기 테스트") {
            it("[TASK-09] getFile에서 file이 존재하지 않는 경우 INTERNAL_SERVER_ERROR를 반환한다") {
                val attachment = Attachment(id = 100L, name = "test.txt", hash = "abc", containerType = ResourceType.ISSUE_POST, containerId = "1", ownerLoginId = "user1")
                every { attachmentRepository.findById(100L) } returns Optional.of(attachment)
                every { accessControl.isAllowedAttachment(any(), attachment, Operation.READ) } returns true
                val mockFile = mockk<java.io.File>()
                every { mockFile.exists() } returns false
                every { attachmentService.getFile(attachment) } returns mockFile
                
                mockMvc.perform(
                    get("/api/attachments/100")
                ).andExpect(status().isInternalServerError)
            }
        }
"""

content = content.replace("    }\n})", new_tests + "    }\n})")

with open(path, "w") as f:
    f.write(content)
