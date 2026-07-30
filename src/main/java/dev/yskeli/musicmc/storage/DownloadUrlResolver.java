package dev.yskeli.musicmc.storage;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;

public final class DownloadUrlResolver {
    private final List<String> proxyPrefixes;

    public DownloadUrlResolver(List<String> proxyPrefixes) {
        this.proxyPrefixes = proxyPrefixes.stream().map(DownloadUrlResolver::normalizePrefix).distinct().toList();
    }

    public List<URI> candidates(String originalUrl) {
        URI original = requireHttps(originalUrl);
        LinkedHashSet<URI> candidates = new LinkedHashSet<>();
        if (isGitHubRelease(original)) {
            for (String prefix : proxyPrefixes) {
                candidates.add(URI.create(prefix + original.toASCIIString()));
            }
        }
        candidates.add(original);
        return List.copyOf(candidates);
    }

    public String preferred(String originalUrl) {
        return candidates(originalUrl).getFirst().toASCIIString();
    }

    private static boolean isGitHubRelease(URI uri) {
        return "github.com".equalsIgnoreCase(uri.getHost()) && uri.getPath().contains("/releases/download/");
    }

    private static String normalizePrefix(String value) {
        URI uri = requireHttps(value);
        String normalized = uri.toASCIIString();
        return normalized.endsWith("/") ? normalized : normalized + '/';
    }

    private static URI requireHttps(String value) {
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalArgumentException("Download URL must use HTTPS: " + value);
        }
        return uri;
    }
}
