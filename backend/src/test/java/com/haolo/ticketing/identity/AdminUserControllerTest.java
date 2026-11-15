package com.haolo.ticketing.identity;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.haolo.ticketing.shared.ApiException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminUserControllerTest {
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final AdminUserController controller = new AdminUserController(jdbc);
    final AdminLeadController leadController = new AdminLeadController(jdbc);

    @Test
    void invalidPaginationThrowsValidationError() {
        assertThrows(ApiException.class, () -> controller.listUsers(-1, 20, null));
        assertThrows(ApiException.class, () -> controller.listUsers(0, 0, null));
        assertThrows(ApiException.class, () -> controller.listUsers(0, 101, null));

        assertThrows(ApiException.class, () -> leadController.listLeads(-1, 20));
        assertThrows(ApiException.class, () -> leadController.listLeads(0, 0));
        assertThrows(ApiException.class, () -> leadController.listLeads(0, 101));
    }
}
