import java.io.*;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.util.*;

public class GuavaProbe {
  static byte[] serialize(Object value) throws Exception {
    var bytes = new ByteArrayOutputStream();
    try (var out = new ObjectOutputStream(bytes)) {
      out.writeObject(value);
    }
    return bytes.toByteArray();
  }

  static Object deserialize(byte[] bytes) throws Exception {
    try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
      return in.readObject();
    }
  }

  @SuppressWarnings("unchecked")
  public static void main(String[] args) throws Exception {
    if (args.length < 2 || args.length > 3
        || !Set.of("fixed", "inspect").contains(args[1])
        || (args.length == 3 && !args[2].equals("ksp"))) {
      throw new IllegalArgumentException("Usage: GuavaProbe COLLECTION fixed|inspect [ksp]");
    }
    if (Runtime.getRuntime().maxMemory() > 128L * 1024 * 1024) {
      throw new IllegalArgumentException("Run the disposable deserialization probe with -Xmx64m");
    }
    String name = args[0];
    if (!Set.of(
            "CompactHashMap",
            "CompactHashSet",
            "CompactLinkedHashMap",
            "CompactLinkedHashSet",
            "MapMakerInternalMap")
        .contains(name)) {
      throw new IllegalArgumentException("Unknown probe collection");
    }
    Object value;
    String namespace = args.length == 3 ? "ksp.com.google.common.collect." : "com.google.common.collect.";
    if (name.equals("MapMakerInternalMap")) {
      var maker =
          Class.forName(namespace + "MapMaker").getConstructor().newInstance();
      maker.getClass().getMethod("weakKeys").invoke(maker);
      value = maker.getClass().getMethod("makeMap").invoke(maker);
    } else {
      var constructor = Class.forName(namespace + name).getDeclaredConstructor();
      constructor.setAccessible(true);
      value = constructor.newInstance();
    }
    if (value instanceof Map) ((Map<Object, Object>) value).put("key", "value");
    else ((Set<Object>) value).add("key");
    byte[] normal = serialize(value);
    Object roundTrip = deserialize(normal);
    if ((roundTrip instanceof Map ? ((Map<?, ?>) roundTrip).size() : ((Set<?>) roundTrip).size())
        != 1) throw new AssertionError("round trip");
    byte[] payload = normal.clone();
    int offset = -1;
    for (int i = 0; i < payload.length - 6; i++) {
      if (payload[i] == 0x77
          && payload[i + 1] == 4
          && ByteBuffer.wrap(payload, i + 2, 4).getInt() == 1) {
        if (offset != -1) throw new AssertionError("ambiguous size field");
        offset = i + 2;
      }
    }
    if (offset < 0) throw new AssertionError("size field not found");
    ByteBuffer.wrap(payload, offset, 4).putInt(1 << 28);
    String result;
    try {
      deserialize(payload);
      result = "bounded-success";
    } catch (OutOfMemoryError expected) {
      result = "unbounded-allocation";
    } catch (IOException expected) {
      result = "bounded-rejection:" + expected.getClass().getSimpleName();
    }
    System.out.println(
        name
            + " benign-roundtrip=pass malformed="
            + result
            + " origin="
            + value.getClass().getProtectionDomain().getCodeSource().getLocation());
    if (args[1].equals("fixed") && result.equals("unbounded-allocation"))
      throw new AssertionError("vulnerable allocation");
  }
}
