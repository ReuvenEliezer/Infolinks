package com.infolinks.services;

import com.infolinks.app.InfolinksApp;
import com.infolinks.entities.Ads;
import com.infolinks.entities.SiteStatusEnum;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.Duration;
import java.util.Set;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = InfolinksApp.class)
@RunWith(SpringRunner.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class ReaderTest {

    @Autowired
    private CrawlerService crawlerService;

    @Test
    public void test() throws InterruptedException {
        Thread.sleep(Duration.ofMinutes(3).toMillis());

        String site = "http://www.indiaeducation.net";
        Ads ads = new Ads("Pubmatic.com", "60809", "RESELLER", "5d62403b186f2ace");
        Set<String> allSite = crawlerService.getAllSite(ads);
        Assert.assertTrue(allSite.size() > 100); //552
        Set<Ads> allMissingLines = crawlerService.getAllMissingLines("http://northwest50plus.com");
        Assert.assertEquals(2, allMissingLines.size());
        Assert.assertEquals(SiteStatusEnum.MissingLine, crawlerService.getStatus(site));
        Assert.assertEquals(SiteStatusEnum.NotSet, crawlerService.getStatus("http://dividaat.com"));
        Set<String> allSiteWithoutAdsFile = crawlerService.getAllSiteWithoutAdsFile();
        Assert.assertTrue(allSiteWithoutAdsFile.size() > 100); //542
        allMissingLines = crawlerService.getAllMissingLines("http://kitaabghar.net");
        Assert.assertEquals(6, allMissingLines.size());
    }


    @Test
    public void retryViaHttpInsteadOfHttpsTest() throws InterruptedException {
        Thread.sleep(20000);
        String site = "http://www.aviationbanter.com/ads.txt";
        Assert.assertNotEquals(SiteStatusEnum.AdsFileNotFound, crawlerService.getStatus(site));
    }

    @Test
    public void movedPermanentlyHttpStatusCodeTest() throws InterruptedException {
        Thread.sleep(20000);
        String site = "http://www.itcampus.in/ads.txt";
        Assert.assertNotEquals(SiteStatusEnum.AdsFileNotFound, crawlerService.getStatus(site));
    }

}
