package io.github.tontu89.debugclientagent.utils;

import io.github.tontu89.debugclientagent.config.AppConfig;
import io.github.tontu89.debugclientagent.config.SSLConfig;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.github.tontu89.debugserverlib.utils.Constants.SPRING_PROFILE_NAME;

@Slf4j
public class SecurityUtils {
	private static Pattern ipPattern = Pattern.compile("^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$");
	/**
	 * Create cert for hostname using my CA
	 * @param hostname
	 * @param certFile
	 * @param sslResource
	 * @throws Exception
	 */
	public static void createHostCert(
			String hostname,
			String certFile,
			SSLConfig sslResource) throws Exception {

		Security.addProvider(new BouncyCastleProvider());

		// Get public key from private key
		RSAPrivateCrtKey privk = (RSAPrivateCrtKey)sslResource.getCertKey();
		RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(privk.getModulus(), privk.getPublicExponent());
		KeyFactory keyFactory = KeyFactory.getInstance("RSA");
		PublicKey certPublicKey = keyFactory.generatePublic(publicKeySpec);

		// subject name builder.
		X500NameBuilder nameBuilder = new X500NameBuilder();

		nameBuilder.addRDN(BCStyle.CN, hostname);
		
		// create the certificate - version 3
		X509v3CertificateBuilder v3Bldr = new JcaX509v3CertificateBuilder(sslResource.getCaCert(), new BigInteger(32, new SecureRandom()),
				new Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24 * 30), new Date(System.currentTimeMillis() + (1000L * 60 * 60 * 24 * 30)),
				nameBuilder.build(), certPublicKey);

		// extensions
		JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();

		v3Bldr.addExtension(
				Extension.subjectKeyIdentifier,
				false,
				extUtils.createSubjectKeyIdentifier(certPublicKey));

		v3Bldr.addExtension(
				Extension.authorityKeyIdentifier,
				false,
				extUtils.createAuthorityKeyIdentifier(sslResource.getCaCert()));

		GeneralName generalName = null;

		if (ipPattern.matcher(hostname).matches()) {
			generalName = new GeneralName(GeneralName.iPAddress, hostname);
		} else {
			generalName = new GeneralName(GeneralName.dNSName, hostname);
		}
		ASN1Encodable[] subjectAlternativeNames = new ASN1Encodable[]
			    {
						generalName
			    };
		
		v3Bldr.addExtension(
				Extension.subjectAlternativeName, 
				false, 
				new DERSequence(subjectAlternativeNames));

		X509CertificateHolder certHldr = v3Bldr.build(new JcaContentSignerBuilder("SHA256WithRSA").setProvider("BC").build(sslResource.getCaKey()));

		X509Certificate cert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(certHldr);

		cert.checkValidity(new Date());

		cert.verify(sslResource.getCaCert().getPublicKey());

		Certificate[] chain = new Certificate[2];
		chain[1] = (Certificate) sslResource.getCaCert();
		chain[0] = (Certificate) cert;

