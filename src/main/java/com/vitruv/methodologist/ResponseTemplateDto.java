package com.vitruv.methodologist;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Builder
@AllArgsConstructor
public class ResponseTemplateDto<T> {
  private T data;
  private String message;
}
