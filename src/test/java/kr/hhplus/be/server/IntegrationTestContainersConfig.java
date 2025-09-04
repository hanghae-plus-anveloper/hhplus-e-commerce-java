package kr.hhplus.be.server;

import jakarta.annotation.PreDestroy;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration
@Profile("test")
public class IntegrationTestContainersConfig {

	public static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
			.withDatabaseName("hhplus")
			.withUsername("test")
			.withPassword("test");

	public static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.2"))
			.withExposedPorts(6379);

	public static final KafkaContainer KAFKA =
			new KafkaContainer(DockerImageName.parse("apache/kafka:4.0.0"));

	static {
		MYSQL.start();
		REDIS.start();
		KAFKA.start();

		System.setProperty("spring.datasource.url",
				MYSQL.getJdbcUrl() + "?characterEncoding=UTF-8&serverTimezone=UTC");
		System.setProperty("spring.datasource.username", MYSQL.getUsername());
		System.setProperty("spring.datasource.password", MYSQL.getPassword());

		System.setProperty("spring.data.redis.host", REDIS.getHost());
		System.setProperty("spring.data.redis.port", String.valueOf(REDIS.getFirstMappedPort()));

		System.setProperty("spring.kafka.bootstrap-servers", KAFKA.getBootstrapServers());

	}

	@Bean(destroyMethod = "shutdown")
	public RedissonClient redissonClient() {
		String addr = "redis://" + REDIS.getHost() + ":" + REDIS.getFirstMappedPort();
		Config cfg = new Config();
		cfg.useSingleServer()
				.setAddress(addr)
				.setConnectTimeout(10_000)
				.setTimeout(3_000)
				.setRetryAttempts(3)
				.setRetryInterval(1_500)
				.setPingConnectionInterval(1_000)
				.setKeepAlive(true)
				.setTcpNoDelay(true);
		return Redisson.create(cfg);
	}

	@PreDestroy
	public void shutdown() {
		if (REDIS.isRunning()) REDIS.stop();
		if (MYSQL.isRunning()) MYSQL.stop();
		if (KAFKA.isRunning()) KAFKA.stop();
	}
}
