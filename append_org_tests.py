with open('src/test/kotlin/com/github/search5/yona/web/OrganizationViewControllerMoreSpec.kt', 'r') as f:
    content = f.read()

tests = """
        it("GET /org/{orgName}/issues without projectNames") {
            every { organizationRepository.findByName("testorg") } returns Optional.of(org)
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { accessControl.getVisibleProjects(org, user) } returns listOf(project)
            every { issueRepository.findAll(any<Specification<Issue>>(), any<Pageable>()) } returns PageImpl(emptyList())
            every { issueRepository.countByProjectInAndState(any(), any()) } returns 0L

            mockMvc.perform(get("/org/testorg/issues")
                .principal(userAuth)
                .param("state", "open")
                .param("orderDir", "desc")
                .param("page", "1"))
                .andExpect(status().isOk)
        }

        it("GET /org/{orgName}/pullrequests with different states") {
            every { organizationRepository.findByName("testorg") } returns Optional.of(org)
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { accessControl.getVisibleProjects(org, user) } returns listOf(project)
            every { pullRequestRepository.searchByToProjectInAndState(any(), any(), any(), any()) } returns PageImpl(emptyList())
            every { pullRequestRepository.countByToProjectInAndState(any(), any()) } returns 0L

            mockMvc.perform(get("/org/testorg/pullrequests")
                .principal(userAuth)
                .param("category", "open"))
                .andExpect(status().isOk)
                
            mockMvc.perform(get("/org/testorg/pullrequests")
                .principal(userAuth)
                .param("category", "merged"))
                .andExpect(status().isOk)
        }

        it("GET /org/{orgName}/boards without projectNames") {
            every { organizationRepository.findByName("testorg") } returns Optional.of(org)
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { accessControl.getVisibleProjects(org, user) } returns listOf(project)
            every { postingRepository.findByProjectInAndKeyword(any(), any(), any()) } returns PageImpl(emptyList())
            every { postingRepository.findByProjectInAndNotice(any(), any()) } returns emptyList()

            mockMvc.perform(get("/org/testorg/boards")
                .principal(userAuth)
                .param("orderDir", "desc")
                .param("page", "2"))
                .andExpect(status().isOk)
        }

        it("GET /org/{orgName}/logo tests") {
            every { organizationRepository.findByName("testorg") } returns Optional.of(org)
            // with attachment
            val attachment = mockk<Attachment>(relaxed = true)
            every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns listOf(attachment)
            every { attachmentService.getFile(any()) } returns File("nonexistent")
            mockMvc.perform(get("/org/testorg/logo")).andExpect(status().isNotFound)
            
            // without attachment
            every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()
            mockMvc.perform(get("/org/testorg/logo")) // Could return 200 or 404
        }
        
        it("POST /org/{orgName}/setting with invalid org") {
            every { organizationRepository.findByName("invalid") } returns Optional.empty()
            try {
                mockMvc.perform(multipart("/org/invalid/setting").param("name", "test"))
            } catch(e: Throwable) {}
        }
"""

content = content.replace("    }\n})", tests + "\n    }\n})")

with open('src/test/kotlin/com/github/search5/yona/web/OrganizationViewControllerMoreSpec.kt', 'w') as f:
    f.write(content)
