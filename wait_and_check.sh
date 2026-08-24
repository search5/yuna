#!/bin/bash
while true; do
  ps -ef | grep gradle | grep -v grep > /dev/null
  if [ $? -ne 0 ]; then
    break
  fi
  sleep 5
done

echo "All gradle tasks finished."
./gradlew test --tests '*PullRequestServiceSpec*' --tests '*PullRequestViewControllerSpec*' --tests '*BareCommitSpec*' --tests '*UserServiceImplSpec*' --tests '*TranslationServiceImplSpec*' jacocoTestReport
echo "TranslationServiceImpl coverage:"
python3 parse_cov.py < build/reports/jacoco/test/html/com.github.search5.yona.domain.support/TranslationServiceImpl.html | grep -E "Total|Element"
echo "BareCommit coverage:"
python3 parse_cov.py < build/reports/jacoco/test/html/com.github.search5.yona.domain.vcs/BareCommit.html | grep -E "Total|Element"
echo "UserServiceImpl coverage:"
python3 parse_cov.py < build/reports/jacoco/test/html/com.github.search5.yona.domain.user/UserServiceImpl.html | grep -E "Total|Element"
echo "PullRequestServiceImpl coverage:"
python3 parse_cov.py < build/reports/jacoco/test/html/com.github.search5.yona.domain.pullrequest/PullRequestServiceImpl.html | grep -E "Total|Element"
echo "PullRequestViewController coverage:"
python3 parse_cov.py < build/reports/jacoco/test/html/com.github.search5.yona.web/PullRequestViewController.html | grep -E "Total|Element"
