package com.zouliangbu.server.route;

import com.zouliangbu.server.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理后台的提案接口（/api/v1/admin/** 由 AdminAuthInterceptor 拦）。
 *
 * 管理员只处理"机器判不了"的三类：外部导入合规、主观调整、版本冲突/隐私。
 * 客观勘误通常已经自动合并，这里只是抽查与回滚。
 */
@RestController
@RequestMapping("/api/v1/admin/proposals")
public class AdminProposalController {

    private final ProposalService proposalService;

    public AdminProposalController(ProposalService proposalService) {
        this.proposalService = proposalService;
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    /** 提案列表。 */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProposalController.ProposalRow>>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long routeId,
            @RequestParam(defaultValue = "100") int limit) {
        List<ProposalController.ProposalRow> rows = proposalService.list(status, routeId, limit)
                .stream().map(ProposalController.ProposalRow::of).toList();
        return ok(rows);
    }

    /** 批准（导入抓取入库 / 上传建线 / 勘误合并）。 */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<Long>> approve(@PathVariable long id) {
        try {
            return ok(proposalService.approve(id, "admin"));
        } catch (IllegalArgumentException e) {
            return ApiResponse.failResponse(ApiResponse.ErrorCode.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.failResponse(ApiResponse.ErrorCode.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.failResponse(ApiResponse.ErrorCode.SERVER_ERROR, "处理失败：" + e.getMessage());
        }
    }

    /** 驳回。 */
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<Void>> reject(@PathVariable long id, @RequestBody RejectRequest req) {
        if (proposalService.get(id) == null) {
            return ApiResponse.failResponse(ApiResponse.ErrorCode.NOT_FOUND, "提案不存在");
        }
        proposalService.reject(id, req.reason());
        return ok(null);
    }

    /** 线路版本列表（新→旧）。 */
    @GetMapping("/routes/{routeId}/revisions")
    public ResponseEntity<ApiResponse<List<RevisionRow>>> revisions(@PathVariable long routeId) {
        List<RevisionRow> rows = proposalService.revisions(routeId).stream()
                .map(r -> new RevisionRow(r.getId(), r.getRouteId(), r.getVersion(),
                        r.getProposerName() == null ? "" : r.getProposerName(),
                        r.getReason() == null ? "" : r.getReason(),
                        r.getCreatedAt() == null ? "" : r.getCreatedAt().toString()))
                .toList();
        return ok(rows);
    }

    /** 回滚到历史版本（版本号继续 +1，保持单调）。 */
    @PostMapping("/routes/{routeId}/rollback")
    public ResponseEntity<ApiResponse<Integer>> rollback(@PathVariable long routeId,
                                                         @RequestBody RollbackRequest req) {
        try {
            return ok(proposalService.rollback(routeId, req.version(), "admin"));
        } catch (IllegalArgumentException e) {
            return ApiResponse.failResponse(ApiResponse.ErrorCode.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.failResponse(ApiResponse.ErrorCode.BAD_REQUEST, e.getMessage());
        }
    }

    record RejectRequest(String reason) {}

    record RollbackRequest(int version) {}

    record RevisionRow(long id, long routeId, int version, String proposerName,
                       String reason, String createdAt) {}
}
