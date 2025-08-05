package com.example.softdelete.config;

import am.ik.pagination.web.CursorPageRequestHandlerMethodArgumentResolver;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(
				new CursorPageRequestHandlerMethodArgumentResolver<>(Long::parseLong, props -> props.withSizeMax(100)));
	}

}
