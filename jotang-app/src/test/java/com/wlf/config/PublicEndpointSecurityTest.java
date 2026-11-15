package com.wlf.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 放行名单的<b>清单式</b>验证：名单内全部匿名可达、名单外全部 40100。
 *
 * <p>逐个接口散着写的 {@code xxxRequiresLogin} 拼不出「名单本身对不对」——
 * 这里两端都从真实来源枚举，而不是抄一份人工清单：
 * 名单侧反射 {@code SecurityConfig.PUBLIC_ENDPOINTS}，全量路由侧问 {@link RequestMappingHandlerMapping}。
 * 于是往名单里加接口、或新写一个 controller 却忘了加鉴权断言，都会在这里露出来。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class PublicEndpointSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    /** 名单里的每一条匿名都打得通：不是 40100 */
    @Test
    void everyPublicEndpointIsReachableAnonymously() throws Exception {
        Map<String, Set<RequestMethod>> mappings = mappings();

        for (String path : publicEndpoints()) {
            Set<RequestMethod> methods = mappings.get(path);
            assertThat(methods).as("放行名单里的 %s 找不到对应路由，名单或路由必有一方写错了", path).isNotEmpty();

            MvcResult result = call(path, pick(methods));
            assertThat(result.getResponse().getStatus())
                    .as("%s 在放行名单里，匿名请求不该被拦成 401", path)
                    .isNotEqualTo(401);
        }
    }

    /**
     * 名单外的每一条路由匿名都必须是 40100。
     *
     * <p>路径模板里的 {@code {id}} 之类填成 {@code 1} 即可：鉴权失败发生在过滤器链，
     * 早于业务解析，参数非法与否无关。
     */
    @Test
    void everyOtherEndpointRejectsAnonymousRequests() throws Exception {
        Set<String> publicPaths = Set.of(publicEndpoints());
        Map<String, Set<RequestMethod>> mappings = mappings();

        Set<String> protectedPaths = new LinkedHashSet<>(mappings.keySet());
        protectedPaths.removeAll(publicPaths);
        protectedPaths.remove("/error");

        // 护栏：枚举不到路由时下面的循环零断言通过，那就成了空跑
        assertThat(protectedPaths)
                .as("一条受保护路由都没枚举到，这个测试等于没测")
                .hasSizeGreaterThan(10)
                .contains("/api/auth/me");

        for (String path : protectedPaths) {
            MvcResult result = call(concrete(path), pick(mappings.get(path)));
            assertThat(result.getResponse().getStatus())
                    .as("%s 不在放行名单里，匿名请求应当 401", path)
                    .isEqualTo(401);
            assertThat(result.getResponse().getContentAsString())
                    .as("%s 的拒绝响应应带 40100", path)
                    .contains("\"code\":40100");
        }
    }

    // ---- helpers ----------------------------------------------------------

    private MvcResult call(String path, RequestMethod method) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .request(HttpMethod.valueOf(method.name()), path))
                .andReturn();
    }

    /** 路径模板 → 可请求的具体路径：把 {xxx} 段填成 1 */
    private static String concrete(String path) {
        return path.replaceAll("\\{[^/}]+}", "1");
    }

    /** 路由允许多个方法时取一个即可（GET 优先，与具体业务无关，只为把请求发出去） */
    private static RequestMethod pick(Set<RequestMethod> methods) {
        return methods.contains(RequestMethod.GET) ? RequestMethod.GET : methods.iterator().next();
    }

    /** 全部路由 → 允许的方法集合 */
    private Map<String, Set<RequestMethod>> mappings() {
        Map<String, Set<RequestMethod>> byPattern = new LinkedHashMap<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            Set<String> patterns = info.getPathPatternsCondition().getPatternValues();
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String pattern : patterns) {
                byPattern.computeIfAbsent(pattern, k -> new LinkedHashSet<>()).addAll(methods);
            }
        }
        return byPattern;
    }

    /** 反射读 SecurityConfig 里的放行名单，避免测试抄一份、与生产漂移 */
    private static String[] publicEndpoints() {
        try {
            Field field = SecurityConfig.class.getDeclaredField("PUBLIC_ENDPOINTS");
            field.setAccessible(true);
            return (String[]) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("读不到 SecurityConfig.PUBLIC_ENDPOINTS", e);
        }
    }
}
