package com.sub2api.module.common.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * GitHub Release 服务
 * 用于检查和下载 GitHub releases
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubReleaseService {

    private final HttpClient httpClient;

    @Value("${github.release.user-agent:Sub2API-Updater}")
    private String userAgent;

    /**
     * GitHub Release 信息
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GitHubRelease {
        private String url;
        private String tagName;
        private String name;
        private boolean draft;
        private boolean prerelease;
        private String publishedAt;

        @JsonProperty("html_url")
        private String htmlUrl;

        private List<GitHubAsset> assets;
    }

    /**
     * GitHub Release Asset
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GitHubAsset {
        private String name;
        private String browserDownloadUrl;

        @JsonProperty("download_count")
        private int downloadCount;

        private long size;
    }

    /**
     * 获取最新 Release
     *
     * @param repo 仓库 (格式: owner/repo)
     * @return 最新 Release 信息
     */
    public GitHubRelease getLatestRelease(String repo) {
        try {
            String url = String.format("https://api.github.com/repos/%s/releases/latest", repo);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", userAgent)
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("GitHub API returned {} for repo: {}", response.statusCode(), repo);
                return null;
            }

            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(response.body(), GitHubRelease.class);

        } catch (Exception e) {
            log.error("Failed to fetch latest release for {}: {}", repo, e.getMessage());
            return null;
        }
    }

    /**
     * 下载文件
     *
     * @param url      下载 URL
     * @param dest     目标路径
     * @param maxSize  最大文件大小 (字节)
     */
    public void downloadFile(String url, Path dest, long maxSize) throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", userAgent)
                    .timeout(Duration.ofMinutes(10))
                    .GET()
                    .build();

            HttpResponse<Path> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofFile(dest));

            if (response.statusCode() != 200) {
                throw new IOException("Download returned " + response.statusCode());
            }

            // 检查文件大小
            long fileSize = Files.size(dest);
            if (fileSize > maxSize) {
                Files.deleteIfExists(dest);
                throw new IOException(String.format("File too large: %d bytes (max %d)", fileSize, maxSize));
            }

            log.info("Downloaded file to: {}", dest);

        } catch (IOException e) {
            Files.deleteIfExists(dest);
            throw e;
        }
    }

    /**
     * 获取 Checksum 文件内容
     *
     * @param url checksum 文件 URL
     * @return 文件内容
     */
    public String fetchChecksumFile(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", userAgent)
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Failed to fetch checksum file: status={}", response.statusCode());
                return null;
            }

            return response.body();

        } catch (Exception e) {
            log.error("Failed to fetch checksum file: {}", e.getMessage());
            return null;
        }
    }
}
