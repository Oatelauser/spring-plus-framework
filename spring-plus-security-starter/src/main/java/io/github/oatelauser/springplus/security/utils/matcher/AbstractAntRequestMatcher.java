package io.github.oatelauser.springplus.security.utils.matcher;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.web.util.pattern.PathPatternParser.defaultInstance;

/**
 * 抽象Ant请求匹配器
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2024-10-15
 * @since 1.0
 */
@Getter
public abstract class AbstractAntRequestMatcher implements RequestMatcher {

    @Setter
    private PathPatternParser defaultPathParser = defaultInstance;
    protected final List<RequestMatcher> requestMatches = new ArrayList<>();

    @Override
    public boolean matches(@NonNull HttpServletRequest request) {
        if (!requestMatches.isEmpty()) {
            for (RequestMatcher ant : requestMatches) {
                if (ant.matches(request)) {
                    return true;
                }
            }
        }
        return false;
    }

    protected void addRequestMatcher(String pattern) {
        this.addRequestMatcher(null, pattern);
    }

    protected void addRequestMatcher(HttpMethod httpMethod, String pattern) {
        RequestMatcher requestMatcher = PathPatternRequestMatcher.withPathPatternParser(defaultPathParser)
                .matcher(httpMethod, pattern);
        requestMatches.add(requestMatcher);
    }

}
