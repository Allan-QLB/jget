package com.github.qlb;

import io.netty.util.internal.StringUtil;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
//import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Http {
    private static final String SCHEMA_INSECURE = "http://";
    private static final String SCHEMA_SECURE = "https://";
    private static final String FILE_NAME_KEY_IN_QUERY = "filename";
    private static final int DEFAULT_PORT_SECURE = 443;
    private static final int DEFAULT_PORT_INSECURE = 80;
    private final String url;
    private final Map<String, String> query = new HashMap<>();
    private String fileName;
    private String host;
    private int port;
    private boolean secure;

    public Http(String url) {
        parseUrl(url);
        this.url = url;
    }

    private String decodeAndParseQuery(String url) {
        final int queryPoint = url.indexOf('?');
        final String decoded;
        try {
            decoded = URLDecoder.decode(url, "utf8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("unsupported encoding");
        }
        if (queryPoint == -1) {
            return decoded;
        } else {
            final String queryString = url.substring(queryPoint + 1);
            final String[] kvs = queryString.split("&");
            for (String kv : kvs) {
                final String[] kvArr = kv.split("=");
                if (kvArr.length == 2) {
                    query.put(kvArr[0].toLowerCase(), kvArr[1]);
                }
            }
            return url.substring(0, queryPoint);
        }
    }
    private void parseUrl(String url) {
        if (StringUtil.isNullOrEmpty(url)) {
            throw new IllegalArgumentException("Url can not be empty");
        }
        final String decodedStripedUrl = decodeAndParseQuery(url);
        if (decodedStripedUrl.startsWith(SCHEMA_SECURE)) {
            parseHostAndPort(decodedStripedUrl, SCHEMA_SECURE);
        } else if (decodedStripedUrl.startsWith(SCHEMA_INSECURE)) {
            parseHostAndPort(decodedStripedUrl, SCHEMA_INSECURE);
        } else {
            throw new IllegalArgumentException("Http schema can not be found");
        }
        this.fileName = findFileName(decodedStripedUrl, query);
    }

    private void parseHostAndPort(String url, String schema) {
        final Pattern pattern = Pattern.compile(schema + "(?<host>[\\w.-]+)[\\w./-]*(:(?<port>\\d+))?(.*)$");
        final Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            host = Objects.requireNonNull(matcher.group("host"), "host cannot be null");
            if (matcher.group("port") != null) {
                port = Integer.parseInt(matcher.group("port"));
            } else {
                port = defaultPort(schema);
            }
//            port = Optional.ofNullable(matcher.group("port")).map(Integer::parseInt)
//                    .orElse(defaultPort(schema));
            if (SCHEMA_SECURE.equals(schema)) {
                secure = true;
            }
        } else {
            throw new IllegalArgumentException("Not valid url " + url);
        }
    }

    public static int defaultPort(String schema) {
        if (SCHEMA_SECURE.equals(schema)) {
            return DEFAULT_PORT_SECURE;
        } else if (SCHEMA_INSECURE.equals(schema)) {
            return DEFAULT_PORT_INSECURE;
        } else {
            throw new IllegalArgumentException("unsupported schema " + schema);
        }
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public boolean isSecure() {
        return secure;
    }

    public String getUrl() {
        return url;
    }

    public String getFileName() {
        return fileName;
    }

    public String findFileName(String url, Map<String, String> query) {
        final int lastSeparatorIdx = url.lastIndexOf("/");
        if (query.containsKey(FILE_NAME_KEY_IN_QUERY)) {
            return query.get(FILE_NAME_KEY_IN_QUERY);
        } else if (lastSeparatorIdx != -1) {
            return url.substring(lastSeparatorIdx + 1);
        } else {
            throw new IllegalStateException("Can not find filename in url" + url);
        }
    }
}
