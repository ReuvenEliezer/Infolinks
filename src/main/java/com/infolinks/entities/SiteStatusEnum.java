package com.infolinks.entities;

public enum SiteStatusEnum {
    NotSet,
    Ok,
    MissingLine,
    AdsFileNotFound,
    ConnectionTimeOut,
    IoException,
    HttpClientErrorException,
}
