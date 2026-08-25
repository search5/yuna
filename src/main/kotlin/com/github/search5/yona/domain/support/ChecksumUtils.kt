package com.github.search5.yona.domain.support

import java.security.MessageDigest

// yona IssueApi.java:538-548 isModifiedByOthers() 대응 (P1-102). 이슈 본문/댓글 인라인 수정 시 [GL-controllers_api_IssueApi-029]
// 동시편집 충돌을 감지하는 데 쓰인다.
fun sha1Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

// 캐리지 리턴 문자 차이로 인한 오탐을 막기 위해 \r을 제거하고 trim한 뒤 SHA-1로 비교한다.
fun isModifiedByOthers(current: String, fromView: String): Boolean {
    val currentChecksum = sha1Hex(current.replace("\r", "").trim())
    val fromViewChecksum = sha1Hex(fromView.replace("\r", "").trim())
    return currentChecksum != fromViewChecksum
}
