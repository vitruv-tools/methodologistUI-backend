package com.vitruv.methodologist.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Value;

import java.time.ZoneId;
import java.time.ZonedDateTime;

@Value
@Builder
public class ErrorResponse {
   @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSxxx")
   @Builder.Default
   private ZonedDateTime timestamp = ZonedDateTime.now(ZoneId.of("UTC"));

   private String error;

   private String message;

   private String path;
}
