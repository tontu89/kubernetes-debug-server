package io.github.tontu89.debugclientagent.config;

import io.github.tontu89.debugclientagent.utils.SecurityUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/**
 * SSL resource loader (TODO: generate new ssl config when it is first use)
 * @author citestra
 *
 */
public class SSLConfig {

	/**
	 * SSL resources
	 */
	public final static String SSL_PATH = "/CA/";
	
	/**
	 * CA key
	 */
	private PrivateKey caKey;

	/**
	 * CA Cert
	 */
	private X509Certificate caCert;

	/**
	 * Cert Key
	 */
	private PrivateKey certKey;

	/**
	 * Website cert certificates
	 */
	private Path certsFolder;
	
	/**
	 * Constructor
	 * @param root folder
	 */
	public SSLConfig() {
		try {
			/* CA KEY */
			this.caKey = SecurityUtils.loadPrivateKey(SSLConfig.class.getResourceAsStream(SSL_PATH + "ca.key"));
			
			/* CA CERT */
			this.caCert = SecurityUtils.loadX509Certificate(SSLConfig.class.getResourceAsStream(SSL_PATH + "ca.crt"));
			
			/* CERT KEY */
			this.certKey = SecurityUtils.loadPrivateKey(SSLConfig.class.getResourceAsStream(SSL_PATH + "cert.key"));
			
			/* CERTS FOLDER */
			this.certsFolder = Files.exists(Paths.get("certs")) ? Paths.get("certs") : Files.createDirectory(Paths.get("certs"));
			
		} catch (IOException | GeneralSecurityException e) {
			System.out.println("Couldn't load Keys/Certificate from filesystem");
			e.printStackTrace();
		}
	}
	
	public boolean isAllSet() {
		return this.caKey != null && this.caCert != null && this.certKey != null && this.certsFolder != null;
	}
	
	///////////////////////////////////////// GETTERS \\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\
	public PrivateKey getCaKey() {
		return caKey;
	}

	public X509Certificate getCaCert() {
		return caCert;
	}

	public PrivateKey getCertKey() {
		return certKey;
	}

	public Path getCertsFolder() {
		return certsFolder;
	}
}
