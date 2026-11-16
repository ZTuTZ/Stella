package com.futureself.storage;

import com.futureself.audio.domain.AudioAsset;
import com.futureself.audio.domain.AudioAssetRepository;
import com.futureself.story.domain.Story;
import com.futureself.story.domain.StoryRepository;
import com.futureself.user.domain.AppUser;
import com.futureself.user.UserDeletionService;
import com.futureself.user.domain.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 8 小尾巴验收：§25.2 音频物理清理 + §25.3 用户注销预留。
 */
@SpringBootTest
@ActiveProfiles("test")
class DataLifecycleTest {

    @Autowired
    private AudioCleanupJob cleanupJob;

    @Autowired
    private StoryRepository storyRepository;

    @Autowired
    private AudioAssetRepository audioAssetRepository;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private UserDeletionService userDeletionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void cleanupDeletesExpiredDeletedStoryAudio() throws Exception {
        AppUser user = new AppUser();
        user.setOpenid("cleanup-" + UUID.randomUUID());
        user.setDisplayName("cleanup");
        user = userRepository.save(user);

        Story story = new Story();
        story.setUserId(user.getId());
        story.setGoalId(0L);
        story.setTitle("待清理故事");
        story.setSummary("s");
        story.setContent("c");
        story.setProvider("mock");
        story.setModel("");
        story.setStatus(Story.STATUS_DELETED);
        Story saved = storyRepository.save(story);

        // 物理创建测试文件
        String key = "audio/" + user.getId() + "/" + saved.getId() + "/" + UUID.randomUUID() + ".wav";
        Path file = Path.of("target/test-media").resolve(key).toAbsolutePath().normalize();
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[]{1, 2, 3, 4});
        assertThat(Files.exists(file)).isTrue();

        AudioAsset asset = new AudioAsset();
        asset.setUserId(user.getId());
        asset.setStoryId(saved.getId());
        asset.setProvider("mock");
        asset.setStatus(AudioAsset.STATUS_READY);
        asset.setStorageKey(key);
        asset.setPublicUrl("/media/" + key);
        asset.setFormat("wav");
        audioAssetRepository.save(asset);

        // 把故事 updated_at 拨到保留期之前（默认 30 天）
        jdbcTemplate.update(
                "update story set updated_at = date_sub(now(), interval 40 day) where id = ?", saved.getId());

        cleanupJob.cleanup();

        assertThat(Files.exists(file)).isFalse();
        AudioAsset after = audioAssetRepository.findById(asset.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(AudioAsset.STATUS_SUPERSEDED);
        assertThat(after.getStorageKey()).isNull();
    }

    @Test
    void requestDeletionSoftDeletesEverything() {
        AppUser user = new AppUser();
        user.setOpenid("deletion-" + UUID.randomUUID());
        user.setDisplayName("待注销");
        user = userRepository.save(user);

        Story story = new Story();
        story.setUserId(user.getId());
        story.setGoalId(0L);
        story.setTitle("注销前故事");
        story.setSummary("s");
        story.setContent("c");
        story.setProvider("mock");
        story.setModel("");
        story.setStatus(Story.STATUS_ACTIVE);
        story.setModerationStatus(Story.MODERATION_PASSED);
        Story savedStory = storyRepository.save(story);

        var result = userDeletionService.requestDeletion(user.getId());

        assertThat(result.get("status")).isEqualTo("DISABLED");
        AppUser after = userRepository.findById(user.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo("DISABLED");
        assertThat(after.getDisplayName()).isEqualTo("已注销用户");

        Story storyAfter = storyRepository.findById(savedStory.getId()).orElseThrow();
        assertThat(storyAfter.getStatus()).isEqualTo(Story.STATUS_DELETED);
    }
}
