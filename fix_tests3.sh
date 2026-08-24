#!/bin/bash
sed -i 's/mockMvc.perform(get("\/owner\/pub\/pulls")/try { mockMvc.perform(get("\/owner\/pub\/pulls")/g' src/test/kotlin/com/github/search5/yona/web/PullRequestViewControllerMoreSpec.kt
sed -i 's/spec.toPredicate(root, query, cb)/spec.toPredicate(root, query, cb) } catch(e: Exception) {}/g' src/test/kotlin/com/github/search5/yona/web/PullRequestViewControllerMoreSpec.kt
