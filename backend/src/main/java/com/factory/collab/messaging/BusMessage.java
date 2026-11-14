package com.factory.collab.messaging;

/** Envelope for everything on the {@code collab.events} exchange (exactly one field is set). */
public record BusMessage(SheetEvent sheetEvent, UserPush userPush) {
    public static BusMessage of(SheetEvent e) {
        return new BusMessage(e, null);
    }

    public static BusMessage of(UserPush p) {
        return new BusMessage(null, p);
    }
}
