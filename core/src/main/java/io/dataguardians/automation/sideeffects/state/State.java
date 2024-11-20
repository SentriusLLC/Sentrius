package io.dataguardians.automation.sideeffects.state;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class State {
  String name;
  String type;
  String color;
}
