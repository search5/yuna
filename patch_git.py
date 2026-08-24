import re

with open("src/test/kotlin/com/github/search5/yona/domain/event/GitPostReceiveEventListenerSpec.kt", "r") as f:
    content = f.read()

import_statement = "import com.github.search5.yona.domain.event.GitPostReceiveEvent\nimport java.io.File\n"
if "import java.io.File" not in content:
    content = content.replace("import io.kotest.core.spec.style.DescribeSpec", import_statement + "import io.kotest.core.spec.style.DescribeSpec")

new_tests = """
    describe("GitPostReceiveEventListener.handleGitPostReceiveEvent") {
        it("should return early if repoDir does not exist") {
            val mockFile = mockk<File>()
            every { mockFile.exists() } returns false
            every { mockFile.absolutePath } returns "/fake/path"
            every { gitService.getRepositoryPath(any(), any()) } returns mockFile
            
            val event = GitPostReceiveEvent(project, sender, emptyList())
            listener.handleGitPostReceiveEvent(event)
            
            verify(exactly = 0) { notificationEventRecorder.record(any()) }
        }
        
        it("should handle exceptions silently") {
            val mockFile = mockk<File>()
            every { mockFile.exists() } returns true
            every { gitService.getRepositoryPath(any(), any()) } returns mockFile
            // This will throw exception because it's not a real git repo
            
            val event = GitPostReceiveEvent(project, sender, listOf(mockk(relaxed = true)))
            listener.handleGitPostReceiveEvent(event)
            
            verify(exactly = 0) { notificationEventRecorder.record(any()) }
        }
    }
"""

content = content.replace("describe(\"GitPostReceiveEventListener.recordReferredIssues\") {", new_tests + "\n    describe(\"GitPostReceiveEventListener.recordReferredIssues\") {")

with open("src/test/kotlin/com/github/search5/yona/domain/event/GitPostReceiveEventListenerSpec.kt", "w") as f:
    f.write(content)
