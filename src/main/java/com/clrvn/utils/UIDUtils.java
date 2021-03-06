package com.clrvn.utils;

import java.util.UUID;

/**
 * @author Clrvn
 */
public class UIDUtils {

    /**
     * 生成code
     *
     * @return
     */
    public static String getUId() {
        return UUID.randomUUID().toString().replace("-", "").substring(10);
    }
}
