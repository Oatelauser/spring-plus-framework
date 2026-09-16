package io.github.oatelauser.springplus.web.response;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CommonStatus implements ServerStatus {

    /**
     * 操作成功
     */
    SUCCESS(SUCCESS_CODE, SUCCESS_MSG),
    ;

    final String code;
    final String message;

}
