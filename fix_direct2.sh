#!/bin/bash
sed -i 's/organizationLogo("testorg")/organizationLogo(1L)/g' src/test/kotlin/com/github/search5/yona/web/DirectControllerCoverageSpec.kt
