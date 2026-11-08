import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.*;
import org.bouncycastle.cert.jcajce.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.*;

public class CryptoProbe {
  static final Provider BC = new BouncyCastleProvider();

  static X509Certificate certificate(
      String subject,
      String issuer,
      KeyPair key,
      KeyPair issuerKey,
      boolean ca,
      GeneralName san,
      NameConstraints nc)
      throws Exception {
    var builder =
        new JcaX509v3CertificateBuilder(
            new X500Name(issuer),
            BigInteger.valueOf(System.nanoTime()).abs(),
            new Date(System.currentTimeMillis() - 60000),
            new Date(System.currentTimeMillis() + 3600000),
            new X500Name(subject),
            key.getPublic());
    builder.addExtension(
        org.bouncycastle.asn1.x509.Extension.basicConstraints, true, new BasicConstraints(ca));
    builder.addExtension(
        org.bouncycastle.asn1.x509.Extension.keyUsage,
        true,
        new KeyUsage(ca ? KeyUsage.keyCertSign | KeyUsage.cRLSign : KeyUsage.digitalSignature));
    if (san != null)
      builder.addExtension(
          org.bouncycastle.asn1.x509.Extension.subjectAlternativeName,
          false,
          new GeneralNames(san));
    if (nc != null)
      builder.addExtension(org.bouncycastle.asn1.x509.Extension.nameConstraints, true, nc);
    var signer =
        new JcaContentSignerBuilder("SHA256withRSA").setProvider(BC).build(issuerKey.getPrivate());
    var cert =
        new JcaX509CertificateConverter().setProvider(BC).getCertificate(builder.build(signer));
    cert.verify(issuerKey.getPublic());
    return cert;
  }

  static byte[] sequence(byte[] child) throws Exception {
    var b = new ByteArrayOutputStream();
    b.write(0x30);
    if (child.length < 128) b.write(child.length);
    else {
      b.write(0x82);
      b.write(child.length >> 8);
      b.write(child.length);
    }
    b.write(child);
    return b.toByteArray();
  }

  public static void main(String[] args) throws Exception {
    boolean fixed = args[0].equals("fixed");
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair rootKey = generator.generateKeyPair(),
        intermediateKey = generator.generateKeyPair(),
        leafKey = generator.generateKeyPair();
    var root = certificate("CN=Root", "CN=Root", rootKey, rootKey, true, null, null);
    for (int tag : new int[] {GeneralName.rfc822Name, GeneralName.uniformResourceIdentifier}) {
      var constraints =
          new NameConstraints(
              null,
              new GeneralSubtree[] {new GeneralSubtree(new GeneralName(tag, "blocked.test"))});
      var intermediate =
          certificate(
              "CN=Intermediate", "CN=Root", intermediateKey, rootKey, true, null, constraints);
      for (String host : new String[] {"allowed.test", "blocked.test", "blocked.test."}) {
        String name = tag == GeneralName.rfc822Name ? "probe@" + host : "https://" + host + "/";
        var leaf =
            certificate(
                "CN=Leaf",
                "CN=Intermediate",
                leafKey,
                intermediateKey,
                false,
                new GeneralName(tag, name),
                null);
        var path =
            CertificateFactory.getInstance("X.509", BC)
                .generateCertPath(List.of(leaf, intermediate));
        var parameters = new PKIXParameters(Set.of(new TrustAnchor(root, null)));
        parameters.setRevocationEnabled(false);
        boolean accepted;
        try {
          CertPathValidator.getInstance("PKIX", BC).validate(path, parameters);
          accepted = true;
        } catch (CertPathValidatorException expected) {
          accepted = false;
        }
        boolean expected = host.equals("allowed.test") || (!fixed && host.endsWith("."));
        if (accepted != expected)
          throw new AssertionError("name constraint " + name + " accepted=" + accepted);
        System.out.println("PKIX tag=" + tag + " host=" + host + " accepted=" + accepted);
      }
    }
    byte[] nested = new byte[] {0x30, 0};
    for (int i = 0; i < 256; i++) nested = sequence(nested);
    boolean rejected = false;
    try (var input = new ASN1InputStream(nested, true)) {
      input.readObject().hashCode();
    } catch (ASN1ParsingException | IOException expected) {
      rejected = true;
    }
    if (rejected != fixed) throw new AssertionError("lazy depth rejected=" + rejected);
    System.out.println("lazy ASN.1 depth 257 rejected=" + rejected);
    var signer =
        new JcaContentSignerBuilder("SHA256withRSA").setProvider(BC).build(rootKey.getPrivate());
    var crlBuilder = new X509v2CRLBuilder(new X500Name("CN=Root"), new Date());
    crlBuilder.setNextUpdate(new Date(System.currentTimeMillis() + 3600000));
    crlBuilder.addCRLEntry(BigInteger.ONE, new Date(), 0);
    var crl = crlBuilder.build(signer);
    if (!new X509CRLHolder(crl.getEncoded())
        .isSignatureValid(
            new JcaContentVerifierProviderBuilder().setProvider(BC).build(rootKey.getPublic())))
      throw new AssertionError("CRL signature");
    for (String type : List.of("JKS", "PKCS12")) {
      var store = KeyStore.getInstance(type);
      store.load(null, null);
      store.setKeyEntry(
          "probe",
          rootKey.getPrivate(),
          "disposable".toCharArray(),
          new java.security.cert.Certificate[] {root});
      var bytes = new ByteArrayOutputStream();
      store.store(bytes, "disposable".toCharArray());
      var copy = KeyStore.getInstance(type);
      copy.load(new ByteArrayInputStream(bytes.toByteArray()), "disposable".toCharArray());
      if (!copy.getCertificate("probe").equals(root)) throw new AssertionError(type);
    }
    System.out.println(
        "certificate, CRL signature, JKS and PKCS12 round trips passed; BC=" + BC.getVersionStr());
  }
}
