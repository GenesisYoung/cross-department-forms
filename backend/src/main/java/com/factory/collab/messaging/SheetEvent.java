package com.factory.collab.messaging;

import java.time.Instant;
import java.util.List;

/**
 * Domain event published to RabbitMQ after a transaction commits. It drives both live sheet updates
 * (broadcast to WebSocket subscribers) and persisted notifications.
 */
public record SheetEvent(
        Type type,
        Long sheetId,
        String sheetName,
        long version,
        String batchId,
        String clientId,
        Long actorId,
        String actorName,
        String actorDepartmentCode,
        String actorDepartmentName,
        List<CellChangePayload> changes,
        StructurePayload structure,
        List<Long> newlySharedDepartmentIds,
        Instant at) {

    public enum Type { CELLS_UPDATED, STRUCTURE_CHANGED, META_CHANGED, SHEET_CREATED, SHEET_DELETED, SHEET_SHARED }

    /** {@code v}/{@code s} are the new value/style; {@code old} is the previous value (for messages). */
    public record CellChangePayload(int r, int c, String v, String s, String old, boolean valueChanged,
                                    boolean watched, String columnLabel, String rowLabel, String cellRef) {}

    public record StructurePayload(String op, int index, int count) {}

    public String routingKey() {
        return "sheet." + sheetId + "." + type.name().toLowerCase();
    }
}
