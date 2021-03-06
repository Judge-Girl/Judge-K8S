package tw.waterball.judgegirl.springboot.student.password;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import tw.waterball.judgegirl.studentservice.password.PasswordEncoder;

/**
 * @author - johnny850807@gmail.com (Waterball)
 */
@Component
@AllArgsConstructor
public class SpringBootPasswordEncoderAdapter implements PasswordEncoder {
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Override
    public String encode(CharSequence rawPassword) {
        if (rawPassword.toString().isBlank()) {
            return "";
        }
        return passwordEncoder.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence raw, String encoded) {
        return passwordEncoder.matches(raw, encoded);
    }
}
