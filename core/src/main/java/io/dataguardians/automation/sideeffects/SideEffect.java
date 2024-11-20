package io.dataguardians.automation.sideeffects;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SideEffect {
  SideEffectType type;
  String asset;
  String assetLocation;
  String sideEffectDescription;
  @Builder.Default boolean status = false;

  @Override
  public String toString() {
    return "SideEffect{"
        + "type="
        + type
        + ", asset='"
        + asset
        + '\''
        + ", assetLocation='"
        + assetLocation
        + '\''
        + ", sideEffectDescription='"
        + sideEffectDescription
        + '\''
        + ", status="
        + status
        + '}';
  }
}
