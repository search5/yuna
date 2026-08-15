package com.github.search5.yona.domain.board

import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.user.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import com.github.search5.yona.domain.attachment.AttachmentService
import com.github.search5.yona.domain.enumeration.ResourceType

@Service
@Transactional(readOnly = true)
class PostingServiceImpl(
    private val postingRepository: PostingRepository,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val attachmentService: AttachmentService,
    private val postingCommentRepository: PostingCommentRepository
) : PostingService {

    override fun getPostings(projectId: Long, pageable: Pageable): Page<Posting> {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("프로젝트를 찾을 수 없습니다.") }
        return postingRepository.findByProject(project, pageable)
    }

    override fun getNotices(projectId: Long): List<Posting> {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("프로젝트를 찾을 수 없습니다.") }
        return postingRepository.findByProjectAndNotice(project, true)
    }

    override fun getPosting(projectId: Long, number: Long): Posting? {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("프로젝트를 찾을 수 없습니다.") }
        return postingRepository.findByProjectAndNumber(project, number)
    }

    @Transactional
    override fun createPosting(projectId: Long, posting: Posting, authorId: Long): Posting {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("프로젝트를 찾을 수 없습니다.") }
        val author = userRepository.findById(authorId)
            .orElseThrow { IllegalArgumentException("사용자를 찾을 수 없습니다.") }

        // 일련번호 증가 및 프로젝트 영속화
        project.lastPostingNumber = project.lastPostingNumber + 1
        projectRepository.save(project)

        posting.project = project
        posting.number = project.lastPostingNumber
        posting.authorId = author.id
        posting.authorLoginId = author.loginId
        posting.authorName = author.name
        posting.createdDate = Instant.now()
        posting.updatedDate = Instant.now()

        return postingRepository.save(posting)
    }

    @Transactional
    override fun updatePosting(
        projectId: Long,
        number: Long,
        title: String,
        body: String,
        notice: Boolean,
        readme: Boolean,
        authorId: Long
    ): Posting {
        val posting = getPosting(projectId, number)
            ?: throw IllegalArgumentException("포스팅을 찾을 수 없습니다.")

        posting.title = title.trim()
        posting.body = body
        posting.notice = notice
        posting.readme = readme
        posting.updatedDate = Instant.now()

        return postingRepository.save(posting)
    }

    @Transactional
    override fun deletePosting(projectId: Long, number: Long, authorId: Long) {
        val posting = getPosting(projectId, number)
            ?: throw IllegalArgumentException("포스팅을 찾을 수 없습니다.")
        
        // 연관된 댓글의 첨부파일도 일괄 삭제
        val comments = postingCommentRepository.findByPostingIdOrderByCreatedDateAsc(posting.id!!)
        for (comment in comments) {
            attachmentService.deleteAll(ResourceType.NONISSUE_COMMENT, comment.id.toString())
        }

        attachmentService.deleteAll(ResourceType.BOARD_POST, posting.id.toString())
        postingRepository.delete(posting)
    }
}
