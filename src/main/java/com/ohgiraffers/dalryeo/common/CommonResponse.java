package com.ohgiraffers.dalryeo.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CommonResponse<T> {
    private boolean success;
    private T data;

    public static <T> CommonResponse<T> success(T data) {
        return new CommonResponse<>(true, data);
    }

    public static <T> CommonResponse<T> success() {
        return new CommonResponse<>(true, null);
    }
}

