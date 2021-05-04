package com.infolinks.services;

import com.infolinks.entities.Ads;
import com.infolinks.entities.ClientInfo;
import com.infolinks.entities.SiteStatusEnum;
import com.infolinks.utils.InfoUtils;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.ConnectException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CrawlerServiceImpl implements CrawlerService {

    private static final Logger logger = LogManager.getLogger(CrawlerServiceImpl.class);


    private static final String MY_PUBLISHER_DOMAIN = "Infolinks.com";
    private static final String ADS_FILE_PATH = "/ads.txt";
    private static final String HTTPS = "https";
    private static final String HTTP = "http";
    private static final String CSV_SPLITTER = ",";
    private static final String DOT_URL_SITE = ".";
    private static final String ADS_COMMENT_SIGN = "#";

    private Map<String, Set<Ads>> missingLinesToSiteMap = new ConcurrentHashMap<>();
    private Map<String, SiteStatusEnum> siteStatusMap = new ConcurrentHashMap<>();
    private Map<Ads, Set<String>> lineToUrlSiteMap = new ConcurrentHashMap<>(); //TODO we need to put it only the site that including in master or all sites that appears in ads.txt?
    private Set<String> siteWithoutAdsFileSet = ConcurrentHashMap.newKeySet();
    private Map<String, String> siteToPublisherIdMap = new ConcurrentHashMap<>();

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private InfoScheduler infoScheduler;

    @PostConstruct
    private void init() throws IOException {
        LocalDateTime start = LocalDateTime.now();
        String infolinksSitesPath = InfoUtils.getInfolinksSitesPath();
        Reader siteReader = Files.newBufferedReader(Paths.get(infolinksSitesPath));
        CsvToBean<ClientInfo> clientInfoCsvToBean = new CsvToBeanBuilder(siteReader)
                .withType(ClientInfo.class)
                .withIgnoreLeadingWhiteSpace(true)
                .build();
        List<ClientInfo> clientInfoList = new ArrayList<>();
        clientInfoCsvToBean.forEach((clientInfo -> clientInfoList.add(clientInfo)));
//        clientInfoList.forEach(clientInfo -> logger.info(clientInfo));
        clientInfoList.forEach(clientInfo -> siteToPublisherIdMap.put(clientInfo.getSiteURL(), clientInfo.getPublisherId()));
        String infolinksMasterPath = InfoUtils.getInfolinksMasterPath();
        Reader masterReader = Files.newBufferedReader(Paths.get(infolinksMasterPath));
        CsvToBean<Ads> adsCsvToBean = new CsvToBeanBuilder(masterReader)
                .withType(Ads.class)
                .withIgnoreLeadingWhiteSpace(true)
                .build();

        Map<String, Ads> domainToAdsMap = new HashMap<>();
        adsCsvToBean.forEach(ads -> domainToAdsMap.put(ads.getDomain(), ads));
//        adsList.forEach(ads -> logger.info(ads));

        clientInfoList.stream()
//                .parallel()
                .forEach(clientInfo -> infoScheduler.scheduleNow(() -> processSite(clientInfo, domainToAdsMap)));
        logger.info("done. total duration: {}", Duration.between(start, LocalDateTime.now()));
    }

    private void processSite(ClientInfo clientInfo, Map<String, Ads> domainToAdsMap) {
        logger.info("start to process site {}", clientInfo);
        try {
            Set<Ads> adsHashSet = setMyPublisher(clientInfo, domainToAdsMap);
            String siteURL = clientInfo.getSiteURL().replace(HTTP, HTTPS);
//                        String siteURL = "https://dividaat.com/"; //for HttpClientErrorException
            String result = restTemplate.getForObject(siteURL + ADS_FILE_PATH, String.class);
            List<String> lines = IOUtils.readLines(new StringReader(result));
//                lines.forEach(line -> logger.info("result from {} : {}", clientInfo.getSiteURL(), line));
            for (String line : lines) {
                line = line.trim();
                if (!line.startsWith(ADS_COMMENT_SIGN) && line.matches(String.format(".*%1$s.*%2$s.*%2$s.*", DOT_URL_SITE, CSV_SPLITTER))) {
                    String[] split = line.split(CSV_SPLITTER);
                    String optionalValue = null;
                    if (split.length > 3) {
                        optionalValue = split[3];
                    }
                    Ads ads1 = new Ads(split[0], split[1], split[2], optionalValue);
                    lineToUrlSiteMap.computeIfAbsent(ads1, v -> new HashSet<>()).add(clientInfo.getSiteURL());
                    if (adsHashSet.contains(ads1)) {
                        adsHashSet.remove(ads1);
                    }
                }
            }

            if (!adsHashSet.isEmpty()) {
                handleMissingLine(clientInfo, adsHashSet);
            } else {
                setOkStatus(clientInfo);
            }
        } catch (ConnectException connectException) {
            handleConnectionTimeOut(clientInfo, connectException);
//        } catch (HttpClientErrorException.NotFound | IOException httpClientErrorException) {
        } catch (HttpClientErrorException.NotFound notFound) {
            handleFileNotFound(clientInfo, notFound);
        } catch (IOException ioException) {
            handleIoException(clientInfo, ioException);
        } catch (ConcurrentModificationException e) {
            logger.fatal(e);
        } catch (Exception e) {
            handleOtherExceptions(clientInfo, e);
        }
    }

    private Set<Ads> setMyPublisher(ClientInfo clientInfo, Map<String, Ads> domainToAdsMap) {
        Ads ads = domainToAdsMap.get(MY_PUBLISHER_DOMAIN);
        String publisherId = siteToPublisherIdMap.get(clientInfo.getSiteURL());
        logger.info("set {} to publisherId: {}", MY_PUBLISHER_DOMAIN, publisherId);
        ads.setAccountId(publisherId);
        Set<Ads> adsHashSet = new HashSet<>();
        adsHashSet.addAll(domainToAdsMap.values());
//          adsHashSet.forEach(ads -> logger.info(ads));
        return adsHashSet;
    }

    private void handleIoException(ClientInfo clientInfo, IOException ioException) {
        //        infoScheduler.scheduleNow(() -> {
        logger.error("fail during trying to read ads.txt from: {}. connectException:", clientInfo.getSiteURL(), ioException);
        siteStatusMap.put(clientInfo.getSiteURL(), SiteStatusEnum.IoException);
//        });
    }

    private void setOkStatus(ClientInfo clientInfo) {
        logger.info("Site {} status: {}", clientInfo.getSiteURL(), SiteStatusEnum.Ok);
        siteStatusMap.put(clientInfo.getSiteURL(), SiteStatusEnum.Ok);
    }

    private void handleConnectionTimeOut(ClientInfo clientInfo, ConnectException connectException) {
        //        infoScheduler.scheduleNow(() -> {
        logger.error("fail to read ads.txt from: {}. connectException:", clientInfo.getSiteURL(), connectException);
        siteStatusMap.put(clientInfo.getSiteURL(), SiteStatusEnum.ConnectionTimeOut);
//        });
    }

    private void handleOtherExceptions(ClientInfo clientInfo, Exception httpClientErrorException) {
//        infoScheduler.scheduleNow(() -> {
        logger.error("fail to read ads.txt from: {}. HttpClientErrorException:", clientInfo.getSiteURL(), httpClientErrorException);
        siteStatusMap.put(clientInfo.getSiteURL(), SiteStatusEnum.HttpClientErrorException);
//        });
    }

    private void handleFileNotFound(ClientInfo clientInfo, Exception httpClientErrorException) {
//        infoScheduler.scheduleNow(() -> {
        logger.error("ads.txt file not found: {}. NotFound:", clientInfo.getSiteURL(), httpClientErrorException);
        siteStatusMap.put(clientInfo.getSiteURL(), SiteStatusEnum.AdsFileNotFound);
        siteWithoutAdsFileSet.add(clientInfo.getSiteURL());
//        });
    }

    private void handleMissingLine(ClientInfo clientInfo, Set<Ads> missingLine) {
//        infoScheduler.scheduleNow(() -> {
        logger.error("the following sites are missing from {}: {}", clientInfo.getSiteURL(), missingLine);
        missingLinesToSiteMap.put(clientInfo.getSiteURL(), missingLine);
        siteStatusMap.put(clientInfo.getSiteURL(), SiteStatusEnum.MissingLine);
//        });
    }


    @Override
    public Set<String> getAllSite(Ads ads) {
//        for (Map.Entry<Ads, Set<String>> entry : lineToUrlSiteMap.entrySet()) {
//            if (entry.getValue().size() > 1) {
//                logger.info("{} with size {}", entry.getKey(), entry.getValue().size());
//            }
//        }
        Set<String> result = lineToUrlSiteMap.get(ads);
        return result != null ? result : new HashSet<>();
    }

    @Override
    public Set<Ads> getAllMissingLines(String siteURL) {
        Set<Ads> result = missingLinesToSiteMap.get(siteURL);
        return result != null ? result : new HashSet<>();
    }

    @Override
    public Set<String> getAllSiteWithoutAdsFile() {
        return siteWithoutAdsFileSet;
    }

    @Override
    public SiteStatusEnum getStatus(String siteURL) {
        SiteStatusEnum siteStatusEnum = siteStatusMap.get(siteURL);
        return siteStatusEnum != null ? siteStatusEnum : SiteStatusEnum.NotSet;
    }
}
