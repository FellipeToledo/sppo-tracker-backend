package com.fajtech.sppotracker.infrastructure.adapter.out.gtfs;

import com.fajtech.sppotracker.domain.vehicle.Coordinates;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EncodedPolylineTest {

    @Test
    void shouldDecodeGoogleReferenceExample() {
        // Exemplo canônico da documentação do Google Encoded Polyline.
        List<Coordinates> points = EncodedPolyline.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@");

        assertThat(points).hasSize(3);
        assertThat(points.get(0).latitude()).isEqualByComparingTo("38.50000");
        assertThat(points.get(0).longitude()).isEqualByComparingTo("-120.20000");
        assertThat(points.get(1).latitude()).isEqualByComparingTo("40.70000");
        assertThat(points.get(1).longitude()).isEqualByComparingTo("-120.95000");
        assertThat(points.get(2).latitude()).isEqualByComparingTo("43.25200");
        assertThat(points.get(2).longitude()).isEqualByComparingTo("-126.45300");
    }

    @Test
    void shouldReturnEmptyForNullOrEmptyInput() {
        assertThat(EncodedPolyline.decode(null)).isEmpty();
        assertThat(EncodedPolyline.decode("")).isEmpty();
    }
}
