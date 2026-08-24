import sys

with open("src/test/kotlin/com/github/search5/yona/domain/pullrequest/PullRequestServiceSpec.kt", "r") as f:
    text = f.read()

text = text.replace('toProject = project', 'toProject = com.github.search5.yona.domain.project.Project(id=1L, owner="owner", name="repo")')
text = text.replace('fromProject = project', 'fromProject = com.github.search5.yona.domain.project.Project(id=1L, owner="owner", name="repo")')
text = text.replace('contributor = user', 'contributor = com.github.search5.yona.domain.user.User(id=1L, loginId="user", name="user", email="u@y.io")')
text = text.replace('repositoryService.getRepository(project)', 'repositoryService.getRepository(any())')
text = text.replace('pullRequestService.previewMerge(project, project,', 'pullRequestService.previewMerge(com.github.search5.yona.domain.project.Project(id=1L, owner="owner", name="repo"), com.github.search5.yona.domain.project.Project(id=1L, owner="owner", name="repo"),')
text = text.replace('pullRequestService.merge(1L, user)', 'pullRequestService.merge(1L, com.github.search5.yona.domain.user.User(id=1L, loginId="user", name="user", email="u@y.io"))')
text = text.replace('addReviewer(1L, user)', 'addReviewer(1L, com.github.search5.yona.domain.user.User(id=1L, loginId="user", name="user", email="u@y.io"))')
text = text.replace('removeReviewer(1L, user)', 'removeReviewer(1L, com.github.search5.yona.domain.user.User(id=1L, loginId="user", name="user", email="u@y.io"))')
text = text.replace('pr.reviewers.add(user)', 'pr.reviewers.add(com.github.search5.yona.domain.user.User(id=1L, loginId="user", name="user", email="u@y.io"))')

with open("src/test/kotlin/com/github/search5/yona/domain/pullrequest/PullRequestServiceSpec.kt", "w") as f:
    f.write(text)
