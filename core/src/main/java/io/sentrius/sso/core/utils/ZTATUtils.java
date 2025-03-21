package io.sentrius.sso.core.utils;

import lombok.NonNull;
import org.apache.commons.codec.digest.DigestUtils;

public class ZTATUtils {

    public static String getCommandHash(@NonNull String command) {
        String originalString = command.trim();
        return DigestUtils.sha256Hex(originalString);
    }
}
