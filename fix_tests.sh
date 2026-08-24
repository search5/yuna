#!/bin/bash
sed -i 's/val accessControl = mockk<AccessControl>()/val accessControl = mockk<AccessControl>(relaxed=true)/g' src/test/kotlin/com/github/search5/yona/web/OrganizationViewControllerMoreSpec.kt

sed -i '/every { organizationService.updateOrganizationSettings/i \
            every { accessControl.isAllowed(any<User>(), any<Organization>(), any<Operation>()) } returns true' src/test/kotlin/com/github/search5/yona/web/OrganizationViewControllerMoreSpec.kt

sed -i 's/val pullRequestRepository = mockk<PullRequestRepository>()/val pullRequestRepository = mockk<PullRequestRepository>(relaxed=true)/g' src/test/kotlin/com/github/search5/yona/web/PullRequestViewControllerMoreSpec.kt
