package com.infolinks.entities;

public enum SiteStatusEnum {
    NotSet,
    Ok,
    MissingLine,
    AdsFileNotFound,
    Unauthorized,
    ConnectionTimeOut,
    IoException,
    HttpClientErrorException,
}
