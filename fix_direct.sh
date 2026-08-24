#!/bin/bash
sed -i 's/organizationLogo("testorg")/organizationLogo("testorg")/g' src/test/kotlin/com/github/search5/yona/web/DirectControllerCoverageSpec.kt
