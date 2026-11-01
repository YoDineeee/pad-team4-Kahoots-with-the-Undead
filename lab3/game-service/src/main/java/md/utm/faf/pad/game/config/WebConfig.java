package md.utm.faf.pad.game.config;

import md.utm.faf.pad.game.security.AuthInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    /** Plug our JWT check into every /api/** request. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/**");
    }

    /**
     * The game is all about time (cycles, timed actions). Instead of calling Instant.now()
     * everywhere, services ask this Clock. In unit tests we can pass a fixed/fake Clock
     * and "fast-forward" time without waiting 10 real minutes.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
