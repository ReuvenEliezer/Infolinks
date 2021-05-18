package com.infolinks.services;

import com.google.common.collect.ImmutableList;
import com.google.common.net.InternetDomainName;
import com.infolinks.entities.Ads;
import com.infolinks.entities.ClientInfo;
import com.infolinks.entities.ClientInfoVariables;
import com.infolinks.entities.SiteStatusEnum;
import com.infolinks.utils.InfoUtils;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attributes;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URL;
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
    private static final String DOT_URL_SITE = "\\.";
    private static final String ADS_COMMENT_SIGN = "#";
    private static final String VARIABLE_SPLITTER = "=";
    private static final String CONTACT = "contact";
    private static final String SUBDOMAIN = "subdomain";

    private Map<String, Set<Ads>> missingLinesToSiteMap = new ConcurrentHashMap<>();
    private Map<String, SiteStatusEnum> siteStatusMap = new ConcurrentHashMap<>();
    private Map<Ads, Set<String>> lineToUrlSiteMap = new ConcurrentHashMap<>(); //TODO we need to put it only the site that including in master or all sites that appears in ads.txt?
    private Set<String> siteWithoutAdsFileSet = ConcurrentHashMap.newKeySet();
    private Map<String, ClientInfoVariables> siteToVariablesMap = new ConcurrentHashMap<>();
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
                .forEach(clientInfo -> infoScheduler.executeNow(() -> processAdsSite(clientInfo, domainToAdsMap)));
        logger.info("done. total duration: {}", Duration.between(start, LocalDateTime.now()));
    }

    private void processAdsSite(ClientInfo clientInfo, Map<String, Ads> domainToAdsMap) {
        logger.debug("start to process site {}", clientInfo);
        ResponseEntity<String> result;
        String siteURL = clientInfo.getSiteURL().replace(HTTP, HTTPS);
        Set<Ads> adsHashSet = setMyPublisher(clientInfo, domainToAdsMap);
        try {
//                        String siteURL = "https://dividaat.com/"; //for HttpClientErrorException.NotFound
            result = restTemplate.getForEntity(siteURL + ADS_FILE_PATH, String.class);
            logger.debug("response code from {}: {}", siteURL, result.getStatusCode());
            if (HttpStatus.valueOf(result.getStatusCode().value()).is2xxSuccessful()) {
                handleSuccessfulResponse(clientInfo, adsHashSet, result, domainToAdsMap);
            } else if (HttpStatus.MOVED_PERMANENTLY.equals(result.getStatusCode())
                    || HttpStatus.FOUND.equals(result.getStatusCode())
                    || HttpStatus.SEE_OTHER.equals(result.getStatusCode())) {
                String movedSiteURL = extractMovedUrl(result);
                if (movedSiteURL != null && isRedirectUrlWithinOriginalRootDomainScope(siteURL, movedSiteURL)) {
                    clientInfo.setSiteURL(movedSiteURL);
                    logger.debug("change url of site {} to {}", siteURL, movedSiteURL);
                    processAdsSite(clientInfo, domainToAdsMap);
                } else {
                    String message = String.format("not found new URL for site %s, http status %s, result after MOVED_PERMANENTLY %s", siteURL, result.getStatusCode());
                    handleFileNotFound(clientInfo, new UnsupportedOperationException(message));
                    logger.error(message);
                }
            }
        } catch (HttpClientErrorException.Unauthorized unauthorized) {
            handleUnauthorized(clientInfo, unauthorized);
        } catch (HttpClientErrorException.NotFound notFound) {
            //retrying with HTTP instead HTTPS
            logger.info("failed to get response from {} site by {}, re-retrying to get by {}", siteURL, HTTPS, HTTP);
            siteURL = clientInfo.getSiteURL().replace(HTTPS, HTTP);
            result = restTemplate.getForEntity(siteURL + ADS_FILE_PATH, String.class);
            if (HttpStatus.valueOf(result.getStatusCode().value()).is2xxSuccessful()) {
                logger.info("success to get response from {} site by {} instead of ", siteURL, HTTP, HTTPS);
                handleSuccessfulResponse(clientInfo, adsHashSet, result, domainToAdsMap);
            } else {
                handleFileNotFound(clientInfo, notFound);
            }
        } catch (ConcurrentModificationException e) {
            logger.fatal(e);
        } catch (Exception e) {
            handleOtherExceptions(clientInfo, e);
        }

    }

    private void handleUnauthorized(ClientInfo clientInfo, HttpClientErrorException.Unauthorized unauthorized) {
        //        infoScheduler.scheduleNow(() -> {
        /**
         * If the server response indicates the resource is restricted (HTTP 401) the advertising system
         * should seek direct contact with the site for authorization keys or clarification.
         */
        logger.error("site: {}. unauthorized:", clientInfo.getSiteURL(), unauthorized);
        siteStatusMap.put(clientInfo.getSiteURL(), SiteStatusEnum.Unauthorized);
        ClientInfoVariables clientInfoVariables = siteToVariablesMap.get(clientInfo.getSiteURL());
        if (clientInfoVariables != null) {
            //TODO send email to contact
            List<String> contactList = clientInfoVariables.getContactList();
        }
//        });
    }

    private boolean isRedirectUrlWithinOriginalRootDomainScope(String siteURL, String movedSiteURL) throws MalformedURLException {
        URL url = new URL(siteURL);
        String host = url.getHost();
        InternetDomainName internetDomainName = InternetDomainName.from(host).topPrivateDomain();
        ImmutableList<String> parts = internetDomainName.parts();
        if (parts.isEmpty())
            return false;
        String rootDomain = parts.get(parts.size() - 2);//before the last dot
        return movedSiteURL.contains(rootDomain);
    }

    private String extractMovedUrl(ResponseEntity<String> result) {
        String body = result.getBody();
        if (body == null) {
            return null;
        }
        Document parse = Jsoup.parse(body);
        Elements elements = parse.getElementsByAttribute("href");
        if (elements == null || elements.isEmpty()) {
            return null;
        }
        Element element = elements.get(0);// TODO?
        Attributes attributes = element.attributes();
        return attributes.get("href");
    }

    private void handleSuccessfulResponse(ClientInfo clientInfo, Set<Ads> adsHashSet, ResponseEntity<String> result, Map<String, Ads> domainToAdsMap) {
        List<String> lines;
        try {
            lines = IOUtils.readLines(new StringReader(result.getBody()));
        } catch (IOException ioException) {
            handleIoException(clientInfo, ioException);
            return;
        }
//                lines.forEach(line -> logger.info("result from {} : {}", clientInfo.getSiteURL(), line));
        for (String line : lines) {
            line = line.trim();
            if (!line.startsWith(ADS_COMMENT_SIGN)) {
                if (line.matches(String.format(".*%1$s.*%2$s.*%2$s.*", DOT_URL_SITE, CSV_SPLITTER))) {
                    addAdsClientInfo(clientInfo, adsHashSet, line);
                } else if (line.contains(VARIABLE_SPLITTER)) {
                    addAdsClientVariablesInfo(clientInfo, line, domainToAdsMap);
                }
            }
        }

        if (!adsHashSet.isEmpty()) {
            handleMissingLine(clientInfo, adsHashSet);
        } else {
            setOkStatus(clientInfo);
        }
    }

    private void addAdsClientVariablesInfo(ClientInfo clientInfo, String line, Map<String, Ads> domainToAdsMap) {
        ClientInfoVariables clientInfoVariables = new ClientInfoVariables();
        if (line.contains(CONTACT)) {
            String[] split = line.split(VARIABLE_SPLITTER);
            if (split.length == 2)
                clientInfoVariables.addContact(line.split(VARIABLE_SPLITTER)[1]);
            else logger.error("failed to read contact value of site {}", clientInfo.getSiteURL());
        } else if (line.contains(SUBDOMAIN)) {
            String[] split = line.split(VARIABLE_SPLITTER);
            if (split.length == 2) {
                clientInfoVariables.addSubdomain(line.split(VARIABLE_SPLITTER)[1]);
                //TODO call processAdsSite for each subdomain only once
            } else logger.error("failed to read subdomain value of site {}", clientInfo.getSiteURL());
        }
    }

    private void addAdsClientInfo(ClientInfo clientInfo, Set<Ads> adsHashSet, String line) {
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

    private Set<Ads> setMyPublisher(ClientInfo clientInfo, Map<String, Ads> domainToAdsMap) {
        Ads ads = domainToAdsMap.get(MY_PUBLISHER_DOMAIN);
        String publisherId = siteToPublisherIdMap.get(clientInfo.getSiteURL());
        logger.debug("set {} to publisherId: {}", MY_PUBLISHER_DOMAIN, publisherId);
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
