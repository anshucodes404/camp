package org.camp.camp.exceptions;

import java.time.Instant;

public record ApiError(String code, String msg, Instant timestamp, String path) {
}
