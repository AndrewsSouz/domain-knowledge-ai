package com.domainknowledge.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Storage compatível com S3 usado nos testes, a mesma imagem do docker compose.
 */
public class RustFsContainer extends GenericContainer<RustFsContainer> {

    public static final String IMAGE = "rustfs/rustfs:1.0.4";
    public static final String ACCESS_KEY = "test-access";
    public static final String SECRET_KEY = "test-secret-key";

    private static final int S3_PORT = 9000;

    public RustFsContainer() {
        super(DockerImageName.parse(IMAGE));
        withEnv("RUSTFS_ACCESS_KEY", ACCESS_KEY);
        withEnv("RUSTFS_SECRET_KEY", SECRET_KEY);
        withEnv("RUSTFS_ADDRESS", ":" + S3_PORT);
        withExposedPorts(S3_PORT);
        waitingFor(Wait.forHttp("/health").forPort(S3_PORT).forStatusCode(200));
    }

    public String s3Endpoint() {
        return "http://" + getHost() + ":" + getMappedPort(S3_PORT);
    }
}
