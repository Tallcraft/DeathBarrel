package com.tallcraft.deathbarrel;

import org.apache.commons.lang.StringUtils;

public class Util {
  /**
  * Replace args in raw to args.
  *
  * @param raw  text
  * @param args args
  * @return filled text
  */
  public static String fillArgs(String raw, String... args) {
    if (raw == null) {
      return "Invalid message: null";
    }
    if (raw.isEmpty()) {
      return "";
    }
    if (args == null) {
      return raw;
    }
    for (int i = 0; i < args.length; i++) {
      raw = StringUtils.replace(raw, "{" + i + "}", args[i] == null ? "" : args[i]);
    }
    return raw;
}
}
