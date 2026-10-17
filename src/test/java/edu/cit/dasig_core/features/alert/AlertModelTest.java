package edu.cit.dasig_core.features.alert;

import edu.cit.dasig_core.features.alert.model.Alert;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AlertModelTest {

    @Test
    void newAlert_isOpenAndUnacknowledged() {
        Alert alert = new Alert();

        assertThat(alert.getStatus()).isEqualTo(Alert.STATUS_UNACKNOWLEDGED);
        assertThat(alert.isOpen()).isTrue();
        assertThat(alert.isResolved()).isFalse();
    }

    @Test
    void acknowledgedAlert_isStillOpen() {
        Alert alert = new Alert();
        alert.setStatus(Alert.STATUS_ACKNOWLEDGED);

        assertThat(alert.isOpen()).isTrue();
    }

    @Test
    void resolvedAlert_isNotOpen() {
        Alert alert = new Alert();
        alert.setStatus(Alert.STATUS_RESOLVED);

        assertThat(alert.isOpen()).isFalse();
        assertThat(alert.isResolved()).isTrue();
    }
}
