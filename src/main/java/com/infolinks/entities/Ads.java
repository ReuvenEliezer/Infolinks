package com.infolinks.entities;

import com.opencsv.bean.CsvBindByPosition;

import java.util.Objects;

public class Ads {

    @CsvBindByPosition(position = 0, required = true)
    private String domain;

    @CsvBindByPosition(position = 1, required = true)
    private String accountId;

    @CsvBindByPosition(position = 2, required = true)
    private String adsType;

    @CsvBindByPosition(position = 3)
    private String optional;

    //only for csv reader
    public Ads() {
    }

    public Ads(String domain, String accountId, String adsType) {
        this.domain = domain;
        this.accountId = accountId;
        this.adsType = adsType;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain.trim();
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId.trim();
    }

    public AdsTypeEnum getAdsTypeEnum() {
        return AdsTypeEnum.valueOf(adsType.trim().toUpperCase());
    }

    public String getOptional() {
        return optional;
    }

    public void setOptional(String optional) {
        this.optional = optional.trim();
    }


    public void setAdsType(String adsType) {
        this.adsType = adsType.trim();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Ads ads = (Ads) o;
        return Objects.equals(domain, ads.domain) && Objects.equals(accountId, ads.accountId) && Objects.equals(adsType, ads.adsType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(domain, accountId, adsType);
    }

    @Override
    public String toString() {
        return "Ads{" +
                "domain='" + domain + '\'' +
                ", accountId='" + accountId + '\'' +
                ", adsType='" + adsType + '\'' +
                ", optional='" + optional + '\'' +
                '}';
    }
}
