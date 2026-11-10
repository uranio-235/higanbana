package local.higanbana.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.time.Duration;
import java.util.UUID;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {
    @Bean RestClientCustomizer providerTimeouts(
            @org.springframework.beans.factory.annotation.Value("${app.nvidia.connect-timeout:10s}") Duration connectTimeout,
            @org.springframework.beans.factory.annotation.Value("${app.nvidia.read-timeout:180s}") Duration readTimeout) {
        return builder -> {
            var factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(connectTimeout);
            factory.setReadTimeout(readTimeout);
            builder.requestFactory(factory);
        };
    }

    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                response.setHeader("X-Content-Type-Options", "nosniff");
                response.setHeader("X-Frame-Options", "SAMEORIGIN");
                response.setHeader("Referrer-Policy", "same-origin");
                response.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'self'; form-action 'self'");
                var session = request.getSession();
                if (session.getAttribute("csrf") == null) { session.setAttribute("csrf", UUID.randomUUID().toString()); }
                request.setAttribute("csrf", session.getAttribute("csrf"));
                if (request.getMethod().equals("POST") && !session.getAttribute("csrf").equals(request.getParameter("_csrf"))) {
                    response.sendError(403, "Formulario caducado. Recarga la pagina.");
                    return false;
                }
                return true;
            }
        });
    }
}