package com.fajtech.sppotracker.infrastructure.config;

import com.fajtech.sppotracker.application.port.out.CurrentSnapshotStorePort;
import com.fajtech.sppotracker.application.port.out.RouteDeviationHistoryPort;
import com.fajtech.sppotracker.application.query.CurrentVehiclePositionsService;
import com.fajtech.sppotracker.application.route.RouteDeviationHistoryService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring dos casos de uso de consulta (REST). Mantém a camada de aplicação livre
 * de framework.
 */
@Configuration
public class QueryConfig {

    @Bean
    public CurrentVehiclePositionsService currentVehiclePositionsService(CurrentSnapshotStorePort snapshotStore) {
        return new CurrentVehiclePositionsService(snapshotStore);
    }

    @Bean
    public RouteDeviationHistoryService routeDeviationHistoryService(RouteDeviationHistoryPort historyPort) {
        return new RouteDeviationHistoryService(historyPort);
    }
}
