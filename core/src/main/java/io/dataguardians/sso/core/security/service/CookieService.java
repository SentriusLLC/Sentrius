package io.dataguardians.sso.core.security.service;

import java.security.GeneralSecurityException;
import java.util.List;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CookieService {

    private static final String COOKIE_PATH = "/";
    private final CryptoService cryptoService;

    public static final String BREADCRUMB_ITEMS = "breadcrumbs";
    public static final String SELECTED_PROFILE = "selectedProfile";

    // Constructor with CryptoService dependency
    public CookieService(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    // Sets an encrypted cookie
    public boolean setEncryptedCookie(HttpServletRequest request, HttpServletResponse response, String name,
                                      String value, int maxAge) {

        String encryptedValue = null;
        if (null == value){
            expireCookies(request, response, List.of(name));
            return true;
        }
        try {
            encryptedValue = cryptoService.encrypt(value);
            Cookie cookie = new Cookie(name, encryptedValue);
            cookie.setMaxAge(maxAge);
            cookie.setPath(COOKIE_PATH);
            response.addCookie(cookie);
            return true;
        } catch (GeneralSecurityException e) {
            log.error("Error while setting cookie", e);
        }
        return false;
    }

    private static void expireCookies(
        HttpServletRequest request, HttpServletResponse response, List<String> cookies) {
        if (null != request.getCookies()) {
            for (Cookie cookie : request.getCookies()) {
                if (cookies.contains(cookie.getName())) {
                    cookie.setValue("");
                    cookie.setMaxAge(0);
                    //if (!AppConfig.getOptions().allowInsecureCookies) cookie.setSecure(true);
                    cookie.setPath("/");
                    response.addCookie(cookie);
                }
            }
        }
    }

    // Gets and decrypts an encrypted cookie
    public String getEncryptedCookie(HttpServletRequest request, String name) throws GeneralSecurityException {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) {
                    return cryptoService.decrypt(cookie.getValue());
                }
            }
        }
        return null;
    }

    // Clears a cookie by setting its max age to zero
    public void clearCookie(HttpServletResponse response, String name) {
        Cookie cookie = new Cookie(name, null);
        cookie.setMaxAge(0);
        cookie.setPath(COOKIE_PATH);
        response.addCookie(cookie);
    }

}
