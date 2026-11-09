package rain.admin.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AdminUserSeederTest {

    @Test
    void randomPasswordIs16CharsFromSafeAlphabet() {
        for (int i = 0; i < 20; i++) {
            String p = AdminUserSeeder.randomPassword();
            assertEquals(16, p.length());
            assertTrue(p.chars().allMatch(c -> "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789".indexOf(c) >= 0),
                    "含意外字符: " + p);
        }
    }

    @Test
    void randomPasswordIsNotConstant() {
        assertTrue(!AdminUserSeeder.randomPassword().equals(AdminUserSeeder.randomPassword()));
    }
}
