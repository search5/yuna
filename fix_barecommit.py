import sys

with open("src/test/kotlin/com/github/search5/yona/domain/vcs/BareCommitSpec.kt", "r") as f:
    text = f.read()

# Replace the null passing for name and email with reflection
old = """val user = com.github.search5.yona.domain.user.User(id = 1L, loginId = "tester", name = "tester", email = "tester@yona.io")"""
# No, wait. I didn't write `name = null` in the previous python script?
# Ah! In task 48 I did. But in task 71 I overwrote it. Let's check what is in BareCommitSpec.kt
