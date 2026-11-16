package com.futureself.story.api;

import com.futureself.auth.CurrentUser;
import com.futureself.common.api.ApiResponse;
import com.futureself.story.StoryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 故事详情（实施文档 §12.5）。列表/收藏/删除随 Phase 7。
 */
@RestController
@RequestMapping("/api/v1")
public class StoryController {

    private final StoryService storyService;

    public StoryController(StoryService storyService) {
        this.storyService = storyService;
    }

    @GetMapping("/stories/{id}")
    public ApiResponse<StoryResponse> detail(@AuthenticationPrincipal CurrentUser currentUser,
                                             @PathVariable("id") long id) {
        return ApiResponse.ok(storyService.getOwnStory(id, currentUser.userId()));
    }
}
