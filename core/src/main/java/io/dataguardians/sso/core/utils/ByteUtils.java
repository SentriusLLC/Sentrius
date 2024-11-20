package io.dataguardians.sso.core.utils;

import java.nio.ByteBuffer;

public class ByteUtils {
  private static ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);

  public static byte[] longToBytes(long x) {
    buffer.putLong(0, x);
    return buffer.array();
  }

  public static long bytesToLong(byte[] bytes) {
    buffer.put(bytes, 0, bytes.length);
    buffer.flip(); // need flip
    return buffer.getLong();
  }

  public static Long convertToLong(Object obj) {
    if (obj instanceof Long) {
      return (Long) obj;
    } else if (obj instanceof Integer) {
      return ((Integer) obj).longValue();
    } else if (obj instanceof String) {
      try {
        return Long.parseLong((String) obj);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("String does not contain a parsable long value: " + obj);
      }
    } else {
      throw new IllegalArgumentException("Unsupported type for conversion to long: " + obj.getClass());
    }
  }
}
