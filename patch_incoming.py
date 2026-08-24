import re

path = "/home/jiho/yona-convert/yuna/src/test/kotlin/com/github/search5/yona/domain/mail/IncomingMailProcessingServiceSpec.kt"
with open(path, "r") as f:
    content = f.read()

# Add the new test cases right before the last closing brace
new_tests = """
            it("[TASK-01] 에러 메시지가 포함된 도움말 메시지 발송 분기를 커버한다") {
                // IncomingMailOutcome.Rejected를 발생시키고, errors.isNotEmpty() 분기와 
                // sampleAddress != null 분기(기본적으로 inboundBaseAddress가 유효하므로)를 커버한다.
                val message = baseMessage(recipients = listOf("yona+invalid@example.com"))
                every { mailService.sendReply(any(), any(), any(), any(), any()) } returns Unit
                
                service.process(message)
                
                // mailService.sendReply 호출 시 errors 문구가 포함되어 있는지 확인
                verify(exactly = 1) { 
                    mailService.sendReply(
                        any(), any(), any(), 
                        match { it.contains("요청을 처리하는 중 다음과 같은 문제가 발생했습니다") }, 
                        any()
                    )
                }
            }

            it("[TASK-02] resolveByDeterministicMessageId에서 유효하지 않은 ResourceType 예외 발생 분기를 커버한다") {
                // resolveByDeterministicMessageId 내에서 IllegalArgumentException 발생 유도
                val message = baseMessage(recipients = listOf("yona+dlab/hive@example.com"))
                    .copy(inReplyTo = listOf("<invalid_type/123@yona.io>"))
                
                // 에러 발생 없이 무시되고 새 이슈 생성으로 넘어가야 함
                every { issueRepository.findByProjectAndNumber(project, 1L) } returns Optional.empty()
                every { issueService.createIssue(any()) } answers { firstArg<Issue>().apply { id = 123L } }
                
                service.process(message)
            }

            it("[TASK-03] createComment 도달 불가 분기 및 resolveResourceProject else 분기 커버") {
                // owner/project/project/123 으로 보내면 resolveDirectResource가 ResourceType.PROJECT를 파싱하고
                // resolveResourceProject에서 else -> null 을 리턴하여 스레드로 인식되지 않고 새 이슈 생성으로 넘어간다.
                val message = baseMessage(recipients = listOf("yona+dlab/hive/project/123@example.com"))
                every { issueRepository.findByProjectAndNumber(project, 1L) } returns Optional.empty()
                every { issueService.createIssue(any()) } answers { firstArg<Issue>().apply { id = 124L } }
                
                service.process(message)
            }

            it("[TASK-04] attachAttachments else 분기 커버") {
                val noPermissionSender = User(id = 3L, loginId = "", name = "이름없음2", email = "noname2@example.com")
                every { userRepository.findByEmail("noname2@example.com") } returns Optional.of(noPermissionSender)
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val existingIssue = Issue(id = 56L, title = "기존 이슈", body = "...", project = project, number = 3L, authorId = 999L)
                every { issueRepository.findById(56L) } returns Optional.of(existingIssue)

                val message = baseMessage(recipients = listOf("yona+dlab/hive/issue_post/56@example.com"))
                    .copy(
                        fromAddress = "noname2@example.com",
                        attachments = listOf(InboundAttachment("test.txt", "text/plain", "abc".toByteArray()))
                    )
                val result = service.process(message)

                result.size shouldBe 1
                result[0].shouldBeInstanceOf<IncomingMailOutcome.Rejected>()
                // attachAttachments가 emptyMap을 리턴하고 예외 없이 종료된다.
            }
"""

content = content.replace("            }\n        }\n    }\n})", "            }\n" + new_tests + "        }\n    }\n})")

with open(path, "w") as f:
    f.write(content)
