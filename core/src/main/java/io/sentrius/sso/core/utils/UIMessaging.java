package io.sentrius.sso.core.utils;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UIMessaging {

  public String messageToUser;
  public String errorToUser;
  public String banner;

  public boolean isEmpty() {
    return (messageToUser == null || messageToUser.isEmpty()) && (errorToUser == null || errorToUser.isEmpty()) && (banner == null || banner.isEmpty());
  }
}
