package cz.tomasdvorak.eet.client.security;

import cz.tomasdvorak.eet.client.exceptions.DataSigningException;
import cz.tomasdvorak.eet.client.exceptions.InvalidKeystoreException;
import cz.tomasdvorak.eet.client.utils.StringJoiner;
import org.apache.logging.log4j.Logger;
import org.apache.wss4j.common.crypto.Crypto;
import org.apache.wss4j.common.crypto.Merlin;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Enumeration;

public class ClientKey {

    private static final Logger logger = org.apache.logging.log4j.LogManager.getLogger(ClientKey.class);

    private final KeyStore keyStore;
    private final String password;
    private final String alias;
    private final ClientPasswordCallback clientPasswordCallback;

    public ClientKey(final InputStream inputStream, final String password) throws InvalidKeystoreException {
        this.password = password;
        final KeyStore keystore = getKeyStore(inputStream, password);
        final Enumeration<String> aliases = getAliases(keystore);
        if (aliases.hasMoreElements()) {
            this.alias = aliases.nextElement();
            logger.info("Client certificate serial number: " + getCertificateInfo(keystore, alias));

        } else {
            throw new InvalidKeystoreException("Keystore doesn't contain any keys!");
        }
        this.keyStore = keystore;
        this.clientPasswordCallback = new ClientPasswordCallback(alias, password);
    }

    private String getCertificateInfo(final KeyStore keystore, final String alias) throws InvalidKeystoreException {
        try {
            final X509Certificate cert = (X509Certificate) keystore.getCertificate(alias);
            return StringJoiner.join(", ", Arrays.asList(
                    "" + cert.getSerialNumber(),
                    alias,
                    cert.getIssuerDN().toString()
            ));
        } catch (final KeyStoreException e) {
            throw new InvalidKeystoreException(e);
        }
    }

    private Enumeration<String> getAliases(final KeyStore keystore) throws InvalidKeystoreException {
        try {
            return keystore.aliases();
        } catch (final KeyStoreException e) {
            throw new InvalidKeystoreException(e);
        }
    }

    private KeyStore getKeyStore(final InputStream inputStream, final String password) throws InvalidKeystoreException {
        try {
            final KeyStore keystore = KeyStore.getInstance("pkcs12", new BouncyCastleProvider());
            keystore.load(inputStream, password.toCharArray());
            return keystore;
        } catch (final CertificateException e) {
            throw new InvalidKeystoreException(e);
        } catch (final NoSuchAlgorithmException e) {
            throw new InvalidKeystoreException(e);
        } catch (final KeyStoreException e) {
            throw new InvalidKeystoreException(e);
        } catch (final IOException e) {
            throw new InvalidKeystoreException(e);
        }
    }

    /**
     * Sign provided text with SHA256withRSA initialized by the private key
     */
    public byte[] sign(final String text) throws DataSigningException {
        try {
            final Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(getPrivateKey());
            signature.update(text.getBytes("UTF-8"));
            return signature.sign();
        } catch (final NoSuchAlgorithmException e) {
            throw new DataSigningException(e);
        } catch (final UnrecoverableKeyException e) {
            throw new DataSigningException(e);
        } catch (final InvalidKeyException e) {
            throw new DataSigningException(e);
        } catch (final SignatureException e) {
            throw new DataSigningException(e);
        } catch (final UnsupportedEncodingException e) {
            throw new DataSigningException(e);
        } catch (final KeyStoreException e) {
            throw new DataSigningException(e);
        }
    }

    private PrivateKey getPrivateKey() throws KeyStoreException, UnrecoverableKeyException, NoSuchAlgorithmException {
        return (PrivateKey) this.keyStore.getKey(this.alias, this.password.toCharArray());
    }

    /**
     * Crypto implementation used to sign WS requests
     */
    public Crypto getCrypto() {
        final Merlin merlin = new Merlin();
        merlin.setKeyStore(this.keyStore);
        return merlin;
    }

    /**
     * Get the first (and hopefully the only one) alias included in the keystore bundle
     */
    public String getAlias() {
        return alias;
    }

    /**
     * Callback supplying username / password  combination to the WS signing layer
     */
    public ClientPasswordCallback getClientPasswordCallback() {
        return clientPasswordCallback;
    }
}
