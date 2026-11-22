package com.haolo.ticketing.resale;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.haolo.ticketing.identity.DemoPrincipal;
import com.haolo.ticketing.resale.ResaleListingModels.*;
import com.haolo.ticketing.shared.ApiException;

@RestController
@RequestMapping("/api")
public class ResaleListingController {

    private final ResaleListingService resaleService;

    public ResaleListingController(ResaleListingService resaleService) {
        this.resaleService = resaleService;
    }

    @PostMapping("/resale/listings")
    public ResponseEntity<ResaleListingDto> createListing(
        @AuthenticationPrincipal DemoPrincipal principal,
        @RequestBody CreateResaleListingRequest request
    ) {
        return ResponseEntity.ok(resaleService.createListing(principal, request));
    }

    @GetMapping("/events/{eventId}/resale-listings")
    public ResponseEntity<Map<String, Object>> listActiveListings(
        @PathVariable UUID eventId
    ) {
        List<ResaleListingDto> items = resaleService.listActiveListings(eventId);
        return ResponseEntity.ok(Map.of("items", items, "total", items.size()));
    }

    @PostMapping("/resale/listings/{id}/reserve")
    public ResponseEntity<ReserveResaleResponse> reserveListing(
        @AuthenticationPrincipal DemoPrincipal principal,
        @PathVariable UUID id
    ) {
        return ResponseEntity.ok(resaleService.reserveListing(principal, id));
    }

    @PostMapping("/resale/listings/{id}/settle")
    public ResponseEntity<SettleResaleResponse> settlePurchase(
        @AuthenticationPrincipal DemoPrincipal principal,
        @PathVariable UUID id
    ) {
        return ResponseEntity.ok(resaleService.settlePurchase(principal, id));
    }

    @PostMapping("/resale/listings/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelListing(
        @AuthenticationPrincipal DemoPrincipal principal,
        @PathVariable UUID id
    ) {
        return ResponseEntity.ok(resaleService.cancelListing(principal, id));
    }

    @GetMapping("/me/resale/listings")
    public ResponseEntity<Map<String, Object>> getMyListings(
        @AuthenticationPrincipal DemoPrincipal principal
    ) {
        if (principal == null) {
            throw new ApiException(401, "UNAUTHENTICATED", "Yêu cầu đăng nhập");
        }
        List<ResaleListingDto> items = resaleService.getMyListings(principal);
        return ResponseEntity.ok(Map.of("items", items, "total", items.size()));
    }
}
