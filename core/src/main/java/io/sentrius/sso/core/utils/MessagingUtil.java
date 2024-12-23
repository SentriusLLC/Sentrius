package io.sentrius.sso.core.utils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class MessagingUtil {


    static final Map<String, String> preDefinedMessages;

    static final Map<String, String> preDefinedMessageMapping;

    public static final String UNEXPECTED_ERROR = "UNEXPECTED_ERROR";
    public static final String REQUIRE_APPROVAL = "REQUIRE_APPROVAL";

    public static final String AWAITING_APPROVAL = "AWAITING_APPROVAL";

    public static final String DO_NOT_HAVE_ACCESS = "DO_NOT_HAVE_ACCESS";

    public static final String DO_NOT_HAVE_ACCESS_TIMEWINDOW = "DO_NOT_HAVE_ACCESS_TIMEWINDOW";

    public static final String DENIED = "DENIED";

    public static final String PROFILE_LOCKED = "PROFILE_LOCKED";

    public static final String CANNOT_VIEW_SYSTEMS = "CANNOT_VIEW_SYSTEMS";

    public static final String CANNOT_EDIT_SYSTEMS = "CANNOT_EDIT_SYSTEMS";

    public static final String CANNOT_DEL_SYSTEMS = "CANNOT_DEL_SYSTEMS";

    public static final String CANNOT_MANAGE_SYSTEMS = "CANNOT_MANAGE_SYSTEMS";

    public static final String CANNOT_VIEW_USERS = "CANNOT_VIEW_USERS";

    public static final String CANNOT_EDIT_USERS = "CANNOT_EDIT_USERS";

    public static final String CANNOT_DELETE_USERS = "CANNOT_DELETE_USERS";

    public static final String CAN_MANAGE_USERS = "CAN_MANAGE_USERS";

    public static final String TYPE_SAVED = "TYPE_SAVED";
    public static final String USER_DELETE_SUCCESS = "USER_DELETE_SUCCESS";

    public static final String SOME_SETTINGS_FAIL = "SOME_SETTINGS_FAIL";
    public static final String ALL_SETTINGS_FAIL = "ALL_SETTINGS_FAIL";
    public static final String SETTINGS_UPDATED = "SETTINGS_UPDATED";

    public static final String RULE_UPDATED = "RULE_UPDATED";


    // Load scan packages for controllers that have the Kontrol method annotation
    static {
        preDefinedMessages = new ConcurrentHashMap<>();
        preDefinedMessageMapping = new ConcurrentHashMap<>();

        addMapping(
            RULE_UPDATED,
            "Rule(s) updated successfully");
        addMapping(
            SETTINGS_UPDATED,
            "Settings updated successfully");
        addMapping(SOME_SETTINGS_FAIL, "Some settings failed to update");
        addMapping(ALL_SETTINGS_FAIL, "Settings failed to update");
        addMapping(
            UNEXPECTED_ERROR, "An unexpected error occurred. Please contact your administrator.");
        addMapping(
            DO_NOT_HAVE_ACCESS, "You do not have access to this page. Please contact your administrator.");
        addMapping(
            DO_NOT_HAVE_ACCESS_TIMEWINDOW, "You do not have any assigned profiles during this time window. Ensure you " +
                "have selected a valid shift or contact your system administrator.");
        addMapping(
            REQUIRE_APPROVAL,
            "This operation requires approval per the system rules. A request has been submitted on"
                + " your behalf. You will be notified when it is approved.");
        addMapping(
            AWAITING_APPROVAL,
            "This operation has already been submitted for approval. You will be notified when it is"
                + " approved.");
        addMapping(DENIED, "Your approval has been denied. Please contact your administrator.");
        addMapping(PROFILE_LOCKED, "All systems within the given group are locked");
        addMapping(CANNOT_VIEW_SYSTEMS, "You do not have access to view systems");
        addMapping(CANNOT_EDIT_SYSTEMS, "You do not have access to edit systems");
        addMapping(CANNOT_DEL_SYSTEMS, "You do not have access to delete systems");
        addMapping(CANNOT_MANAGE_SYSTEMS, "You do not have access to manage systems");
        addMapping(CANNOT_VIEW_USERS, "You do not have access to view users");
        addMapping(CANNOT_EDIT_USERS, "You do not have access to edit users");
        addMapping(CANNOT_DELETE_USERS, "You do not have access to delete users");
        addMapping(CAN_MANAGE_USERS, "You do not have access to manage users");
        addMapping(
            TYPE_SAVED,
            "User Type saved. Implicit accesses will be added if they are required for your selection"
                + " ");
        addMapping(USER_DELETE_SUCCESS, "User deleted successfully");
    }

    public static void addMapping(String mapName, String text) {
        String uuid = UUID.randomUUID().toString();
        preDefinedMessages.put(uuid, text);
        preDefinedMessageMapping.put(mapName, uuid);
    }

    public static String getMessage(String mapName) {
        String uuid = preDefinedMessageMapping.get(mapName);
        if (uuid != null) {
            return preDefinedMessages.get(uuid);
        }
        return null;
    }

    public static String getMessageFromId(String id) {
        return preDefinedMessages.get(id);
    }

    public static String getMessageId(String mapName) {
        return preDefinedMessageMapping.get(mapName);
    }
}