		KeyStore store = KeyStore.getInstance("PKCS12", "BC");
		store.load(null, null);
		store.setKeyEntry(hostname, sslResource.getCertKey(), null, chain);
		try (FileOutputStream fOut = new FileOutputStream(certFile)) {
			store.store(fOut, "secret".toCharArray());
			fOut.close();
		} catch(Exception e) {
			System.out.println(e.getMessage());
		}
		
	}
	
	/**
	 * 
	 * @param pubKeyFile public key file
	 * @return Public key
	 * @throws GeneralSecurityException
	 * @throws IOException
	 */
	public static Key loadPublicKey(File pubKeyFile) throws GeneralSecurityException, IOException {
		String pubKeyStr = new String(Files.readAllBytes(pubKeyFile.toPath()));
		byte[] data = Base64.getDecoder().decode((pubKeyStr.getBytes()));
		X509EncodedKeySpec spec = new X509EncodedKeySpec(data);
		KeyFactory fact = KeyFactory.getInstance("RSA");
		return fact.generatePublic(spec);
	}

	/**
	 * Load Private key from file
	 * @param privKeyFile private key File
	 * @return private key
	 * @throws GeneralSecurityException
	 * @throws IOException
	 */
	public static PrivateKey loadPrivateKey(File privKeyFile) throws GeneralSecurityException, IOException {
		String privKeyStr = new String(Files.readAllBytes(privKeyFile.toPath()));
		byte[] clear = Base64.getDecoder().decode(
				privKeyStr.replace("-----BEGIN PRIVATE KEY-----", "")
						  .replace("-----END PRIVATE KEY-----", "")
						  .replaceAll("\\n",  "")
						  .getBytes());
		PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(clear);
		KeyFactory fact = KeyFactory.getInstance("RSA");
		PrivateKey priv = fact.generatePrivate(keySpec);
		Arrays.fill(clear, (byte) 0);
		return priv;
	}
	
	/**
	 * Load X509 Certificate from file
	 * @param certFile certificate file
	 * @return X509Certificate
	 * @throws CertificateException
	 * @throws IOException
	 */
	public static X509Certificate loadX509Certificate(File certFile) throws CertificateException, IOException {
		String certStr = new String(Files.readAllBytes(certFile.toPath()));
		CertificateFactory cf = CertificateFactory.getInstance("X.509");
		return (X509Certificate) cf.generateCertificate(
				new ByteArrayInputStream(Base64.getDecoder().decode(
											certStr.replace("-----BEGIN CERTIFICATE-----", "")
												   .replace("-----END CERTIFICATE-----", "")
												   .replaceAll("\\n",  "")
				)));
	}

	/**
	 * Load Private key from Inputstream
	 * @param resourceAsStream resource stream
	 * @return private key
	 * @throws NoSuchAlgorithmException
	 * @throws InvalidKeySpecException
	 */
	public static PrivateKey loadPrivateKey(InputStream resourceAsStream) throws NoSuchAlgorithmException, InvalidKeySpecException {
		String privKeyStr = new BufferedReader(new InputStreamReader(resourceAsStream))
				  .lines().collect(Collectors.joining("\n"));
		byte[] clear = Base64.getDecoder().decode(
				privKeyStr.replace("-----BEGIN PRIVATE KEY-----", "")
						  .replace("-----END PRIVATE KEY-----", "")
						  .replaceAll("\\n",  "")
						  .getBytes());
		PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(clear);
		KeyFactory fact = KeyFactory.getInstance("RSA");
		PrivateKey priv = fact.generatePrivate(keySpec);
		Arrays.fill(clear, (byte) 0);
		return priv;
	}

	/**
	 * Load X509 Certificate from Inputstream
	 * @param resourceAsStream resource stream
	 * @return X509Certificate
	 * @throws CertificateException
	 */
	public static X509Certificate loadX509Certificate(InputStream resourceAsStream) throws CertificateException {
		String certStr = new BufferedReader(new InputStreamReader(resourceAsStream))
				  .lines().collect(Collectors.joining("\n"));
		CertificateFactory cf = CertificateFactory.getInstance("X.509");
		return (X509Certificate) cf.generateCertificate(
				new ByteArrayInputStream(Base64.getDecoder().decode(
											certStr.replace("-----BEGIN CERTIFICATE-----", "")
												   .replace("-----END CERTIFICATE-----", "")
												   .replaceAll("\\n",  "")
				)));
	}

	public static void setupTrustStore() throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, CertificateException, IOException {
		SSLContext.getInstance("SSL").init( null, null, null );

		InputStream stream = SecurityUtils.class.getResourceAsStream(SSLConfig.SSL_PATH + "myTrustStore");

		KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());

		char[] trustStorePassword = "myTrustStore".toCharArray();

		trustStore.load(stream, trustStorePassword);

		SSLContext context = SSLContext.getInstance("SSL");

		TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

		factory.init(trustStore);

		TrustManager[] managers = factory.getTrustManagers();
		context.init(null, managers, null);

		SSLContext.setDefault(context);

	}

	public static void setupProxy(String port) {
		System.setProperty("http.proxyHost", "localhost");
		System.setProperty("http.proxyPort", port);
		System.setProperty("https.proxyHost", "localhost");
		System.setProperty("https.proxyPort", port);
		System.setProperty("https.proxy", "https://localhost:" + port); // Spring kubernetes
		System.setProperty("http.proxy", "http://localhost:" + port); // Spring kubernetes
		modifyApacheHttpClientBuilderToAlwaysSupportProxy();
	}

	public static void setEnvironments(Map<String, String> newEnvironments) throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
		Class<?> clazz = Class.forName("java.lang.ProcessEnvironment");
		Field theUnmodifiableEnvironment = clazz.getDeclaredField("theUnmodifiableEnvironment");
		theUnmodifiableEnvironment.setAccessible(true);

		Class<?> unmodifiableMapClazz = Class.forName("java.util.Collections$UnmodifiableMap");
		Field field = unmodifiableMapClazz.getDeclaredField("m");
		field.setAccessible(true);

		Map<String, String> currentSystemEnv = (Map<String, String>) field.get(theUnmodifiableEnvironment.get(null));
		newEnvironments.forEach((key, value) -> {
			currentSystemEnv.put(key, modifyProperty(key, value));
		});
	}

	public static void setSystemProperties(Map<String, String> newSystemProperties) {
		if (newSystemProperties != null) {
			newSystemProperties.forEach((k, v) -> System.setProperty(k, modifyProperty(k, v)));
		}
	}

	private static String modifyProperty(String key, String value) {
		if (key.equalsIgnoreCase("spring.profiles.active")) {
			String[] profiles = value.split(",");
			value = Arrays.stream(profiles).dropWhile(p -> SPRING_PROFILE_NAME.equals(p)).collect(Collectors.joining());
		}
		return value;
	}

	private static void modifyApacheHttpClientBuilderToAlwaysSupportProxy() {
		ClassPool classPool = ClassPool.getDefault();

		try {
			CtClass ctClass = classPool.get("org.apache.http.impl.client.HttpClientBuilder");

			CtMethod ctMethod = ctClass.getDeclaredMethod("build");
			ctMethod.insertBefore("systemProperties = true;");

			ctClass.toClass();
		} catch (NotFoundException e) {
			log.warn("Cannot find class org.apache.http.impl.client.HttpClientBuilder");
		} catch (CannotCompileException e) {
			log.warn("Cannot find modify org.apache.http.impl.client.HttpClientBuilder.build");
		}
	}
}
