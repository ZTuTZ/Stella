package com.futureself.affirmation.api;

import com.futureself.affirmation.AffirmationService;
import com.futureself.auth.CurrentUser;
import com.futureself.common.api.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 每日肯定句（实施文档 §12.6）：当日不存在则服务端生成一次，幂等。
 */
@RestController
@RequestMapping("/api/v1")
public class AffirmationController {

    private final AffirmationService affirmationService;

    public AffirmationController(AffirmationService affirmationService) {
        this.affirmationService = affirmationService;
    }

    @GetMapping("/affirmations/today")
    public ApiResponse<AffirmationResponse> today(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(affirmationService.today(currentUser.userId()));
    }
}
