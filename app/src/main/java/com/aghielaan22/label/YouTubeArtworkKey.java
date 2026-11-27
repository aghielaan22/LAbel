package com.aghielaan22.label;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/** Identifies a recording for artwork lookup without rewriting the URL used by playback. */
final class YouTubeArtworkKey {
    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");

    private YouTubeArtworkKey() {}

    static String fromUrl(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return null;
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();
        if ((!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) ||
                host == null || uri.getUserInfo() != null) {
            return null;
        }

        host = host.toLowerCase(Locale.ROOT);
        String path = uri.getPath();
        if (path == null) return null;
        String[] segments = path.replaceFirst("^/+", "").split("/");
        String id = null;
        if (host.equals("youtu.be")) {
            if (segments.length == 1) id = segments[0];
        } else if (host.equals("youtube.com") || host.endsWith(".youtube.com")) {
            if (segments.length == 1 && segments[0].equals("watch")) {
                String query = uri.getRawQuery();
                if (query != null) {
                    for (String field : query.split("&")) {
                        if (field.startsWith("v=")) {
                            id = field.substring(2);
                            break;
                        }
                    }
                }
            } else if (segments.length == 2) {
                String kind = segments[0];
                if (kind.equals("shorts") || kind.equals("embed") || kind.equals("live")) {
                    id = segments[1];
                }
            }
        }
        return id != null && VIDEO_ID.matcher(id).matches() ? id : null;
    }
}