package io.sentrius.sso.core.breadcrumbs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.sentrius.sso.core.security.service.CookieService;
import io.sentrius.sso.core.utils.JsonUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Service
public class BreadcrumbService {

    final Function<List<BreadcrumbItem>, List<BreadcrumbItem>> breadCrumbFunction;
    private List<BreadcrumbItem> breadcrumbs = new ArrayList<>();
    private CookieService cookieService;

    public BreadcrumbService(List<BreadcrumbItem> items,
            Function<List<BreadcrumbItem>, List<BreadcrumbItem>> breadCrumbSupplier) {
        this.breadCrumbFunction = breadCrumbSupplier;
        this.breadcrumbs = items;
    }

    public BreadcrumbService() {

        Function<List<BreadcrumbItem>, List<BreadcrumbItem>> fx = (List<BreadcrumbItem> bc) ->{
            if (null == bc) {
                var emptyNode = JsonUtil.MAPPER.createArrayNode();
                cookieService.setEncryptedCookie(getCurrentHttpRequest(), getCurrentHttpResponse(),
                    CookieService.BREADCRUMB_ITEMS, emptyNode.toString(), 0);
                return new ArrayList<>();
            }
            JsonNode newNode  = JsonUtil.MAPPER.valueToTree(bc);
            if (!bc.isEmpty()) {
                cookieService.setEncryptedCookie(getCurrentHttpRequest(), getCurrentHttpResponse(),
                    CookieService.BREADCRUMB_ITEMS, newNode.toString(), 0);
            }
            return bc;

        };

        this.breadCrumbFunction = fx;
        this.breadcrumbs = new ArrayList<>();

    }

    public void addBreadcrumb(String name, String url, Map<String, String[]> parameterMap) {
        if (breadcrumbs.isEmpty() || (!breadcrumbs.get(breadcrumbs.size()-1).getName().equals(name) &&
                !breadcrumbs.get(breadcrumbs.size()-1).getUrl().equals(url))) {
            for(int i=0 ; i < breadcrumbs.size(); i++) {
                if(breadcrumbs.get(i).getName().equals(name) & breadcrumbs.get(i).getUrl().equals(url)) {
                    breadcrumbs = breadcrumbs.subList(0, i+1);
                    return;
                }
            }
            String arguments = "";
            for(Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
                if (!entry.getKey().equals("_csrf") && !entry.getKey().equals("act")) {
                    arguments += entry.getKey() + "=" + entry.getValue()[0] + "&";
                }
            }
            var bc = new BreadcrumbItem(name, url, arguments);
            breadcrumbs.add(bc);

        }
    }

    public void removeLastBreadcrumb() {
        if (!breadcrumbs.isEmpty()) {
            breadcrumbs.remove(breadcrumbs.size() - 1);
        }
    }

    public List<BreadcrumbItem> getBreadcrumbs() throws JsonProcessingException {
        return breadCrumbFunction.apply(breadcrumbs);

    }

    public void clearBreadcrumbs() {
        breadcrumbs.clear();
        breadCrumbFunction.apply(null);
    }

    // Helper method to get the current HttpServletRequest
    private HttpServletResponse getCurrentHttpResponse() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getResponse() : null;
    }

    private HttpServletRequest getCurrentHttpRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }
}
