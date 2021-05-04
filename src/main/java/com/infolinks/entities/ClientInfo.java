package com.infolinks.entities;

import com.opencsv.bean.CsvBindByName;

public class ClientInfo {

    @CsvBindByName(column = "publisher_id", required = true)
    private String publisherId;

    @CsvBindByName(column = "site_index", required = true)
    private String siteIndex;

    @CsvBindByName(column = "siteURL", required = true)
    private String siteURL;

    public String getPublisherId() {
        return publisherId;
    }

    public void setPublisherId(String publisherId) {
        this.publisherId = publisherId.trim();
    }

    public String getSiteIndex() {
        return siteIndex;
    }

    public void setSiteIndex(String siteIndex) {
        this.siteIndex = siteIndex.trim();
    }

    public String getSiteURL() {
        return siteURL;
    }

    public void setSiteURL(String siteURL) {
        this.siteURL = siteURL.trim();
    }

    @Override
    public String toString() {
        return "ClientInfo{" +
                "publisherId='" + publisherId + '\'' +
                ", siteIndex='" + siteIndex + '\'' +
                ", siteURL='" + siteURL + '\'' +
                '}';
    }
}
