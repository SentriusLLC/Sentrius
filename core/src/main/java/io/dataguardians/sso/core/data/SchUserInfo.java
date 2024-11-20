package io.dataguardians.sso.core.data;

import com.jcraft.jsch.UserInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SchUserInfo implements UserInfo {

    String message;
    @Override
    public String getPassphrase() {
        return "";
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public boolean promptPassword(String message) {
        return false;
    }

    @Override
    public boolean promptPassphrase(String message) {
        return false;
    }

    @Override
    public boolean promptYesNo(String message) {
        return false;
    }

    @Override
    public void showMessage(String message) {
        this.message = message;
        System.out.println("Get message");
    }
}