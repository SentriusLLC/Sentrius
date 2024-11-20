package io.dataguardians.sso.core.utils;

import java.net.MalformedURLException;
import java.net.URL;
import jakarta.servlet.http.HttpServletRequest;

public class HttpUtils {

  public static String getRedirectOnReferrer(HttpServletRequest request) {
    // remember that in the RFC referer was misspelled...
    // this has become the standard so we cannot change it.
    String referrer = request.getHeader("referer");
    String redirect = "/sso/dashboard";
    if (null != referrer && referrer.contains("/sso/")) {
      try {
        URL url = new URL(referrer);
        redirect = url.getPath();
      } catch (MalformedURLException e) {
      }
    }
    return redirect;
  }

  public static String getRequestPath(HttpServletRequest request) {
    // remember that in the RFC referer was misspelled...
    // this has become the standard so we cannot change it.
    String referrer = request.getRequestURI().toString();
    String redirect = "/sso/dashboard";
    if (null != referrer && referrer.contains("/sso/")) {
      try {
        URL url = new URL(referrer);
        redirect = url.getPath();
      } catch (MalformedURLException e) {
      }
    }
    return redirect;
  }
}
