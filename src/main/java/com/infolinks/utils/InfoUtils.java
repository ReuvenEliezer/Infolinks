package com.infolinks.utils;

import java.io.File;

public class InfoUtils {
    public static String getInfolinksSitesPath() {
        return "src" + File.separator + "main" + File.separator + "resources" +  File.separator + "static"+File.separator+"infolinks_sites.csv";
    }

    public static String getInfolinksMasterPath() {
        return "src" + File.separator + "main" + File.separator + "resources" +  File.separator + "static"+File.separator+"infolinks_master-ads.csv";
    }
}
