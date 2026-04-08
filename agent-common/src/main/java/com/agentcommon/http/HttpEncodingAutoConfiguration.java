package com.agentcommon.http;

import com.agentcommon.http.config.LlmHttpClientPoolProperties;
import com.agentcommon.web.TraceIdFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

@AutoConfiguration
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableConfigurationProperties(LlmHttpClientPoolProperties.class)
public class HttpEncodingAutoConfiguration {

    @Bean
    public EncodingRepairEngine encodingRepairEngine() {
        return new EncodingRepairEngine();
    }

    @Bean
    public EncodingRepairAspect encodingRepairAspect(EncodingRepairEngine engine) {
        return new EncodingRepairAspect(engine);
    }

    @Bean
    public HttpRequestClient httpRequestClient() {
        return new HttpRequestClient();
    }

    @Bean(destroyMethod = "destroy")
    public LlmHttpClientRouter llmHttpClientRouter(LlmHttpClientPoolProperties properties) {
        return new LlmHttpClientRouter(properties);
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(OncePerRequestFilter.class)
    @ConditionalOnMissingBean(name = "traceIdFilterRegistration")
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration() {
        FilterRegistrationBean<TraceIdFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new TraceIdFilter());
        bean.setName("traceIdFilter");
        bean.addUrlPatterns("/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
}
