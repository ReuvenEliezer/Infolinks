package com.infolinks.services;

import com.infolinks.entities.Ads;
import com.infolinks.entities.SiteStatusEnum;

import java.util.Set;

public interface CrawlerService {

    Set<String> getAllSite(Ads ads);

    Set<Ads> getAllMissingLines(String siteURL);

    Set<String> getAllSiteWithoutAdsFile();

    SiteStatusEnum getStatus(String siteURL);

}
