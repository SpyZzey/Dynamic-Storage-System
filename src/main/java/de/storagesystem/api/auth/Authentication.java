package de.storagesystem.api.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import de.storagesystem.api.exceptions.InvalidTokenException;
import io.github.cdimascio.dotenv.Dotenv;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Map;

/**
 * @author Simon Brebeck
 */
@Component
public class Authentication {

    private static final Logger logger = LogManager.getLogger(Authentication.class);

    /**
     * An {@link RSAPublicKey} instance that is used to verify the signature of the JWT.
     */
    private final RSAPublicKey publicKey;
    /**
     * An {@link RSAPrivateKey} used to sign the JWTs.
     */
    private final RSAPrivateKey privateKey;

    /**
     * Creates a new Authentication object.
     *
     * @throws IOException if the public or private key file could not be read from the key path stored in .env
     * @throws NoSuchAlgorithmException if the RSA algorithm is not supported by the system
     * @throws InvalidKeySpecException if the public or private key file is not a valid RSA key
     */
    public Authentication()
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        this.publicKey = getRSAPublicKey(getPathToPublicKey());
        this.privateKey = getRSAPrivateKey(getPathToPrivateKey());
    }

    /**
     * Creates a new Authentication object with publicKeyPath and
     * privateKeyPath as paths to the private/public encryption key.
     *
     * @param publicKeyPath path to the public key
     * @param privateKeyPath path to the private key
     * @throws IOException if the public or private key file could not be read from the  key path stored in .env
     * @throws NoSuchAlgorithmException if the RSA algorithm is not supported by the system
     * @throws InvalidKeySpecException if the public or private key file is not a valid RSA key
     */
    public Authentication(String publicKeyPath, String privateKeyPath)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        this.publicKey = getRSAPublicKey(publicKeyPath);
        this.privateKey = getRSAPrivateKey(privateKeyPath);
    }

    /**
     * Creates a new Authentication object with publicKey and
     * privateKey as the private/public encryption key.
     *
     * @param publicKey public key
     * @param privateKey private key
     */
    public Authentication(RSAPublicKey publicKey, RSAPrivateKey privateKey) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
    }


    /**
     * Extracts the token from the bearer string.
     *
     * @param bearer The bearer string.
     * @throws InvalidTokenException if the token is invalid
     * @return String The token.
     */
    public String extractTokenFromBearer(String bearer) throws InvalidTokenException {
        if(bearer == null) throw new InvalidTokenException("Bearer is null");
        if(!bearer.startsWith("Bearer ")) throw new InvalidTokenException("Token is not a bearer token");

        return bearer.substring(7);
    }

    /**
     * Creates, signs and returns a new RSA256 JSON Web Token with the public key stored at the path given by the
     * environment variable "PUBLIC_KEY_PATH" and the private key stored at the path given by the environment variable
     * "PRIVATE_KEY_PATH". The issuer of the token is StorageSystem.
     * @return String - RSA256 JSON Web Token
     */
    public String createToken(Map<String, ?> payload) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        Dotenv env = Dotenv.load();

        Algorithm algorithm = Algorithm.RSA256(publicKey, privateKey);
        return JWT.create()
                .withIssuer(env.get("TOKEN_ISSUER"))
                .withPayload(payload)
                .sign(algorithm);
    }

    /**
     * Verify a given token with the public key stored at the path given by the environment variable "PUBLIC_KEY_PATH"
     * and the private key stored at the path given by the environment variable "PRIVATE_KEY_PATH".
     * The issuer must be "StorageSystem", otherwise the token is invalid.
     * @param token The token to verify
     * @return DecodedJWT if token is valid, null if token is invalid
     * @throws IOException if key file is not found
     * @throws NoSuchAlgorithmException if the algorithm is not supported
     * @throws InvalidKeySpecException if the key is invalid
     * @throws JWTVerificationException if token is invalid
     */
    public DecodedJWT verifyToken(String token) throws
            IOException,
            NoSuchAlgorithmException,
            InvalidKeySpecException,
            JWTVerificationException {

        RSAPublicKey publicKey = getRSAPublicKey(getPathToPublicKey());
        RSAPrivateKey privateKey = getRSAPrivateKey(getPathToPrivateKey());

        Dotenv env = Dotenv.load();
        Algorithm algorithm = Algorithm.RSA256(publicKey, privateKey);
        return JWT.require(algorithm)
                .withIssuer(env.get("TOKEN_ISSUER"))
                .build()
                .verify(token);
    }


    /**
     * Loads a RSA private key from a file
     * @param keyPath the path to the key file
     * @return RSAKey the key from the file in keyPath
     * @throws IOException if the key file could not be read
     * @throws NoSuchAlgorithmException if the algorithm is not supported
     * @throws InvalidKeySpecException if the key specification in the file in keyPath is invalid
     */
    public RSAPublicKey getRSAPublicKey(String keyPath) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        X509EncodedKeySpec ks =  new X509EncodedKeySpec(readKeyBytes(keyPath));
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) kf.generatePublic(ks);
    }

    /**
     * Loads a RSA private key from a file
     * @param keyPath the path to the key file
     * @return RSAKey the key from the file in keyPath
     * @throws IOException if the key file could not be read
     * @throws NoSuchAlgorithmException if the algorithm is not supported
     * @throws InvalidKeySpecException if the key specification in the file in keyPath is invalid
     */
    public RSAPrivateKey getRSAPrivateKey(String keyPath) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        PKCS8EncodedKeySpec ks =  new PKCS8EncodedKeySpec(readKeyBytes(keyPath));
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return (RSAPrivateKey) kf.generatePrivate(ks);
    }

    /**
     * Returns the bytes of a given key file
     * @param keyPath the path to the key file
     * @throws IOException if the key file could not be read
     * @return byte[] the bytes of the key file
     */
    private byte[] readKeyBytes(String keyPath) throws IOException {
        Path path = Paths.get(keyPath);
        return Files.readAllBytes(path);
    }

    /**
     * Generates an RSA key pair and saves it to the given path.
     * @param publicKeyPath The path to save the key public key to.
     * @param privateKeyPath The path to save the key private key to.
     * @throws NoSuchAlgorithmException If the RSA algorithm is not supported.
     * @throws IOException If the key pair could not be saved.
     */
    public static void createRSAKey(String publicKeyPath, String privateKeyPath) throws NoSuchAlgorithmException, IOException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        storeKeyInFile(publicKeyPath, kp.getPublic());
        storeKeyInFile(privateKeyPath, kp.getPrivate());
    }


    /**
     * Stores a given key in a file located at the given path.
     * @param path The path where the key should be stored.
     * @param key The key to be stored.
     * @throws IOException If the key could not be stored.
     */
    private static void storeKeyInFile(String path, Key key) throws IOException {
        File file = new File(path);
        file.getParentFile().mkdirs();

        FileOutputStream out = new FileOutputStream(path);
        out.write(key.getEncoded());
        out.close();
    }

    /**
     * Returns the path to the public key
     * @return String path to public key
     */
    public static String getPathToPublicKey() {
        Dotenv dotenv = Dotenv.load();
        return dotenv.get("PATH_PUBLIC_KEY");
    }

    /**
     * Returns the path to the private key
     * @return String path to private key
     */
    public static String getPathToPrivateKey() {
        Dotenv dotenv = Dotenv.load();
        return dotenv.get("PATH_PRIVATE_KEY");
    }

}
