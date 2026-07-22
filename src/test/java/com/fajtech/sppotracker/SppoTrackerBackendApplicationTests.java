package com.fajtech.sppotracker;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Smoke de contexto: valida que TODO o wiring da aplicação sobe (portas, configs,
 * beans de aplicação instanciados pela infra, controllers, WebSocket, métricas),
 * sem depender de Redis/Postgres reais.
 *
 * <p>Redis/Postgres e Flyway são desligados por auto-configuração excluída, e os
 * poucos beans de infraestrutura que dependeriam de conexão real são mockados.
 * O polling é desligado para o scheduler não disparar. A semântica real de
 * Redis/Postgres é coberta pelos testes de integração Testcontainers (*IT, {@code mvn verify}).
 */
@SpringBootTest(
        properties = {
                "gps.polling.enabled=false",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
                        + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration,"
                        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                        + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"
        })
class SppoTrackerBackendApplicationTests {

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockitoBean
    private RedisMessageListenerContainer redisMessageListenerContainer;

    @Test
    void contextLoads() {
        // O contexto subir sem erro é a asserção.
    }
}
