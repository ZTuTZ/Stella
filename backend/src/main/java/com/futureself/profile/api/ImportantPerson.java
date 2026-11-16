package com.futureself.profile.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 重要人物（实施文档 §5.2 步骤 B）。
 */
public record ImportantPerson(
        @NotBlank @Size(min = 1, max = 40) String name,
        @NotBlank @Size(min = 1, max = 40) String relation,
        @Size(max = 200) String note) {
}
