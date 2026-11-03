import com.android.ide.common.signing.KeystoreHelper;
import com.android.tools.build.bundletool.transparency.CodeTransparencyCryptoUtils;
import com.android.tools.build.jetifier.processor.transform.pom.XmlUtils;
import com.google.common.io.ByteSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jose4j.jwe.*;
import org.jose4j.jws.*;
import org.jose4j.keys.AesKey;
import org.jose4j.lang.JoseException;

public class OwnerProbe {
  @SuppressWarnings("unchecked")
  static com.android.utils.Pair<java.security.PrivateKey, java.security.cert.X509Certificate>
      generate(String algorithm, String signature, int years, String name, int bits)
          throws Exception {
    var method =
        KeystoreHelper.class.getDeclaredMethod(
            "generateKeyAndCertificate",
            String.class,
            String.class,
            int.class,
            String.class,
            int.class);
    method.setAccessible(true);
    return (com.android.utils.Pair<java.security.PrivateKey, java.security.cert.X509Certificate>)
        method.invoke(null, algorithm, signature, years, name, bits);
  }

  public static void main(String[] args) throws Exception {
    boolean fixed = args[0].equals("fixed");
    Path dir = Files.createTempDirectory("wallcrawl-benign-tooling-");
    try {
      for (String type : List.of("JKS", "PKCS12")) {
        var file = dir.resolve(type + ".keystore").toFile();
        if (!KeystoreHelper.createNewStore(
            type, file, "disposable", "disposable", "probe", "CN=Disposable", 1))
          throw new AssertionError("store creation");
        var info =
            KeystoreHelper.getCertificateInfo(type, file, "disposable", "disposable", "probe");
        info.getCertificate().verify(info.getCertificate().getPublicKey());
        try {
          KeystoreHelper.getCertificateInfo(type, file, "wrong", "wrong", "probe");
          throw new AssertionError("wrong password accepted");
        } catch (com.android.ide.common.signing.KeytoolException expected) {
        }
      }
      for (String algorithm : List.of("RSA", "EC")) {
        var pair =
            generate(
                algorithm,
                algorithm.equals("RSA") ? "SHA256withRSA" : "SHA256withECDSA",
                1,
                "CN=Disposable",
                algorithm.equals("RSA") ? 2048 : 256);
        pair.getSecond().verify(pair.getSecond().getPublicKey());
      }
      var pair = generate("RSA", "SHA256withRSA", 1, "CN=Disposable", 2048);
      var jws = new JsonWebSignature();
      jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
      jws.setCertificateChainHeaderValue(pair.getSecond());
      jws.setKey(pair.getFirst());
      jws.setPayload("{\"code_related_file\":[]}");
      String compact = jws.getCompactSerialization();
      var parsed =
          CodeTransparencyCryptoUtils.parseJws(
              ByteSource.wrap(compact.getBytes(StandardCharsets.UTF_8)));
      if (!CodeTransparencyCryptoUtils.verifySignature(parsed))
        throw new AssertionError("JWS roundtrip");
      if (!CodeTransparencyCryptoUtils.getCertificateFingerprint(parsed)
          .equals(CodeTransparencyCryptoUtils.getCertificateFingerprint(pair.getSecond())))
        throw new AssertionError("fingerprint");
      String[] parts = compact.split("\\.");
      parts[1] =
          Base64.getUrlEncoder()
              .withoutPadding()
              .encodeToString("tampered".getBytes(StandardCharsets.UTF_8));
      var tampered =
          CodeTransparencyCryptoUtils.parseJws(
              ByteSource.wrap(String.join(".", parts).getBytes(StandardCharsets.UTF_8)));
      if (CodeTransparencyCryptoUtils.verifySignature(tampered))
        throw new AssertionError("tampered signature accepted");
      try {
        CodeTransparencyCryptoUtils.parseJws(
            ByteSource.wrap("malformed".getBytes(StandardCharsets.UTF_8)));
        throw new AssertionError("malformed JWS accepted");
      } catch (
          com.android.tools.build.bundletool.model.exceptions.CommandExecutionException expected) {
      }
      for (int size : new int[] {64, 204801}) {
        var key = new AesKey(new byte[16]);
        var jwe = new JsonWebEncryption();
        jwe.setKey(key);
        jwe.setAlgorithmHeaderValue(KeyManagementAlgorithmIdentifiers.DIRECT);
        jwe.setEncryptionMethodHeaderParameter(ContentEncryptionAlgorithmIdentifiers.AES_128_GCM);
        jwe.setCompressionAlgorithmHeaderParameter("DEF");
        jwe.setPayload("a".repeat(size));
        var read = new JsonWebEncryption();
        read.setKey(key);
        read.setCompactSerialization(jwe.getCompactSerialization());
        boolean rejected = false;
        try {
          if (read.getPayload().length() != size) throw new AssertionError("payload length");
        } catch (JoseException expected) {
          if (!expected.getMessage().contains("Maximum allowed size")) throw expected;
          rejected = true;
        }
        if (rejected != (fixed && size > 204800))
          throw new AssertionError("JWE decompression limit");
        System.out.println("compressed JWE bytes=" + size + " rejected=" + rejected);
      }
      Path canary = dir.resolve("canary.txt");
      Files.writeString(canary, "TASK_OWNED_CANARY");
      String xml =
          "<!DOCTYPE project [<!ENTITY probe SYSTEM '"
              + canary.toUri()
              + "'>]><project>&probe;</project>";
      var document =
          XmlUtils.Companion.createDocumentFromByteArray(xml.getBytes(StandardCharsets.UTF_8));
      if (!document.getRootElement().getText().equals("TASK_OWNED_CANARY"))
        throw new AssertionError("unexpected Jetifier parser change; reassess");
      System.out.println("Jetifier direct helper expands only task-owned canary; residual remains");
      System.out.println(
          "AGP KeystoreHelper JKS/PKCS12, RSA/EC; bundletool JWS/fingerprint/tamper/malformed"
              + " checks passed");
    } finally {
      try (var files = Files.list(dir)) {
        for (Path f : files.toList()) Files.delete(f);
      }
      Files.delete(dir);
    }
  }
}
