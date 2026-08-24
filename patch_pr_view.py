import sys

with open("src/test/kotlin/com/github/search5/yona/web/PullRequestViewControllerSpec.kt", "r") as f:
    lines = f.readlines()

# Find the last "})"
last_idx = -1
for i, line in enumerate(lines):
    if line.strip() == "})":
        last_idx = i

if last_idx != -1:
    new_test = """
        describe("Coverage addition for PullRequestViewController") {
            it("should handle null states, null filter, null contributorId in listPullRequests") {
                val memberUser = com.github.search5.yona.domain.user.User(id = 10L, loginId = "testuser", name = "테스트유저")
                val project = com.github.search5.yona.domain.project.Project(id = 1L, name = "TestProj", owner = "owner", projectScope = com.github.search5.yona.domain.project.ProjectScope.PRIVATE)
                memberUser.projectUsers.add(com.github.search5.yona.domain.project.ProjectUser(id = 900L, user = memberUser, project = project, role = com.github.search5.yona.domain.role.Role(id = com.github.search5.yona.domain.role.RoleType.MEMBER.roleType)))
                
                io.mockk.every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns java.util.Optional.of(project)
                io.mockk.every { userRepository.findByLoginId("testuser") } returns java.util.Optional.of(memberUser)
                io.mockk.every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                io.mockk.every {
                    pullRequestRepository.findAll(any<org.springframework.data.jpa.domain.Specification<com.github.search5.yona.domain.pullrequest.PullRequest>>(), any<org.springframework.data.domain.Pageable>())
                } returns org.springframework.data.domain.PageImpl(emptyList<com.github.search5.yona.domain.pullrequest.PullRequest>(), org.springframework.data.domain.PageRequest.of(0, 20), 0)
                
                // state=all (states=null), filter=null, contributorId=null
                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/owner/TestProj/pulls")
                    .param("state", "all")
                    .principal(org.springframework.security.authentication.UsernamePasswordAuthenticationToken("testuser", "password")))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk)
            }
            
            it("should handle getReferredIssues with empty body") {
                val memberUser = com.github.search5.yona.domain.user.User(id = 10L, loginId = "testuser", name = "테스트유저")
                val project = com.github.search5.yona.domain.project.Project(id = 1L, name = "TestProj", owner = "owner", projectScope = com.github.search5.yona.domain.project.ProjectScope.PRIVATE)
                memberUser.projectUsers.add(com.github.search5.yona.domain.project.ProjectUser(id = 900L, user = memberUser, project = project, role = com.github.search5.yona.domain.role.Role(id = com.github.search5.yona.domain.role.RoleType.MEMBER.roleType)))
                
                val prWithEmptyBody = com.github.search5.yona.domain.pullrequest.PullRequest(
                    id = 50L, title = "PR tests", body = null, toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature", contributor = memberUser, state = com.github.search5.yona.domain.enumeration.State.OPEN, number = 1L
                )
                
                io.mockk.every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns java.util.Optional.of(project)
                io.mockk.every { userRepository.findByLoginId("testuser") } returns java.util.Optional.of(memberUser)
                io.mockk.every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                io.mockk.every { pullRequestService.getPullRequest(1L, 1L) } returns prWithEmptyBody
                io.mockk.every { pullRequestService.attemptMerge(50L) } returns com.github.search5.yona.domain.pullrequest.PullRequestMergeResult(pullRequest = prWithEmptyBody)
                io.mockk.every { commentThreadRepository.findByPullRequest(prWithEmptyBody) } returns emptyList()
                io.mockk.every { pullRequestCommitRepository.findByPullRequest(prWithEmptyBody) } returns emptyList()
                
                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/owner/TestProj/pull/1")
                    .principal(org.springframework.security.authentication.UsernamePasswordAuthenticationToken("testuser", "password")))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk)
            }
            
            it("should handle viewPullRequest$lambda$5(Attachment) with null fields") {
                val memberUser = com.github.search5.yona.domain.user.User(id = 10L, loginId = "testuser", name = "테스트유저")
                val project = com.github.search5.yona.domain.project.Project(id = 1L, name = "TestProj", owner = "owner", projectScope = com.github.search5.yona.domain.project.ProjectScope.PRIVATE)
                memberUser.projectUsers.add(com.github.search5.yona.domain.project.ProjectUser(id = 900L, user = memberUser, project = project, role = com.github.search5.yona.domain.role.Role(id = com.github.search5.yona.domain.role.RoleType.MEMBER.roleType)))
                val pr = com.github.search5.yona.domain.pullrequest.PullRequest(
                    id = 50L, title = "PR test", body = "body", toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature", contributor = memberUser, state = com.github.search5.yona.domain.enumeration.State.OPEN, number = 1L
                )
                
                io.mockk.every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns java.util.Optional.of(project)
                io.mockk.every { userRepository.findByLoginId("testuser") } returns java.util.Optional.of(memberUser)
                io.mockk.every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                io.mockk.every { pullRequestService.getPullRequest(1L, 1L) } returns pr
                io.mockk.every { pullRequestService.attemptMerge(50L) } returns com.github.search5.yona.domain.pullrequest.PullRequestMergeResult(pullRequest = pr)
                io.mockk.every { commentThreadRepository.findByPullRequest(pr) } returns emptyList()
                io.mockk.every { pullRequestCommitRepository.findByPullRequest(pr) } returns emptyList()
                
                val attachment = com.github.search5.yona.domain.attachment.Attachment(
                    id = null, mimeType = null, name = "test.txt", size = null, containerType = com.github.search5.yona.domain.enumeration.ResourceType.PULL_REQUEST, containerId = "50"
                )
                io.mockk.every { attachmentRepository.findByContainerTypeAndContainerId(com.github.search5.yona.domain.enumeration.ResourceType.PULL_REQUEST, "50") } returns listOf(attachment)
                
                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/owner/TestProj/pull/1")
                    .principal(org.springframework.security.authentication.UsernamePasswordAuthenticationToken("testuser", "password")))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk)
            }
            
            it("should return false when isManagerOrContributor is called for non-manager") {
                val memberUser = com.github.search5.yona.domain.user.User(id = 10L, loginId = "testuser", name = "테스트유저")
                val project = com.github.search5.yona.domain.project.Project(id = 1L, name = "TestProj", owner = "owner", projectScope = com.github.search5.yona.domain.project.ProjectScope.PRIVATE)
                val memberRole = com.github.search5.yona.domain.role.Role(id = com.github.search5.yona.domain.role.RoleType.MEMBER.roleType)
                memberUser.projectUsers.add(com.github.search5.yona.domain.project.ProjectUser(id = 900L, user = memberUser, project = project, role = memberRole))
                
                val pr = com.github.search5.yona.domain.pullrequest.PullRequest(
                    id = 50L, title = "PR test", body = "body", toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature", 
                    contributor = com.github.search5.yona.domain.user.User(id = 20L, loginId = "other", name = "other"), 
                    state = com.github.search5.yona.domain.enumeration.State.OPEN, number = 1L
                )
                
                io.mockk.every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns java.util.Optional.of(project)
                io.mockk.every { userRepository.findByLoginId("testuser") } returns java.util.Optional.of(memberUser)
                io.mockk.every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                io.mockk.every { pullRequestService.getPullRequest(1L, 1L) } returns pr
                io.mockk.every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns java.util.Optional.of(com.github.search5.yona.domain.project.ProjectUser(id = 900L, user = memberUser, project = project, role = memberRole))
                
                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/owner/TestProj/pull/1/edit")
                    .principal(org.springframework.security.authentication.UsernamePasswordAuthenticationToken("testuser", "password")))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden)
            }
            
        }
    """
    lines.insert(last_idx, new_test + "\n")
    with open("src/test/kotlin/com/github/search5/yona/web/PullRequestViewControllerSpec.kt", "w") as f:
        f.writelines(lines)
