package com.example.qylbackend.dto;

import java.util.List;

public record SiteUrlData(
        List<String> thirdUrls,
        List<String> finalUrls,
        String officialDomain,
        String iosDownload,
        String androidDownload
) {
}
