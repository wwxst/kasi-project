package com.kasi.backend.user.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class UserAvatarWebConfig implements WebMvcConfigurer {

    private final String resourceLocation;

    public UserAvatarWebConfig(@Value("${app.upload.dir:./data/uploads}") String uploadDirectory) {
        String location = Path.of(uploadDirectory)
                .toAbsolutePath()
                .normalize()
                .resolve("user-avatars")
                .toUri()
                .toString();
        this.resourceLocation = location.endsWith("/") ? location : location + "/";
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/user-avatars/**")
                .addResourceLocations(resourceLocation);
    }
}
