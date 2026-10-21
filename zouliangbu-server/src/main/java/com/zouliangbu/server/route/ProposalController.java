package com.zouliangbu.server.route;

import com.zouliangbu.server.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 社区提案公开接口（无需登录）。
 *
 * 免登录是刻意的：开源项目里"注册才能贡献"会把贡献者挡在门外。
 * 身份用客户端生成的设备 UUID（proposerUuid / voterKey），昵称可留空。
 *
 * 提交人 = 自己走过的人或转贴别人链接的人；投票人 = 任何愿意确认的人。
 * 机器能判的（轨迹是否合规）由规则层直接拒；机器判不了的才进人工队列。
 */
@RestController
@RequestMapping("/api/v1/proposals")
public class ProposalController {

    private final ProposalService proposalService;

    public ProposalController(ProposalService proposalService) {
        this.proposalService = proposalService;
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    /** 提交提案（IMPORT 外部导入 / UPLOAD 自有轨迹 / EDIT 线路勘误）。 */
    @PostMapping
    public ResponseEntity<ApiResponse<ProposalRow>> submit(@RequestBody SubmitRequest req) {
        try {
            ProposalService.SubmitCommand cmd = new ProposalService.SubmitCommand(
                    req.type(), req.category(), req.targetRouteId(), req.sourceUrl(),
                    req.payloadJson(), req.baseVersion(), req.reason(),
                    req.proposerUuid(), req.proposerName(), req.proposerUserId());
            ContentProposalEntity p = proposalService.submit(cmd);
            return ok(ProposalRow.of(p));
        } catch (IllegalArgumentException e) {
            return ApiResponse.failResponse(ApiResponse.ErrorCode.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.failResponse(ApiResponse.ErrorCode.BAD_REQUEST, e.getMessage());
        }
    }

    /** 提案列表。 */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProposalRow>>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long routeId,
            @RequestParam(defaultValue = "50") int limit) {
        List<ProposalRow> rows = proposalService.list(status, routeId, limit)
                .stream().map(ProposalRow::of).toList();
        return ok(rows);
    }

    /** 提案详情（含当前认票数）。 */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProposalRow>> get(@PathVariable long id) {
        ContentProposalEntity p = proposalService.get(id);
        if (p == null) {
            return ApiResponse.failResponse(ApiResponse.ErrorCode.NOT_FOUND, "提案不存在");
        }
        return ok(ProposalRow.of(p));
    }

    /** 投票（1 认可 / -1 反对，可改票）。客观勘误达阈值且 0 反对 → 自动合并成新版本。 */
    @PostMapping("/{id}/vote")
    public ResponseEntity<ApiResponse<ProposalService.VoteResult>> vote(
            @PathVariable long id, @RequestBody VoteRequest req) {
        try {
            return ok(proposalService.vote(id, req.voterKey(), req.value()));
        } catch (IllegalArgumentException e) {
            return ApiResponse.failResponse(ApiResponse.ErrorCode.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.failResponse(ApiResponse.ErrorCode.BAD_REQUEST, e.getMessage());
        }
    }

    // ===== DTO =====

    record SubmitRequest(String type, String category, Long targetRouteId, String sourceUrl,
                         String payloadJson, Integer baseVersion, String reason,
                         String proposerUuid, String proposerName, Long proposerUserId) {}

    record VoteRequest(String voterKey, int value) {}

    /** 提案对外结构（字段名与 App 端 model 保持一致）。 */
    public record ProposalRow(long id, String type, String category, Long targetRouteId,
                              String sourceUrl, String reason, String proposerName,
                              String status, int votesUp, int votesDown, int threshold,
                              Long routeId, Integer mergedVersion, String rejectReason,
                              Integer baseVersion, String createdAt) {
        public static ProposalRow of(ContentProposalEntity p) {
            return new ProposalRow(
                    p.getId(), p.getType(), p.getCategory(), p.getTargetRouteId(),
                    p.getSourceUrl() == null ? "" : p.getSourceUrl(),
                    p.getReason() == null ? "" : p.getReason(),
                    p.getProposerName() == null ? "" : p.getProposerName(),
                    p.getStatus(),
                    p.getVotesUp() == null ? 0 : p.getVotesUp(),
                    p.getVotesDown() == null ? 0 : p.getVotesDown(),
                    p.getThreshold() == null ? 2 : p.getThreshold(),
                    p.getRouteId(), p.getMergedVersion(),
                    p.getRejectReason() == null ? "" : p.getRejectReason(),
                    p.getBaseVersion(),
                    p.getCreatedAt() == null ? "" : p.getCreatedAt().toString());
        }
    }
}
