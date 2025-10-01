package tools.vitruv.methodologist.vsum.controller;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.vitruv.methodologist.vsum.service.VsumHistoryService;

/**
 * REST controller for managing VSUM history snapshots.
 *
 * <p>Exposes endpoints for creating and retrieving VSUM history records via {@link
 * VsumHistoryService}.
 */
@RestController
@RequestMapping("/api/")
@Validated
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VsumHistoryController {
  VsumHistoryService vsumHistoryService;
}
