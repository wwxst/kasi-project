package com.kasi.backend.admin.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class AdminAvatarWebConfig implements WebMvcConfigurer {

    private final String resourceLocation;

    public AdminAvatarWebConfig(@Value("${app.upload.dir:./data/uploads}") String uploadDirectory) {
        String location = Path.of(uploadDirectory)
                .toAbsolutePath()
                .normalize()
                .resolve("admin-avatars")
                .toUri()
                .toString();
        this.resourceLocation = location.endsWith("/") ? location : location + "/";
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/admin-avatars/**")
                .addResourceLocations(resourceLocation);
    }
}
