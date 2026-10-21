package com.zouliangbu.server.route;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 提案投票（一人一票，可改票：唯一索引 proposal_id + voter_key）。 */
@TableName("content_vote")
public class ContentVoteEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long proposalId;
    private String voterKey;
    private Integer value;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Long getProposalId() { return proposalId; }
    public void setProposalId(Long v) { this.proposalId = v; }
    public String getVoterKey() { return voterKey; }
    public void setVoterKey(String v) { this.voterKey = v; }
    public Integer getValue() { return value; }
    public void setValue(Integer v) { this.value = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
}
