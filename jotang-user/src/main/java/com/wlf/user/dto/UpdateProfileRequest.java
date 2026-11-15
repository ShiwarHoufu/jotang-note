package com.wlf.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 改资料入参：昵称 + 学院。见《概要设计》§5.2
 *
 * <p><b>本接口是全量替换（PUT）。</b>不过这里的「全量」是退化的——两个字段都必填，
 * 没有哪个可选字段能被「没传 = 清空」（对比 {@code NoteUpdateRequest}，那里
 * {@code summary} / {@code teacher} / {@code tags} 都是可空且传 null 即抹掉）。
 * 前端资料表单本就是回填昵称与学院再整体提交，所以直接要求两者都在，比引入一套
 * 「可空字段」的语义简单得多。
 *
 * <p><b>不含头像，而且不能加。</b>头像有独立的 {@code POST /api/users/me/avatar}：
 * 它要走 multipart 与 OSS 上传，形状与本请求体完全不同。塞一个 {@code avatar} 字段进来
 * 只会得到一个被静默忽略的入参——本项目不会经由此接口清空或设置头像。
 *
 * <p>两个字段的校验文案都照抄既有类：{@code collegeId} 的与 {@code RegisterRequest} 一字不差
 * （{@code AuthControllerTest} 断言了那个字符串），{@code nickname} 的措辞同
 * {@code NoteUpdateRequest} 的「不超过 N 个字符」。
 */
@Getter
@Setter
public class UpdateProfileRequest {

    /**
     * 昵称，不校验唯一性。
     * 昵称上没有唯一约束，重名是允许的（它是展示用的名字，不是登录凭证）。
     *
     * <p>超长由本注解拦下并给出「哪个字段、为什么」的 40001，而不是撞库后变成 50000——
     * 与 {@code NoteUpdateRequest} 照抄列宽的理由相同。
     */
    @NotBlank(message = "昵称不能为空")
    @Size(max = 32, message = "昵称不超过 32 个字符")
    private String nickname;

    /**
     * 学院 id，只校验非空；「这个 id 是否真实存在」由{@code UserService} 查库判断，好给出带字段明细的 40001。
     *
     * <p><b>刻意不加 {@code @Positive}</b>：{@code RegisterRequest} 也没有，加了会让同一个字段
     * 出现第二种文案形状。{@code 0} / 负数会被 {@code CollegeService#exists} 判否，
     * 照常落到「学院不存在」。
     */
    @NotNull(message = "学院不能为空")
    private Long collegeId;
}
