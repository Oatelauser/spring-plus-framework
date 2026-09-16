package io.github.oatelauser.springplus.example.controller;

import io.github.oatelauser.springplus.governor.annotation.Idempotent;
import io.github.oatelauser.springplus.governor.annotation.RepeatSubmit;
import io.github.oatelauser.springplus.web.response.SimpleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 幂等与重复提交测试 Controller。
 */
@RestController
@Tag(name = "幂等与重复提交测试")
@RequiredArgsConstructor
@RequestMapping("/idempotent-test")
public class IdempotentController {

    @PostMapping("/idempotent")
    @Operation(summary = "测试 @Idempotent")
    @Idempotent(window = 60)
    public SimpleResponse<String> idempotent() {
        return SimpleResponse.ok("idempotent:" + UUID.randomUUID());
    }

    @PostMapping("/repeat-submit")
    @Operation(summary = "测试 @RepeatSubmit")
    @RepeatSubmit(window = 5)
    public SimpleResponse<String> repeatSubmit() {
        return SimpleResponse.ok("repeat-submit:" + UUID.randomUUID());
    }
}
