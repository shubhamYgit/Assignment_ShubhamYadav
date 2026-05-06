package org.shubh.assignment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "comments")
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    private Comment parentComment;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_type", nullable = false, length = 16)
    private AuthorType authorType;

    @Column(nullable = false, length = 5000)
    private String content;

    @Column(name = "depth_level", nullable = false)
    private int depthLevel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Comment() {
    }

    public Comment(Post post, Comment parentComment, Long authorId, AuthorType authorType, String content, int depthLevel) {
        this.post = post;
        this.parentComment = parentComment;
        this.authorId = authorId;
        this.authorType = authorType;
        this.content = content;
        this.depthLevel = depthLevel;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Post getPost() {
        return post;
    }

    public Comment getParentComment() {
        return parentComment;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public AuthorType getAuthorType() {
        return authorType;
    }

    public String getContent() {
        return content;
    }

    public int getDepthLevel() {
        return depthLevel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
