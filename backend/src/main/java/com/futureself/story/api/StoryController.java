package com.futureself.story.api;

import com.futureself.auth.CurrentUser;
import com.futureself.common.api.ApiResponse;
import com.futureself.generation.GenerationTaskService;
import com.futureself.story.StoryService;
import com.futureself.story.api.StoryResponse;
import com.futureself.user.UserEventService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 故事详情/列表/收藏/删除与音频重试（实施文档 §12.5）。列表/收藏/删除随 Phase 7。
 */
@RestController
@RequestMapping("/api/v1")
public class StoryController {

    private final StoryService storyService;
    private final GenerationTaskService taskService;
    private final UserEventService userEventService;

    public StoryController(StoryService storyService, GenerationTaskService taskService,
                           UserEventService userEventService) {
        this.storyService = storyService;
        this.taskService = taskService;
        this.userEventService = userEventService;
    }

    @GetMapping("/stories/{id}")
    public ApiResponse<StoryResponse> detail(@AuthenticationPrincipal CurrentUser currentUser,
                                             @PathVariable("id") long id) {
        StoryResponse response = storyService.getOwnStory(id, currentUser.userId());
        userEventService.record(currentUser.userId(), UserEventService.EVENT_STORY_OPENED, "STORY", id, null);
        return ApiResponse.ok(response);
    }

    /** 故事列表分页（§12.5）：favorite=true 仅收藏。 */
    @GetMapping("/stories")
    public ApiResponse<StoryService.StoryPage> list(@AuthenticationPrincipal CurrentUser currentUser,
                                                    @RequestParam(value = "page", defaultValue = "0") int page,
                                                    @RequestParam(value = "size", defaultValue = "20") int size,
                                                    @RequestParam(value = "favorite", defaultValue = "false") boolean favorite) {
        return ApiResponse.ok(storyService.list(currentUser.userId(), page, size, favorite));
    }

    /** 收藏/取消收藏（§12.5）。 */
    @PutMapping("/stories/{id}/favorite")
    public ApiResponse<Map<String, Object>> favorite(@AuthenticationPrincipal CurrentUser currentUser,
                                                     @PathVariable("id") long id,
                                                     @RequestBody(required = false) Map<String, Boolean> body) {
        boolean favorite = body == null || body.getOrDefault("favorite", true);
        Map<String, Object> result = storyService.setFavorite(id, currentUser.userId(), favorite);
        if (favorite) {
            userEventService.record(currentUser.userId(), UserEventService.EVENT_STORY_FAVORITED, "STORY", id, null);
        }
        return ApiResponse.ok(result);
    }

    /** 软删除（§32.8）。 */
    @DeleteMapping("/stories/{id}")
    public ApiResponse<Map<String, Object>> delete(@AuthenticationPrincipal CurrentUser currentUser,
                                                   @PathVariable("id") long id) {
        return ApiResponse.ok(storyService.deleteOwn(id, currentUser.userId()));
    }

    /** 仅重试音频（§20.4：文本完成、音频失败的任务）。 */
    @PostMapping("/stories/{id}/retry-audio")
    public ApiResponse<Map<String, Object>> retryAudio(@AuthenticationPrincipal CurrentUser currentUser,
                                                       @PathVariable("id") long id) {
        return ApiResponse.ok(taskService.retryAudio(currentUser.userId(), id));
    }
}
