package com.haolo.ticketing.transfers;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.shared.ApiException;
import com.haolo.ticketing.transfers.TicketTransferModels.*;

@RestController
@RequestMapping("/api")
public class TicketTransferController {

    private final TicketTransferService transferService;

    public TicketTransferController(TicketTransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/transfers/initiate")
    public ResponseEntity<TransferItemDto> initiateTransfer(
        @AuthenticationPrincipal DemoPrincipal principal,
        @RequestBody InitiateTransferRequest request
    ) {
        return ResponseEntity.ok(transferService.initiateTransfer(principal, request));
    }

    @GetMapping("/transfers/lookup/{token}")
    public ResponseEntity<TransferDetailDto> lookupTransfer(
        @PathVariable String token
    ) {
        return ResponseEntity.ok(transferService.getTransferByToken(token));
    }

    @PostMapping("/transfers/accept/{token}")
    public ResponseEntity<AcceptTransferResponse> acceptTransfer(
        @AuthenticationPrincipal DemoPrincipal principal,
        @PathVariable String token
    ) {
        return ResponseEntity.ok(transferService.acceptTransfer(principal, token));
    }

    @PostMapping("/transfers/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelTransfer(
        @AuthenticationPrincipal DemoPrincipal principal,
        @PathVariable UUID id
    ) {
        transferService.cancelTransfer(principal, id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Đã hủy giao dịch chuyển tặng vé"));
    }

    @GetMapping("/me/transfers/sent")
    public ResponseEntity<Map<String, Object>> getSentTransfers(
        @AuthenticationPrincipal DemoPrincipal principal
    ) {
        if (principal == null) {
            throw new ApiException(401, "UNAUTHENTICATED", "Yêu cầu đăng nhập");
        }
        List<TransferItemDto> items = transferService.getSentTransfers(principal.accountId());
        return ResponseEntity.ok(Map.of("items", items, "total", items.size()));
    }

    @GetMapping("/me/transfers/received")
    public ResponseEntity<Map<String, Object>> getReceivedTransfers(
        @AuthenticationPrincipal DemoPrincipal principal
    ) {
        if (principal == null) {
            throw new ApiException(401, "UNAUTHENTICATED", "Yêu cầu đăng nhập");
        }
        List<TransferItemDto> items = transferService.getReceivedTransfers(principal.accountId());
        return ResponseEntity.ok(Map.of("items", items, "total", items.size()));
    }
}
