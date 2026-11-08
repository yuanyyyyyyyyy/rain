package rain.admin.auth;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** 签发-校验闭环 + 两个经典失败路径（密钥不同的伪造、过期） */
class JwtServiceTest {

    @Test
    void issueThenVerifyRoundTrip() {
        JwtService svc = new JwtService("test-secret-key-0123456789abcdef-0123456789abcdef", 120);
        String token = svc.issue("admin");
        assertEquals("admin", svc.verify(token));
    }

    @Test
    void forgedTokenFromDifferentSecretFails() {
        JwtService a = new JwtService("secret-A-0123456789abcdef0123456789abcdef", 120);
        JwtService b = new JwtService("secret-B-0123456789abcdef0123456789abcdef", 120);
        // verify 吞掉异常统一返回 null，由过滤器据此不设置认证
        assertNull(b.verify(a.issue("admin")));
    }

    @Test
    void expiredTokenFails() throws InterruptedException {
        JwtService svc = new JwtService("test-secret-key-0123456789abcdef-0123456789abcdef", 0);
        String token = svc.issue("admin");
        Thread.sleep(50);
        assertNull(svc.verify(token));
    }

    @Test
    void garbageTokenFails() {
        JwtService svc = new JwtService("test-secret-key-0123456789abcdef-0123456789abcdef", 120);
        assertNull(svc.verify("not-a-token"));
        assertNull(svc.verify(""));
    }
}
